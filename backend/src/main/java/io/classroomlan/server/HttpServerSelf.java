package io.classroomlan.server;

import com.sun.net.httpserver.HttpServer;
import io.classroomlan.node.NodeState;
import io.classroomlan.server.handlers.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.logging.Logger;

/**
 * HTTP 服务器 — 支持端口冲突自动重试
 * 默认端口 8080，如被占用依次尝试 8081-8085
 */
public class HttpServerSelf {
    private static final Logger LOGGER = Logger.getLogger(HttpServerSelf.class.getName());
    private static final int HTTP_PORT_DEFAULT = 8080;
    private static final int MAX_PORT_ATTEMPTS = 5;

    private com.sun.net.httpserver.HttpServer server;
    private final NodeState nodeState;
    private int actualPort;

    public HttpServerSelf(NodeState nodeState) {
        this.nodeState = nodeState;
        this.actualPort = HTTP_PORT_DEFAULT;
    }

    /**
     * 启动 HTTP 服务器（带端口冲突重试）
     */
    public void start() throws IOException {
        for (int attempt = 0; attempt < MAX_PORT_ATTEMPTS; attempt++) {
            int tryPort = HTTP_PORT_DEFAULT + attempt;
            try {
                server = HttpServer.create(new InetSocketAddress(tryPort), 0);
                actualPort = tryPort;
                LOGGER.info("HTTP Server listening on port " + tryPort);
                break;
            } catch (IOException e) {
                LOGGER.warning("Port " + tryPort + " in use, trying next...");
                if (attempt == MAX_PORT_ATTEMPTS - 1) {
                    throw new IOException("Failed to bind HTTP server after " + MAX_PORT_ATTEMPTS + " attempts", e);
                }
            }
        }

        // 注册路由
        server.createContext("/", new StaticHandler());
        server.createContext("/assets", new StaticHandler());
        server.createContext("/api/files", new ApiFileHandler(nodeState));
        server.createContext("/api/upload", new ApiFileHandler(nodeState));
        server.createContext("/api/download", new ApiFileHandler(nodeState));
        server.createContext("/api/peers", new ApiFileHandler(nodeState));
        server.createContext("/api/status", new StatusHandler(nodeState));

        ChatHttpHandler chatHttpHandler = new ChatHttpHandler();
        server.createContext("/api/chat/history", chatHttpHandler);
        server.createContext("/api/chat/online", chatHttpHandler);
        server.createContext("/api/chat/send", chatHttpHandler);

        server.setExecutor(null);
        server.start();
    }

    public int getActualPort() {
        return actualPort;
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }
}
