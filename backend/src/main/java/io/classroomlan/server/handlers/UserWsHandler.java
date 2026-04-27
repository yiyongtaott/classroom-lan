package io.classroomlan.server.handlers;

import com.sun.net.httpserver.*;
import io.classroomlan.node.NodeState;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.*;

/**
 * 用户在线 WebSocket 处理器
 * 路径: /ws/user
 *
 * 使用 com.sun.net.httpserver 实现 WebSocket 协议
 */
public class UserWsHandler implements HttpHandler {
    private static final Logger LOGGER = Logger.getLogger(UserWsHandler.class.getName());

    // 活跃的 WebSocket 连接
    private static final Map<String, WsSession> sessions = new ConcurrentHashMap<>();

    // 回调接口
    public interface SessionCallback { void accept(WsSession session); }
    private static SessionCallback onConnect;
    private static SessionCallback onDisconnect;
    public static void setOnConnect(SessionCallback callback) { onConnect = callback; }
    public static void setOnDisconnect(SessionCallback callback) { onDisconnect = callback; }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String connection = exchange.getRequestHeaders().getFirst("Connection");
        String upgrade = exchange.getRequestHeaders().getFirst("Upgrade");
        String key = exchange.getRequestHeaders().getFirst("Sec-WebSocket-Key");

        if ("Upgrade".equalsIgnoreCase(connection) && "websocket".equalsIgnoreCase(upgrade) && key != null) {
            handleWebSocket(exchange, key);
        } else {
            sendJson(exchange, "{\"type\":\"info\",\"message\":\"User WebSocket endpoint\"}");
        }
    }

    private void handleWebSocket(HttpExchange exchange, String key) throws IOException {
        String acceptKey = generateAcceptKey(key);
        String clientIp = getClientIp(exchange);
        String userId = UUID.randomUUID().toString().substring(0, 8);
        String nickname = "用户" + new Random().nextInt(1000);
        String avatar = "1";

        // 发送握手响应
        exchange.getResponseHeaders().set("Upgrade", "websocket");
        exchange.getResponseHeaders().set("Connection", "Upgrade");
        exchange.getResponseHeaders().set("Sec-WebSocket-Accept", acceptKey);
        exchange.getResponseHeaders().set("Sec-WebSocket-Protocol", "chat");
        exchange.sendResponseHeaders(101, -1);

        // 创建会话
        WsSession session = new WsSession(exchange, userId, clientIp, nickname, avatar);
        sessions.put(userId, session);

        LOGGER.info("WebSocket connected: " + clientIp + " userId:" + userId);

        if (onConnect != null) onConnect.accept(session);

        // 发送欢迎消息
        sendText(session, "{\"type\":\"welcome\",\"userId\":\"" + userId + "\",\"nickname\":\"" + nickname + "\"}");
        broadcastUserList();

        // 保持连接并读取消息（简化版）
        try {
            Thread.sleep(5000); // 简化：5秒后自动断开
        } catch (InterruptedException e) {
            // ignore
        }

        sessions.remove(userId);
        if (onDisconnect != null) onDisconnect.accept(session);
        broadcastUserList();
    }

    public static void sendText(WsSession session, String text) {
        try {
            OutputStream out = session.exchange.getResponseBody();
            // WebSocket 文本帧: FIN(1) + OPCODE(4) = 0x81
            byte[] payload = text.getBytes(StandardCharsets.UTF_8);
            byte[] frame = new byte[payload.length + 2];
            frame[0] = (byte) 0x81;
            frame[1] = (byte) payload.length;
            System.arraycopy(payload, 0, frame, 2, payload.length);
            session.exchange.sendResponseHeaders(200, frame.length);
            out.write(frame);
            out.flush();
        } catch (Exception e) {
            LOGGER.warning("Send error: " + e.getMessage());
        }
    }

    public static void broadcastUserList() {
        StringBuilder sb = new StringBuilder("{\"type\":\"userList\",\"users\":[");
        boolean first = true;
        for (WsSession s : sessions.values()) {
            if (!first) sb.append(",");
            sb.append(String.format("{\"userId\":\"%s\",\"ip\":\"%s\",\"nickname\":\"%s\",\"avatar\":\"%s\"}",
                s.userId, s.ip, s.nickname, s.avatar));
            first = false;
        }
        sb.append("]}");

        for (WsSession s : sessions.values()) {
            sendText(s, sb.toString());
        }

        // 更新 NodeState
        NodeState state = NodeState.getInstance();
        state.getOnlineUsers().clear();
        for (WsSession s : sessions.values()) {
            state.addOnlineUser(s.userId, s.ip, s.nickname, s.avatar);
        }
    }

    private String generateAcceptKey(String key) throws IOException {
        String guid = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest((key + guid).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private String getClientIp(HttpExchange exchange) {
        String forwarded = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return exchange.getRemoteAddress().getAddress().getHostAddress();
    }

    private void sendJson(HttpExchange exchange, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    public static class WsSession {
        public HttpExchange exchange;
        public String userId;
        public String ip;
        public String nickname;
        public String avatar;

        public WsSession(HttpExchange exchange, String userId, String ip, String nickname, String avatar) {
            this.exchange = exchange;
            this.userId = userId;
            this.ip = ip;
            this.nickname = nickname;
            this.avatar = avatar;
        }
    }
}