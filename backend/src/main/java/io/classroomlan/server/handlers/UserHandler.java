package io.classroomlan.server.handlers;

import com.sun.net.httpserver.*;
import io.classroomlan.node.NodeState;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

/**
 * 用户在线管理 API
 * POST /api/users/register - 注册用户
 * GET /api/users/online - 获取在线用户列表
 * POST /api/users/heartbeat - 心跳保活
 */
public class UserHandler implements HttpHandler {
    private final NodeState nodeState;

    public UserHandler(NodeState nodeState) {
        this.nodeState = nodeState;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        try {
            if (path.equals("/api/users/register") && "POST".equals(method)) {
                handleRegister(exchange);
            } else if (path.equals("/api/users/online") && "GET".equals(method)) {
                handleOnline(exchange);
            } else if (path.equals("/api/users/heartbeat") && "POST".equals(method)) {
                handleHeartbeat(exchange);
            } else if (path.equals("/api/users/leave") && "POST".equals(method)) {
                handleLeave(exchange);
            } else {
                sendResponse(exchange, 404, "{\"error\":\"Not Found\"}");
            }
        } catch (Exception e) {
            sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private void handleRegister(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        String userId = extractJson(body, "userId");
        String nickname = extractJson(body, "nickname");
        String avatar = extractJson(body, "avatar");

        if (userId == null || userId.isEmpty()) {
            userId = nodeState.getNodeId();
        }
        if (nickname == null || nickname.isEmpty()) {
            nickname = "用户";
        }
        if (avatar == null || avatar.isEmpty()) {
            avatar = "1";
        }

        // 获取客户端 IP
        String clientIp = getClientIp(exchange);

        // Leader 记录用户
        if (nodeState.isLeader()) {
            nodeState.addOnlineUser(userId, clientIp, nickname, avatar);
        } else {
            // Follower 存储自己的信息
            nodeState.setNickname(nickname);
            nodeState.setAvatar(avatar);
        }

        sendJson(exchange, "{\"success\":true,\"userId\":\"" + userId + "\"}");
    }

    private void handleOnline(HttpExchange exchange) throws IOException {
        StringBuilder sb = new StringBuilder("[");

        if (nodeState.isLeader()) {
            Collection<NodeState.OnlineUser> users = nodeState.getOnlineUsers();
            boolean first = true;
            for (NodeState.OnlineUser user : users) {
                if (!first) sb.append(",");
                sb.append(user.toJson());
                first = false;
            }
        } else {
            // Follower 返回自己
            sb.append(String.format("{\"userId\":\"%s\",\"ip\":\"%s\",\"nickname\":\"%s\",\"avatar\":\"%s\"}",
                nodeState.getNodeId(), nodeState.getSelfIp(), nodeState.getNickname(), nodeState.getAvatar()));
        }

        sb.append("]");
        sendJson(exchange, sb.toString());
    }

    private void handleHeartbeat(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String userId = extractJson(body, "userId");

        if (nodeState.isLeader() && userId != null) {
            nodeState.updateOnlineUserTimestamp(userId);
            sendJson(exchange, "{\"success\":true}");
        } else {
            sendJson(exchange, "{\"success\":true}");
        }
    }

    private void handleLeave(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String userId = extractJson(body, "userId");

        if (nodeState.isLeader() && userId != null) {
            nodeState.removeOnlineUser(userId);
            sendJson(exchange, "{\"success\":true}");
        } else {
            sendJson(exchange, "{\"success\":true}");
        }
    }

    private String getClientIp(HttpExchange exchange) {
        // 尝试从 X-Forwarded-For 获取
        String forwarded = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        // 从 HTTP 连接获取
        java.net.InetSocketAddress addr = (java.net.InetSocketAddress) exchange.getRemoteAddress();
        return addr.getAddress().getHostAddress();
    }

    private String extractJson(String json, String field) {
        if (json == null || json.isEmpty()) return null;
        String search = "\"" + field + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return null;
        return json.substring(start, end);
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

    private void sendResponse(HttpExchange exchange, int code, String msg) throws IOException {
        byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(code, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
}