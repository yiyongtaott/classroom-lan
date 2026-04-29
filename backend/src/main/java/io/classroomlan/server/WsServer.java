package io.classroomlan.server;

import io.classroomlan.node.NodeState;
import io.classroomlan.server.ws.ChatWsEndpoint;
import io.classroomlan.server.ws.GameWsEndpoint;
import io.classroomlan.server.ws.UserWsEndpoint;
import org.glassfish.tyrus.server.Server;

import java.io.IOException;
import jakarta.websocket.DeploymentException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * WebSocket 服务器 — 基于 Tyrus，支持端口冲突重试
 * 默认 8081，失败后尝试 8082-8086
 */
public class WsServer {
    private static final Logger LOGGER = Logger.getLogger(WsServer.class.getName());
    private static final int WS_PORT_DEFAULT = 8081;
    private static final int MAX_PORT_ATTEMPTS = 5;

    private Server server;
    private final NodeState nodeState;
    private int actualPort;

    public WsServer(NodeState nodeState) {
        this.nodeState = nodeState;
        UserWsEndpoint.setNodeState(nodeState);
        GameWsEndpoint.setNodeState(nodeState);
        ChatWsEndpoint.setNodeState(nodeState);
    }

    /**
     * 启动 WebSocket 服务器（带重试）
     */
    public void start() throws IOException {
        IOException lastEx = null;
        for (int attempt = 0; attempt < MAX_PORT_ATTEMPTS; attempt++) {
            int tryPort = WS_PORT_DEFAULT + attempt;
            try {
                server = new Server(
                    "0.0.0.0",
                    tryPort,
                    "/",
                    null,
                    GameWsEndpoint.class,
                    ChatWsEndpoint.class,
                    UserWsEndpoint.class
                );
                actualPort = tryPort;
                server.start();
                LOGGER.info("WebSocket Server started on port " + tryPort);
                return;
            } catch (Exception e) {
                LOGGER.warning("WS port " + tryPort + " failed: " + e.getMessage());
                lastEx = new IOException("Failed to start WS server", e);
            }
        }
        throw new IOException("Failed to bind WS server after " + MAX_PORT_ATTEMPTS + " attempts", lastEx);
    }

    public int getActualPort() {
        return actualPort;
    }

    public void stop() {
        if (server != null) {
            server.stop();
            LOGGER.info("WebSocket Server stopped");
        }
    }
}
