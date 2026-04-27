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
 * 群聊 HTTP API 处理器
 * REST API: /api/chat/*
 */
public class ChatHttpHandler implements HttpHandler {
    private static final Logger LOGGER = Logger.getLogger(ChatHttpHandler.class.getName());
    private final ChatRoom chatRoom;

    public ChatHttpHandler() {
        this.chatRoom = ChatRoom.getInstance();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        try {
            if (path.equals("/api/chat/messages") && "GET".equals(method)) {
                handleGetMessages(exchange);
            } else if (path.equals("/api/chat/online") && "GET".equals(method)) {
                handleGetOnline(exchange);
            } else if (path.equals("/api/chat/send") && "POST".equals(method)) {
                handleSendMessage(exchange);
            } else {
                sendJson(exchange, 404, "{\"error\":\"Not Found\"}");
            }
        } catch (Exception e) {
            LOGGER.warning("Handler error: " + e.getMessage());
            sendJson(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private void handleGetMessages(HttpExchange exchange) throws IOException {
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
    }

    private void handleGetOnline(HttpExchange exchange) throws IOException {
        String json = String.format(
            "{\"count\":%d,\"players\":[%s]}",
            chatRoom.getOnlineCount(),
            String.join(",", chatRoom.getOnlinePlayers())
        );
        sendJson(exchange, 200, json);
    }

    private void handleSendMessage(HttpExchange exchange) throws IOException {
        // 简化：从请求体读取 JSON
        // 格式: {"playerName":"xxx","content":"xxx"}
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        String playerName = extractJson(body, "playerName");
        String content = extractJson(body, "content");

        if (playerName != null && content != null) {
            chatRoom.sendMessage(playerName, content);
            sendJson(exchange, 200, "{\"success\":true}");
        } else {
            sendJson(exchange, 400, "{\"error\":\"Invalid request\"}");
        }
    }

    private void sendJson(HttpExchange exchange, int code, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
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