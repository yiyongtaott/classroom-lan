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
 * 用户在线状态 WebSocket Endpoint
 * 路径: ws://host:8081/ws/user
 *
 * 客户端连接后发送：
 *   {"action":"register","nickname":"张三","avatar":"2"}
 *
 * 服务端广播：
 *   {"type":"userList","users":[{"userId":"...","nickname":"...","avatar":"...","ip":"..."},...]}
 *
 * 客户端心跳（可选，30s一次）：
 *   {"action":"ping"}
 * 服务端回：
 *   {"type":"pong"}
 */
@ServerEndpoint("/ws/user")
public class UserWsEndpoint {
    private static final Logger LOGGER = Logger.getLogger(UserWsEndpoint.class.getName());

    // 所有活跃会话：userId → Session
    private static final Map<String, Session> sessions = new ConcurrentHashMap<>();
    // userId → 用户信息
    private static final Map<String, UserInfo> users = new ConcurrentHashMap<>();

    // NodeState 通过静态方法注入（JSR-356 不支持构造器注入）
    private static volatile NodeState nodeState;

    public static void setNodeState(NodeState state) {
        nodeState = state;
    }

    // ── 每个 Session 对应的 userId（存在 Session 的 userProperties 里）
    private static final String USER_ID_KEY = "userId";

    @OnOpen
    public void onOpen(Session session) {
        // 分配临时 userId，等客户端发 register 后更新昵称
        String userId = session.getId().substring(0, 8);
        String nickname = "用户" + new Random().nextInt(9000 + 1000);
        String avatar = "1";

        session.getUserProperties().put(USER_ID_KEY, userId);
        sessions.put(userId, session);

        // 获取客户端 IP（Grizzly 把 ip 放在 userProperties 里）
        String ip = getRemoteIp(session);
        UserInfo info = new UserInfo(userId, nickname, avatar, ip);
        users.put(userId, info);

        // 同步到 NodeState
        if (nodeState != null) {
            nodeState.addOnlineUser(userId, ip, nickname, avatar);
        }

        LOGGER.info("WS/user connected: " + ip + " userId=" + userId);

        // 回传 userId，让客户端保存
        sendTo(session, "{\"type\":\"welcome\",\"userId\":\"" + userId + "\",\"nickname\":" + jsonStr(nickname) + "}");

        // 广播新的在线列表
        broadcastUserList();
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        String userId = (String) session.getUserProperties().get(USER_ID_KEY);
        if (userId == null) return;

        try {
            // 简单 JSON 解析（不引入第三方 JSON 库）
            String action = extractField(message, "action");

            if ("register".equals(action)) {
                // 更新昵称和头像
                String nickname = extractField(message, "nickname");
                String avatar = extractField(message, "avatar");

                UserInfo info = users.get(userId);
                if (info != null) {
                    if (nickname != null && !nickname.isBlank()) info.nickname = nickname;
                    if (avatar != null && !avatar.isBlank()) info.avatar = avatar;
                }

                if (nodeState != null) {
                    nodeState.updateOnlineUser(userId,
                        info != null ? info.nickname : "用户",
                        info != null ? info.avatar : "1");
                }

                broadcastUserList();

            } else if ("ping".equals(action)) {
                sendTo(session, "{\"type\":\"pong\"}");

                if (nodeState != null) {
                    nodeState.updateOnlineUserTimestamp(userId);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "onMessage error userId=" + userId, e);
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        String userId = (String) session.getUserProperties().get(USER_ID_KEY);
        if (userId == null) return;

        sessions.remove(userId);
        UserInfo info = users.remove(userId);

        if (nodeState != null) {
            nodeState.removeOnlineUser(userId);
        }

        String name = info != null ? info.nickname : userId;
        LOGGER.info("WS/user disconnected: " + name + " reason=" + reason.getCloseCode());

        broadcastUserList();
    }

    @OnError
    public void onError(Session session, Throwable error) {
        String userId = (String) session.getUserProperties().getOrDefault(USER_ID_KEY, "unknown");
        LOGGER.log(Level.WARNING, "WS/user error userId=" + userId, error);
    }

    // ── 广播在线用户列表给所有连接的客户端 ──────────────────────────────

    public static void broadcastUserList() {
        String json = buildUserListJson();
        for (Session s : sessions.values()) {
            sendTo(s, json);
        }
    }

    private static String buildUserListJson() {
        StringBuilder sb = new StringBuilder("{\"type\":\"userList\",\"users\":[");
        boolean first = true;
        for (UserInfo u : users.values()) {
            if (!first) sb.append(",");
            sb.append(u.toJson());
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

    private String getRemoteIp(Session session) {
        // Grizzly 把远端地址存在 userProperties 里，key 是平台相关的
        Object addr = session.getUserProperties().get("jakarta.websocket.endpoint.remoteAddress");
        if (addr != null) return addr.toString().replaceAll("/", "").split(":")[0];
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

    // ── 内部用户信息 ─────────────────────────────────────────────────────

    static class UserInfo {
        String userId, nickname, avatar, ip;

        UserInfo(String userId, String nickname, String avatar, String ip) {
            this.userId = userId;
            this.nickname = nickname;
            this.avatar = avatar;
            this.ip = ip;
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
