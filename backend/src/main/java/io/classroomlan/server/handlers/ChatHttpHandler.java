package io.classroomlan.server.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.classroomlan.game.ChatRoom;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Logger;

/**
 * 群聊 HTTP 处理器 (REST API backup for chat)
 * 提供聊天记录、在线人数查询、发送消息接口
 *
 * 路径（由 HttpServer 挂载）:
 *   GET  /api/chat/history  → 获取历史消息
 *   GET  /api/chat/online   → 获取在线用户
 *   POST /api/chat/send     → 发送消息
 */
public class ChatHttpHandler implements HttpHandler {
    private static final Logger LOGGER = Logger.getLogger(ChatHttpHandler.class.getName());

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        try {
            if ("/api/chat/history".equals(path) && "GET".equals(method)) {
                handleGetHistory(exchange);
            } else if ("/api/chat/online".equals(path) && "GET".equals(method)) {
                handleGetOnline(exchange);
            } else if ("/api/chat/send".equals(path) && "POST".equals(method)) {
                handleSend(exchange);
            } else {
                sendJson(exchange, 404, "{\"error\":\"Not Found\"}");
            }
        } catch (Exception e) {
            LOGGER.warning("Chat handler error: " + e.getMessage());
            sendJson(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private void handleGetHistory(HttpExchange exchange) throws IOException {
        ChatRoom chatRoom = ChatRoom.getInstance();
        List<ChatRoom.ChatMessage> messages = chatRoom.getMessages();
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (ChatRoom.ChatMessage msg : messages) {
            if (!first) sb.append(",");
            sb.append(msg.toJson());
            first = false;
        }
        sb.append("]");
        sendJson(exchange, sb.toString());
    }

    private void handleGetOnline(HttpExchange exchange) throws IOException {
        ChatRoom chatRoom = ChatRoom.getInstance();
        String json = String.format(
            "{\"count\":%d,\"players\":[%s]}",
            chatRoom.getOnlineCount(),
            String.join(",", chatRoom.getOnlinePlayers())
        );
        sendJson(exchange, json);
    }

    private void handleSend(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        String playerName = extractJson(body, "playerName");
        String content = extractJson(body, "content");

        if (playerName == null || playerName.isBlank() || content == null || content.isBlank()) {
            sendJson(exchange, 400, "{\"error\":\"playerName and content required\"}");
            return;
        }

        ChatRoom chatRoom = ChatRoom.getInstance();
        ChatRoom.ChatMessage msg = chatRoom.sendMessage(playerName, content);

        LOGGER.info("Chat message from " + playerName + ": " + content);

        // WebSocket endpoints will broadcast separately; REST only acknowledges sender
        sendJson(exchange, msg.toJson());
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

    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
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
        return end == -1 ? null : json.substring(start, end);
    }
}
