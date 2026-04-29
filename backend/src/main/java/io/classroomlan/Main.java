package io.classroomlan;

import io.classroomlan.node.*;
import io.classroomlan.server.*;
import java.awt.Desktop;
import java.net.URI;
import java.util.logging.*;
/**
 * ClassroomLAN 主入口 — 启动 UDP 选举 + HTTP + WS
 *
 * 架构:
 *   所有节点运行相同代码 → 选举产生 Leader
 *   Leader: 启动 HTTP(8080) + WS(8081) → 广播 LEADER_ HERE
 *   Follower: 收到 LEADER_HERE → 浏览器打开 leaderIp:port
 */
public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        LogManager.getLogManager().reset();
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.INFO);
        LOGGER.addHandler(handler);
        LOGGER.setLevel(Level.INFO);
        LOGGER.info("Starting ClassroomLAN...");

        NodeState state = NodeState.getInstance();
        UdpDiscovery udpDiscovery = null;
        HttpServerSelf httpServer = null;
        WsServer wsServer = null;

        try {
            // 1. 启动 UDP 选举
            udpDiscovery = new UdpDiscovery(state);
            udpDiscovery.start();

            // 2. 等待选举完成（2秒）
            Thread.sleep(2000);

            if (state.isLeader()) {
                // 3. Leader 启动 HTTP 服务
                httpServer = new HttpServerSelf(state);
                httpServer.start();
                int httpPort = httpServer.getActualPort();
                state.setHttpPort(httpPort);

                // 4. Leader 启动 WS 服务
                wsServer = new WsServer(state);
                wsServer.start();
                int wsPort = wsServer.getActualPort();
                state.setWsPort(wsPort);

                // 5. 通知 UdpDiscovery 端口已就绪 → 触发首次 LEADER_HERE 广播 + 心跳
                udpDiscovery.setLeaderPorts(httpPort, wsPort);

                LOGGER.info("I am LEADER — HTTP:" + httpPort + " WS:" + wsPort);

                // 6. 自动打开浏览器（本地访问）
                openBrowser("http://localhost:" + httpPort);

            } else if (state.isFollower()) {
                // Follower 直接打开浏览器访问 Leader
                String url = "http://" + state.getLeaderIp() + ":" + state.getLeaderPort();
                LOGGER.info("Following leader at " + url);
                openBrowser(url);
            } else {
                LOGGER.warning("Role undetermined — unexpected state");
            }

            // 关闭钩子
            Runtime.getRuntime().addShutdownHook(new ShutdownHook(udpDiscovery, httpServer, wsServer));

            // 阻塞主线程
            Thread.currentThread().join();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fatal error", e);
        }
    }

    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                System.out.println("Please open manually: " + url);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to open browser", e);
            System.out.println("Please open manually: " + url);
        }
    }

    static class ShutdownHook extends Thread {
        private final UdpDiscovery udp;
        private final HttpServerSelf http;
        private final WsServer ws;

        ShutdownHook(UdpDiscovery udp, HttpServerSelf http, WsServer ws) {
            this.udp = udp; this.http = http; this.ws = ws;
        }

        @Override
        public void run() {
            LOGGER.info("Shutting down...");
            try { if (udp != null) udp.close(); } catch (Exception ignored) {}
            if (http != null) http.stop();
            if (ws  != null) ws.stop();
        }
    }
}
