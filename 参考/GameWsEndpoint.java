package io.classroomlan.server.ws;

import io.classroomlan.node.NodeState;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 游戏 WebSocket Endpoint
 * 路径: ws://host:8081/ws/game
 *
 * 客户端 → 服务端：
 *   {"action":"JOIN_ROOM","roomId":"room1","playerName":"张三"}
 *   {"action":"LEAVE_ROOM","roomId":"room1"}
 *   {"action":"GAME_ACTION","roomId":"room1","payload":{...}}
 *
 * 服务端 → 客户端（房间内广播）：
 *   {"event":"ROOM_STATE","roomId":"room1","players":["张三","李四"],"gameType":"draw"}
 *   {"event":"GAME_STATE","roomId":"room1","data":{...}}
 *   {"event":"PLAYER_JOINED","playerName":"张三"}
 *   {"event":"PLAYER_LEFT","playerName":"张三"}
 *   {"event":"ERROR","message":"房间不存在"}
 */
@ServerEndpoint("/ws/game")
public class GameWsEndpoint {
    private static final Logger LOGGER = Logger.getLogger(GameWsEndpoint.class.getName());

    // roomId → 该房间内的 Session 集合
    private static final Map<String, Set<Session>> rooms = new ConcurrentHashMap<>();
    // sessionId → roomId（用于 onClose 时清理）
    private static final Map<String, String> sessionRoom = new ConcurrentHashMap<>();
    // sessionId → playerName
    private static final Map<String, String> sessionPlayer = new ConcurrentHashMap<>();

    private static volatile NodeState nodeState;

    public static void setNodeState(NodeState state) {
        nodeState = state;
    }

    @OnOpen
    public void onOpen(Session session) {
        LOGGER.info("WS/game connected: " + session.getId());
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        String sessionId = session.getId();
        try {
            String action = extractField(message, "action");
            if (action == null) return;

            switch (action) {
                case "JOIN_ROOM" -> handleJoinRoom(session, message);
                case "LEAVE_ROOM" -> handleLeaveRoom(session);
                case "GAME_ACTION" -> handleGameAction(session, message);
                default -> sendTo(session, "{\"event\":\"ERROR\",\"message\":\"Unknown action: " + action + "\"}");
            }

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "onMessage error", e);
            sendTo(session, "{\"event\":\"ERROR\",\"message\":\"Server error\"}");
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        handleLeaveRoom(session);
        LOGGER.info("WS/game disconnected: " + session.getId());
    }

    @OnError
    public void onError(Session session, Throwable error) {
        LOGGER.log(Level.WARNING, "WS/game error: " + session.getId(), error);
    }

    // ── 加入房间 ──────────────────────────────────────────────────────────

    private void handleJoinRoom(Session session, String message) {
        String roomId = extractField(message, "roomId");
        String playerName = extractField(message, "playerName");

        if (roomId == null || roomId.isBlank()) {
            sendTo(session, "{\"event\":\"ERROR\",\"message\":\"roomId 不能为空\"}");
            return;
        }
        if (playerName == null || playerName.isBlank()) playerName = "玩家";

        String sessionId = session.getId();

        // 如果已在其他房间，先离开
        if (sessionRoom.containsKey(sessionId)) {
            handleLeaveRoom(session);
        }

        // 加入新房间
        rooms.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(session);
        sessionRoom.put(sessionId, roomId);
        sessionPlayer.put(sessionId, playerName);

        LOGGER.info("Player " + playerName + " joined room " + roomId);

        // 广播给房间内所有人
        String finalPlayerName = playerName;
        broadcastToRoom(roomId, "{\"event\":\"PLAYER_JOINED\",\"playerName\":" + q(finalPlayerName) + "}");

        // 给刚加入的人发房间状态
        sendTo(session, buildRoomStateJson(roomId));
    }

    // ── 离开房间 ──────────────────────────────────────────────────────────

    private void handleLeaveRoom(Session session) {
        String sessionId = session.getId();
        String roomId = sessionRoom.remove(sessionId);
        String playerName = sessionPlayer.remove(sessionId);

        if (roomId == null) return;

        Set<Session> roomSessions = rooms.get(roomId);
        if (roomSessions != null) {
            roomSessions.remove(session);
            if (roomSessions.isEmpty()) {
                rooms.remove(roomId);
                LOGGER.info("Room " + roomId + " is now empty, removed");
                return;
            }
        }

        if (playerName != null) {
            broadcastToRoom(roomId, "{\"event\":\"PLAYER_LEFT\",\"playerName\":" + q(playerName) + "}");
        }
    }

    // ── 游戏动作 ──────────────────────────────────────────────────────────

    private void handleGameAction(Session session, String message) {
        String roomId = sessionRoom.get(session.getId());
        if (roomId == null) {
            sendTo(session, "{\"event\":\"ERROR\",\"message\":\"请先加入房间\"}");
            return;
        }

        // 提取 payload 并转发给房间内所有人（包括自己）
        // 后续实现具体游戏逻辑时，在这里分发到 DrawGuessGame / QuizGame / WerewolfGame
        int payloadStart = message.indexOf("\"payload\":");
        String payloadJson = payloadStart >= 0
            ? message.substring(payloadStart + 10, message.lastIndexOf("}") + 1)
            : "{}";

        String playerName = sessionPlayer.getOrDefault(session.getId(), "玩家");
        String forward = "{\"event\":\"GAME_STATE\","
            + "\"roomId\":" + q(roomId) + ","
            + "\"playerName\":" + q(playerName) + ","
            + "\"data\":" + payloadJson + "}";

        broadcastToRoom(roomId, forward);
    }

    // ── 广播 ──────────────────────────────────────────────────────────────

    private void broadcastToRoom(String roomId, String message) {
        Set<Session> roomSessions = rooms.get(roomId);
        if (roomSessions == null) return;
        for (Session s : roomSessions) {
            sendTo(s, message);
        }
    }

    // ── 房间状态 JSON ─────────────────────────────────────────────────────

    private String buildRoomStateJson(String roomId) {
        Set<Session> roomSessions = rooms.getOrDefault(roomId, Set.of());
        StringBuilder players = new StringBuilder("[");
        boolean first = true;
        for (Session s : roomSessions) {
            String name = sessionPlayer.getOrDefault(s.getId(), "玩家");
            if (!first) players.append(",");
            players.append(q(name));
            first = false;
        }
        players.append("]");

        return "{\"event\":\"ROOM_STATE\","
            + "\"roomId\":" + q(roomId) + ","
            + "\"players\":" + players + "}";
    }

    // ── 工具方法 ─────────────────────────────────────────────────────────

    private static void sendTo(Session session, String text) {
        if (session.isOpen()) {
            try {
                session.getBasicRemote().sendText(text);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "sendTo failed", e);
            }
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
