package io.classroomlan.server;

import io.classroomlan.node.NodeState;
import io.classroomlan.server.ws.ChatWsEndpoint;
import io.classroomlan.server.ws.GameWsEndpoint;
import io.classroomlan.server.ws.UserWsEndpoint;
import org.glassfish.tyrus.server.Server;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * WebSocket 服务器 — 基于 Tyrus (Jakarta WebSocket 2.1)
 *
 * 端口：8081
 * 路径：
 *   ws://host:8081/ws/game  → 游戏房间
 *   ws://host:8081/ws/chat  → 群聊
 *   ws://host:8081/ws/user  → 用户在线状态
 */
public class WsServer {
    private static final Logger LOGGER = Logger.getLogger(WsServer.class.getName());
    private static final int WS_PORT = 8081;

    private Server server;
    private final NodeState nodeState;

    public WsServer(NodeState nodeState) {
        this.nodeState = nodeState;
        // 把 NodeState 注入到各 Endpoint 的静态字段，
        // 因为 JSR-356 容器自己负责 Endpoint 实例化，无法构造器注入
        UserWsEndpoint.setNodeState(nodeState);
        GameWsEndpoint.setNodeState(nodeState);
        ChatWsEndpoint.setNodeState(nodeState);
    }

    /**
     * 启动 WebSocket 服务器
     * Tyrus 内嵌 Grizzly，不依赖 Tomcat/Jetty
     */
    public void start() throws Exception {
        server = new Server(
            "0.0.0.0",   // 监听所有网卡
            WS_PORT,
            "/",         // 根路径
            null,        // properties（可空）
            GameWsEndpoint.class,
            ChatWsEndpoint.class,
            UserWsEndpoint.class
        );
        server.start();
        LOGGER.info("WebSocket Server started on port " + WS_PORT);
    }

    /**
     * 停止服务器
     */
    public void stop() {
        if (server != null) {
            server.stop();
            LOGGER.info("WebSocket Server stopped");
        }
    }
}
