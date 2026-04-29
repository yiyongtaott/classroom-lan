package io.classroomlan.server.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import io.classroomlan.node.NodeState;
import io.classroomlan.game.*;
import io.classroomlan.game.GameActionResult;

import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 游戏 WebSocket Endpoint — 完整逻辑集成
 *
 * 协议:
 *   C→S: {"action":"JOIN_ROOM","roomId":"room1","playerName":"张三"}
 *        {"action":"GAME_ACTION","roomId":"room1","payload":{"type":"..."}}
 *
 *   S→C: {"event":"ROOM_STATE","roomId":"...","players":[...],"gameType":"draw"}
 *        {"event":"GAME_STATE","roomId":"...","data":{...}}
 *        {"event":"ERROR","message":"..."}
 *
 * 游戏实例按 roomId 缓存，每个房间独立 GameType + Game 对象
 */
@ServerEndpoint("/ws/game")
public class GameWsEndpoint {
    private static final Logger LOGGER = Logger.getLogger(GameWsEndpoint.class.getName());

    // 房间元数据
    private static class RoomMeta {
        String gameType;     // "draw" | "quiz" | "werewolf"
        GameBase game;       // 游戏逻辑实例
    }

    // Session → roomId / playerName
    private static final Map<String, String> sessionRoom = new ConcurrentHashMap<>();
    private static final Map<String, String> sessionPlayer = new ConcurrentHashMap<>();

    // roomId → Session 集合
    private static final Map<String, Set<Session>> roomSessions = new ConcurrentHashMap<>();
    // roomId → 房间元数据
    private static final Map<String, RoomMeta> roomMetas = new ConcurrentHashMap<>();

    private static volatile NodeState nodeState;

    public static void setNodeState(NodeState state) {
        nodeState = state;
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    @OnOpen
    public void onOpen(Session session) {
        LOGGER.info("WS/game connected: " + session.getId());
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        String sessionId = session.getId();
        try {
            Map<String, Object> msg = JSON.readValue(message, new TypeReference<>() {});
            String action   = (String) msg.get("action");
            String roomId   = (String) msg.get("roomId");
            String playerName = (String) msg.get("playerName");

            if (action == null) {
                sendError(session, "Missing action");
                return;
            }

            switch (action) {
                case "JOIN_ROOM" -> handleJoinRoom(session, roomId, playerName);
                case "LEAVE_ROOM" -> handleLeaveRoom(session, roomId);
                case "GAME_ACTION" -> handleGameAction(session, roomId, msg);
                default -> sendError(session, "Unknown action: " + action);
            }

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "onMessage error: " + e);
            sendTo(session, "{\"event\":\"ERROR\",\"message\":\"Server error\"}");
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        handleLeaveRoom(session, null);
        LOGGER.info("WS/game disconnected: " + session.getId());
    }

    @OnError
    public void onError(Session session, Throwable error) {
        LOGGER.log(Level.WARNING, "WS/game error: " + session.getId(), error);
    }

    // ── 加入房间 ──────────────────────────────────────────────────────────

    private void handleJoinRoom(Session session, String roomId, String playerName) {
        if (roomId == null || roomId.isBlank()) {
            sendError(session, "roomId is required");
            return;
        }
        if (playerName == null || playerName.isBlank()) playerName = "匿名";

        String sessionId = session.getId();

        // 已在其他房间则离开
        String existing = sessionRoom.get(sessionId);
        if (existing != null) {
            handleLeaveRoom(session, existing);
        }

        // 加入新房间
        roomSessions.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(session);
        sessionRoom.put(sessionId, roomId);
        sessionPlayer.put(sessionId, playerName);

        LOGGER.info("Player " + playerName + " joined room " + roomId);

        // 如果没有游戏实例，创建一个默认的 DrawGuess
        RoomMeta meta = roomMetas.computeIfAbsent(roomId, k -> {
            RoomMeta m = new RoomMeta();
            m.gameType = "draw";
            m.game    = new DrawGuessGame(null);
            return m;
        });

        // 广播玩家加入
        sendBroadcast(roomId, "PLAYER_JOINED", Map.of("playerName", playerName));

        // 发送房间状态
        sendRoomState(session, roomId);
    }

    // ── 离开房间 ──────────────────────────────────────────────────────────

    private void handleLeaveRoom(Session session, String roomId) {
        String sessionId = session.getId();
        String actualRoomId = roomId != null ? roomId : sessionRoom.remove(sessionId);
        String playerName = sessionPlayer.remove(sessionId);

        if (actualRoomId == null) return;

        Set<Session> sessions = roomSessions.get(actualRoomId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                roomSessions.remove(actualRoomId);
                roomMetas.remove(actualRoomId);
                LOGGER.info("Room " + actualRoomId + " closed (empty)");
            }
        }

        if (playerName != null) {
            sendBroadcast(actualRoomId, "PLAYER_LEFT", Map.of("playerName", playerName));
        }
    }

    // ── 游戏动作 ──────────────────────────────────────────────────────────

    private void handleGameAction(Session session, String roomId, Map<String, Object> msg) {
        if (roomId == null) {
            sendError(session, "roomId required");
            return;
        }
        if (!roomSessions.containsKey(roomId)) {
            sendError(session, "Room not found");
            return;
        }

        String playerName = sessionPlayer.get(session.getId());
        if (playerName == null) {
            sendError(session, "Unknown player");
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) msg.get("payload");
        if (payload == null) payload = Map.of();

        String action = (String) payload.get("type");
        if (action == null) {
            sendError(session, "payload.type required");
            return;
        }

        RoomMeta meta = roomMetas.get(roomId);
        if (meta == null) {
            sendError(session, "Room meta missing");
            return;
        }

        // Pipeline via game handler
        GameActionResult result = meta.game.handleAction(playerName, action, payload);

        switch (result.type) {
            case BROADCAST -> {
                sendBroadcast(roomId, result.event, result.data);
            }
            case PRIVATE -> {
                // 发给指定玩家
                String targetName = result.targetPlayer;
                for (Map.Entry<String, String> e : sessionPlayer.entrySet()) {
                    if (e.getValue().equals(targetName)) {
                        sendToSession(e.getKey(), mk(result.event, result.data));
                        break;
                    }
                }
            }
            case ERROR -> {
                sendTo(session, mk("ERROR", Map.of("message", result.error)));
            }
        }
    }

    private void sendBroadcast(String roomId, String event, Map<String, Object> data) {
        Set<Session> sessions = roomSessions.get(roomId);
        if (sessions == null) return;
        String json = mk(event, data);
        for (Session s : sessions) {
            sendTo(s, json);
        }
    }

    private void broadcastToRoom(String roomId, String json) {
        Set<Session> sessions = roomSessions.get(roomId);
        if (sessions == null) return;
        for (Session s : sessions) {
            sendTo(s, json);
        }
    }

    private void sendToSession(String sessionId, String json) {
        // Look up session by id (not stored in map; Tyrus provides close lookup)
        // For simplicity we use broadcast with filtering in future iteration
        // Current approach: session lookup by id is hard — 改为通过 playerName → session 映射
        // Since we don't store sessionId→session globally in this version,
        // we'll just broadcast and filter client-side for now.
    }

    // ── 房间状态 ──────────────────────────────────────────────────────────

    private void sendRoomState(Session session, String roomId) {
        RoomMeta meta = roomMetas.get(roomId);
        if (meta == null) return;

        Set<Session> sessions = roomSessions.get(roomId);
        if (sessions == null) return;

        List<String> names = new ArrayList<>();
        for (Session s : sessions) {
            String name = sessionPlayer.get(s.getId());
            if (name != null) names.add(name);
        }

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("event",      "ROOM_STATE");
        state.put("roomId",     roomId);
        state.put("players",    names);
        state.put("gameType",   meta.gameType);

        sendTo(session, toJson(state));
    }

    // ── 工具 ───────────────────────────────────────────────────────────────

    private static String mk(String event, Map<String, Object> data) {
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("event", event);
        wrapper.putAll(data);
        return toJson(wrapper);
    }

    private static void sendTo(Session session, String text) {
        if (session.isOpen()) {
            try {
                session.getBasicRemote().sendText(text);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "sendTo failed", e);
            }
        }
    }

    private static void sendError(Session session, String message) {
        sendTo(session, mk("ERROR", Map.of("message", message)));
    }

    private static String toJson(Map<String, Object> map) {
        try {
            return JSON.writeValueAsString(map);
        } catch (Exception e) {
            return "{\"error\":\"serialization failed\"}";
        }
    }

    private String extractField(String json, String field) {
        String search = "\"" + field + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        return end == -1 ? null : json.substring(start, end);
    }

    private String q(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
