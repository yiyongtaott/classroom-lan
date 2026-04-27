package io.classroomlan.server.handlers;

import com.sun.net.httpserver.*;
import io.classroomlan.node.NodeState;
import io.classroomlan.node.NodeRole;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * 状态 API 处理器
 * CONTEXT.txt: GET /api/status → {"role":"LEADER","selfIp":"x.x.x.x","peers":3}
 */
public class StatusHandler implements HttpHandler {
    private final NodeState nodeState;

    public StatusHandler(NodeState nodeState) {
        this.nodeState = nodeState;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if (!"GET".equals(method)) {
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }

        String role = nodeState.getCurrentRole().name();
        String selfIp = nodeState.getSelfIp() != null ? nodeState.getSelfIp() : "";

        // Leader 统计在线用户，普通节点统计 peerIds
        int onlineCount;
        if (nodeState.isLeader()) {
            onlineCount = nodeState.getOnlineCount();
        } else {
            onlineCount = nodeState.getPeerIds().size();
        }

        String json = String.format("{\"role\":\"%s\",\"selfIp\":\"%s\",\"peers\":%d,\"version\":\"1.0.0\"}",
            role, selfIp, onlineCount);

        sendJson(exchange, json);
    }

    private void sendResponse(HttpExchange exchange, int code, String msg) throws IOException {
        byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
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
}