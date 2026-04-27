package io.classroomlan.server;

import com.sun.net.httpserver.*;
import io.classroomlan.node.NodeState;
import io.classroomlan.server.handlers.*;

import java.io.*;
import java.net.*;
import java.util.logging.*;

/**
 * HTTP 服务器
 * 端口：8080
 * CONTEXT.txt 中的 HTTP SERVER
 */
public class HttpServer {
    private static final Logger LOGGER = Logger.getLogger(HttpServer.class.getName());
    private static final int HTTP_PORT = 8080;

    private com.sun.net.httpserver.HttpServer server;
    private final NodeState nodeState;

    public HttpServer(NodeState nodeState) {
        this.nodeState = nodeState;
    }

    /**
     * 启动 HTTP 服务器
     */
    public void start() throws IOException {
        server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);

        // 静态资源
        server.createContext("/", new StaticHandler());
        server.createContext("/assets", new StaticHandler());

        // API - 按照 CONTEXT.txt 的路由
        server.createContext("/api/files", new ApiFileHandler(nodeState));
        server.createContext("/api/upload", new ApiFileHandler(nodeState));
        server.createContext("/api/download", new ApiFileHandler(nodeState));
        server.createContext("/api/peers", new ApiFileHandler(nodeState));
        server.createContext("/api/status", new StatusHandler(nodeState));

        // 群聊 API
        ChatHttpHandler chatHttpHandler = new ChatHttpHandler();
        server.createContext("/api/chat/messages", chatHttpHandler);
        server.createContext("/api/chat/online", chatHttpHandler);
        server.createContext("/api/chat/send", chatHttpHandler);

        // 用户管理 API
        UserHandler userHandler = new UserHandler(nodeState);
        server.createContext("/api/users/register", userHandler);
        server.createContext("/api/users/online", userHandler);
        server.createContext("/api/users/heartbeat", userHandler);
        server.createContext("/api/users/leave", userHandler);

        server.setExecutor(null);
        server.start();

        LOGGER.info("HTTP Server started on port " + HTTP_PORT);
    }

    /**
     * 停止服务器
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }
}