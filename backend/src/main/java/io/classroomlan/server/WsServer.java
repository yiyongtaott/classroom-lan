package io.classroomlan.server;

import io.classroomlan.node.NodeState;
import io.classroomlan.server.handlers.ChatHandler;
import io.classroomlan.server.handlers.UserWsHandler;

import java.io.*;
import java.net.*;
import java.util.logging.*;

/**
 * WebSocket 服务器
 * 端口：8081
 */
public class WsServer {
    private static final Logger LOGGER = Logger.getLogger(WsServer.class.getName());
    private static final int WS_PORT = 8081;

    private com.sun.net.httpserver.HttpServer server;
    private final NodeState nodeState;

    public WsServer(NodeState nodeState) {
        this.nodeState = nodeState;

        // 设置 WebSocket 回调
        UserWsHandler.setOnConnect(session -> {
            LOGGER.info("User connected: " + session.ip + " (" + session.userId + ")");
            nodeState.addOnlineUser(session.userId, session.ip, session.nickname, session.avatar);
        });

        UserWsHandler.setOnDisconnect(session -> {
            LOGGER.info("User disconnected: " + session.ip + " (" + session.userId + ")");
            nodeState.removeOnlineUser(session.userId);
        });
    }

    /**
     * 启动 WebSocket 服务器
     */
    public void start() throws IOException {
        server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(WS_PORT), 0);
        server.createContext("/ws/game", new WsHandler());
        server.createContext("/ws/chat", new ChatHandler());
        server.createContext("/ws/user", new UserWsHandler());
        server.setExecutor(null);
        server.start();

        LOGGER.info("WebSocket Server started on port " + WS_PORT);
    }

    /**
     * 停止服务器
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * 游戏 WebSocket 处理
     */
    static class WsHandler implements com.sun.net.httpserver.HttpHandler {
        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            String response = "Game WebSocket endpoint";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes());
        }
    }
}