package io.classroomlan.server.ws;

import io.classroomlan.node.NodeState;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;

import java.io.IOException;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 用户在线状态 WebSocket Endpoint — 支持跨 Tab 去重
 *
 * 规则: 同一浏览器实例通过 localStorage 存储 clientId(单 Tab 内共享)
 *       新 Tab 携带相同 clientId → 服务器断开旧连接，保留新连接
 *
 * 消息:
 *   C→S: {"action":"init","clientId":"uuid-…","nickname":"…","avatar":"…"}
 *        {"action":"ping"}
 *   S→C: {"type":"welcome","userId":"…","clientId":"…"}
 *        {"type":"userList","users":[{…}]}
 */
@ServerEndpoint("/ws/user")
public class UserWsEndpoint {
    private static final Logger LOGGER = Logger.getLogger(UserWsEndpoint.class.getName());

    // userId → Session (当前 WebSocket 连接)
    private static final Map<String, Session> sessionsByUserId = new ConcurrentHashMap<>();
    // userId → UserInfo
    private static final Map<String, UserInfo> usersByUserId   = new ConcurrentHashMap<>();
    // clientId → userId (当前最新的 userId 绑定，用于 Tab 去重)
    private static final Map<String, String> clientIdToUserId = new ConcurrentHashMap<>();

    private static volatile NodeState nodeState;

    public static void setNodeState(NodeState state) {
        nodeState = state;
    }

    // 键，从 Session UserProperties 读取
    private static final String USER_ID_KEY = "userId";
    private static final String CLIENT_ID_KEY = "clientId";

    @OnOpen
    public void onOpen(Session session) {
        // 先创建一个临时会话，等待客户端的 init 消息
        String tempUserId = "tmp-" + session.getId().substring(0, 6);
        session.getUserProperties().put(USER_ID_KEY, tempUserId);
        sessionsByUserId.put(tempUserId, session);

        LOGGER.info("WS/user connected (tempId=" + tempUserId + ")");
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            // 手动解析简单 JSON（保持与项目风格一致）
            String action = extractField(message, "action");
            String clientId = extractField(message, "clientId");   // 客户端 UUID
            String nickname = extractField(message, "nickname");
            String avatar   = extractField(message, "avatar");

            if ("init".equals(action)) {
                handleInit(session, clientId, nickname, avatar);
            } else if ("ping".equals(action)) {
                sendTo(session, "{\"type\":\"pong\"}");
                // Heartbeat → update timestamp (placeholders for now)
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "onMessage error", e);
        }
    }

    private void handleInit(Session session, String clientId, String nickname, String avatar) {
        String ip = getRemoteIp(session);
        String tempUserId = (String) session.getUserProperties().get(USER_ID_KEY);

        // A. 如果该 clientId 已绑定到另一个 userId → 关闭旧会话
        String existingUserId = clientIdToUserId.get(clientId);
        if (existingUserId != null && !existingUserId.equals(tempUserId)) {
            Session old = sessionsByUserId.remove(existingUserId);
            if (old != null && old.isOpen()) {
                try { old.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "Displaced by new tab")); }
                catch (IOException ignored) {}
            }
            UserInfo oldInfo = usersByUserId.remove(existingUserId);
            if (nodeState != null && oldInfo != null) {
                nodeState.removeOnlineUser(existingUserId);
            }
            LOGGER.info("Duplicated clientId detected, old session closed: " + clientId);
        }

        // B. 生成正式的持久化 userId
        String realUserId = clientId;   // use clientId as stable userId
        session.getUserProperties().put(USER_ID_KEY, realUserId);
        session.getUserProperties().put(CLIENT_ID_KEY, clientId);

        // C. 更新映射
        sessionsByUserId.remove(tempUserId);
        sessionsByUserId.put(realUserId, session);
        clientIdToUserId.put(clientId, realUserId);

        UserInfo info = new UserInfo(realUserId, nickname, avatar, ip);
        usersByUserId.put(realUserId, info);

        if (nodeState != null) {
            nodeState.addOnlineUser(realUserId, ip, nickname, avatar);
        }

        // 欢迎消息
        sendTo(session, "{\"type\":\"welcome\",\"userId\":\"" + realUserId
                + "\",\"nickname\":" + jsonStr(nickname)
                + ",\"clientId\":\"" + clientId + "\"}");

        broadcastUserList();

        LOGGER.info("WS/user registered: userId=" + realUserId + " ip=" + ip);
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        String userId = (String) session.getUserProperties().get(USER_ID_KEY);
        String clientId = (String) session.getUserProperties().get(CLIENT_ID_KEY);

        if (userId != null) {
            sessionsByUserId.remove(userId);
            usersByUserId.remove(userId);
            if (clientId != null && userId.equals(clientIdToUserId.get(clientId))) {
                clientIdToUserId.remove(clientId);
            }
            if (nodeState != null) {
                nodeState.removeOnlineUser(userId);
            }
            LOGGER.info("WS/user disconnected: userId=" + userId + " reason=" + reason);
            broadcastUserList();
        }
    }

    @OnError
    public void onError(Session session, Throwable error) {
        String userId = (String) session.getUserProperties().getOrDefault(USER_ID_KEY, "unknown");
        LOGGER.log(Level.WARNING, "WS/user error userId=" + userId, error);
    }

    // ── 广播在线列表 ───────────────────────────────────────────────────

    public static void broadcastUserList() {
        String json = buildUserListJson();
        for (Session s : sessionsByUserId.values()) {
            if (s.isOpen()) sendTo(s, json);
        }
    }

    private static String buildUserListJson() {
        StringBuilder sb = new StringBuilder("{\"type\":\"userList\",\"users\":[");
        boolean first = true;
        for (UserInfo u : usersByUserId.values()) {
            if (!first) sb.append(",");
            sb.append(u.toJson());
            first = false;
        }
        sb.append("]}");
        return sb.toString();
    }

    // ── 工具 ─────────────────────────────────────────────────────────────

    private static void sendTo(Session session, String text) {
        if (session.isOpen()) {
            try { session.getBasicRemote().sendText(text); }
            catch (IOException e) { LOGGER.log(Level.WARNING, "sendTo failed", e); }
        }
    }

    private String getRemoteIp(Session session) {
        Object addr = session.getUserProperties().get("jakarta.websocket.endpoint.remoteAddress");
        if (addr != null) {
            return addr.toString().replaceAll("/", "").split(":")[0];
        }
        return "unknown";
    }

    private String extractField(String json, String field) {
        String search = "\"" + field + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        return end == -1 ? null : json.substring(start, end);
    }

    private String jsonStr(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ── 用户信息类 ───────────────────────────────────────────────────────

    static class UserInfo {
        String userId, nickname, avatar, ip;
        long lastSeen;

        UserInfo(String userId, String nickname, String avatar, String ip) {
            this.userId = userId;
            this.nickname = nickname;
            this.avatar = avatar;
            this.ip = ip;
            this.lastSeen = System.currentTimeMillis();
        }

        String toJson() {
            return "{\"userId\":" + q(userId) + ",\"nickname\":" + q(nickname)
                 + ",\"avatar\":" + q(avatar) + ",\"ip\":" + q(ip) + "}";
        }

        private String q(String s) {
            if (s == null) return "\"\"";
            return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
    }
}
