package io.classroomlan.server.ws;

import io.classroomlan.node.NodeState;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 群聊 WebSocket Endpoint
 * 路径: ws://host:8081/ws/chat
 *
 * 客户端 → 服务端消息：
 *   {"action":"join","nickname":"张三"}           首次加入，设置昵称
 *   {"action":"send","content":"你好"}            发送消息
 *   {"action":"ping"}                             心跳
 *
 * 服务端 → 客户端（广播）：
 *   {"type":"message","sender":"张三","content":"你好","timestamp":1234567890,"isSystem":false}
 *   {"type":"history","messages":[...]}           连接后推送最近100条历史
 *   {"type":"pong"}
 */
@ServerEndpoint("/ws/chat")
public class ChatWsEndpoint {
    private static final Logger LOGGER = Logger.getLogger(ChatWsEndpoint.class.getName());
    private static final int MAX_HISTORY = 100;

    // 所有活跃会话：sessionId → Session
    private static final Map<String, Session> sessions = new ConcurrentHashMap<>();
    // sessionId → 昵称
    private static final Map<String, String> nicknames = new ConcurrentHashMap<>();
    // 消息历史（线程安全的简单列表，用 ConcurrentHashMap 做有序存储）
    private static final java.util.Deque<String> history =
        new java.util.concurrent.ConcurrentLinkedDeque<>();

    private static volatile NodeState nodeState;

    public static void setNodeState(NodeState state) {
        nodeState = state;
    }

    @OnOpen
    public void onOpen(Session session) {
        sessions.put(session.getId(), session);
        LOGGER.info("WS/chat connected: " + session.getId());

        // 推送历史消息
        String historyJson = buildHistoryJson();
        sendTo(session, historyJson);
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        String sessionId = session.getId();
        try {
            String action = extractField(message, "action");

            if ("join".equals(action)) {
                String nickname = extractField(message, "nickname");
                if (nickname == null || nickname.isBlank()) nickname = "匿名";
                nicknames.put(sessionId, nickname);

                // 广播系统消息
                broadcastSystem(nickname + " 加入了群聊");

            } else if ("send".equals(action)) {
                String content = extractField(message, "content");
                if (content == null || content.isBlank()) return;

                String sender = nicknames.getOrDefault(sessionId, "匿名");
                broadcastMessage(sender, content, false);

            } else if ("ping".equals(action)) {
                sendTo(session, "{\"type\":\"pong\"}");
            }

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "onMessage error", e);
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        String nickname = nicknames.remove(sessionId);

        if (nickname != null) {
            broadcastSystem(nickname + " 离开了群聊");
        }
        LOGGER.info("WS/chat disconnected: " + sessionId);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        LOGGER.log(Level.WARNING, "WS/chat error: " + session.getId(), error);
    }

    // ── 广播 ─────────────────────────────────────────────────────────────

    private void broadcastMessage(String sender, String content, boolean isSystem) {
        long ts = System.currentTimeMillis();
        String json = "{\"type\":\"message\","
            + "\"sender\":" + q(sender) + ","
            + "\"content\":" + q(content) + ","
            + "\"timestamp\":" + ts + ","
            + "\"isSystem\":" + isSystem + "}";

        // 存入历史
        history.addLast(json);
        while (history.size() > MAX_HISTORY) history.pollFirst();

        // 广播给所有人
        for (Session s : sessions.values()) {
            sendTo(s, json);
        }
    }

    private void broadcastSystem(String content) {
        broadcastMessage("系统", content, true);
    }

    // ── 历史消息 ─────────────────────────────────────────────────────────

    private String buildHistoryJson() {
        StringBuilder sb = new StringBuilder("{\"type\":\"history\",\"messages\":[");
        boolean first = true;
        for (String msg : history) {
            if (!first) sb.append(",");
            sb.append(msg);
            first = false;
        }
        sb.append("]}");
        return sb.toString();
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
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                       .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
}
