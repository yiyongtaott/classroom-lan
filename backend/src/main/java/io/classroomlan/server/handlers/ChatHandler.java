package io.classroomlan.server.handlers;

import io.classroomlan.game.ChatRoom;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Logger;

/**
 * 群聊 WebSocket 处理器（简化版）
 * 路径: /ws/chat
 *
 * 注意：纯 Java 17 不包含服务器端 WebSocket 支持
 * 此处提供 HTTP API，实时消息通过轮询 /api/chat/messages 获取
 */
public class ChatHandler implements HttpHandler {
    private static final Logger LOGGER = Logger.getLogger(ChatHandler.class.getName());

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        try {
            if (path.equals("/ws/chat")) {
                handleChat(exchange);
            } else {
                sendJson(exchange, 404, "{\"error\":\"Not Found\"}");
            }
        } catch (Exception e) {
            LOGGER.warning("Chat handler error: " + e.getMessage());
            sendJson(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private void handleChat(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();

        if ("GET".equals(method)) {
            // 返回聊天连接信息
            String json = "{" +
                "\"type\":\"info\"," +
                "\"message\":\"Chat endpoint\"," +
                "\"restApi\":{" +
                    "\"messages\":\"GET /api/chat/messages\"," +
                    "\"online\":\"GET /api/chat/online\"," +
                    "\"send\":\"POST /api/chat/send\"" +
                "}," +
                "\"websocket\":\"WebSocket not supported in pure Java 17, use REST polling\"," +
                "\"lastUpdate\":" + System.currentTimeMillis() +
                "}";
            sendJson(exchange, 200, json);
        } else if ("POST".equals(method)) {
            // 处理聊天消息
            handleMessage(exchange);
        } else {
            sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
        }
    }

    private void handleMessage(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        String action = extractJson(body, "action");
        String playerName = extractJson(body, "playerName");
        String content = extractJson(body, "content");

        ChatRoom chatRoom = ChatRoom.getInstance();

        if ("join".equals(action) && playerName != null) {
            chatRoom.join(null, playerName);
            sendJson(exchange, 200, "{\"success\":true,\"action\":\"join\"}");
        } else if ("send".equals(action) && playerName != null && content != null) {
            ChatRoom.ChatMessage msg = chatRoom.sendMessage(playerName, content);
            sendJson(exchange, 200, msg.toJson());
        } else if ("leave".equals(action) && playerName != null) {
            chatRoom.leave(playerName);
            sendJson(exchange, 200, "{\"success\":true,\"action\":\"leave\"}");
        } else if ("getMessages".equals(action)) {
            List<ChatRoom.ChatMessage> messages = chatRoom.getMessages();
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (ChatRoom.ChatMessage msg : messages) {
                if (!first) sb.append(",");
                sb.append(msg.toJson());
                first = false;
            }
            sb.append("]");
            sendJson(exchange, 200, sb.toString());
        } else if ("getOnline".equals(action)) {
            String json = String.format(
                "{\"count\":%d,\"players\":[%s]}",
                chatRoom.getOnlineCount(),
                String.join(",", chatRoom.getOnlinePlayers())
            );
            sendJson(exchange, 200, json);
        } else {
            sendJson(exchange, 400, "{\"error\":\"Invalid request\"}");
        }
    }

    private void sendJson(HttpExchange exchange, int code, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private String extractJson(String json, String field) {
        if (json == null) return null;
        String search = "\"" + field + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return null;
        return json.substring(start, end);
    }
}