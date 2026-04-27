package io.classroomlan;

import io.classroomlan.node.*;
import io.classroomlan.server.*;

import java.awt.Desktop;
import java.net.URI;
import java.util.logging.*;

/**
 * ClassroomLAN 主入口
 * 启动 UDP + HTTP + WS
 */
public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        // 配置日志
        LogManager.getLogManager().reset();
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.INFO);
        LOGGER.addHandler(handler);
        LOGGER.setLevel(Level.INFO);
        LOGGER.info("Starting ClassroomLAN...");

        NodeState state = NodeState.getInstance();
        UdpDiscovery udpDiscovery = null;
        HttpServer httpServer = null;
        WsServer wsServer = null;

        try {
            // 启动 UDP 发现
            udpDiscovery = new UdpDiscovery(state);
            udpDiscovery.start();

            // 等待角色确定
            Thread.sleep(2000);

            if (state.isLeader()) {
                // Leader 启动 HTTP 和 WS 服务器
                httpServer = new HttpServer(state);
                httpServer.start();

                wsServer = new WsServer(state);
                wsServer.start();

                // 自动打开浏览器
                openBrowser("http://localhost:8080");

                LOGGER.info("I am the Leader!");
            } else if (state.isFollower()) {
                // Follower 打开 Leader 的网页
                String url = "http://" + state.getLeaderIp() + ":8080";
                openBrowser(url);
                LOGGER.info("Connected to Leader: " + state.getLeaderIp());
            } else {
                LOGGER.warning("Role not determined yet");
            }

            // 添加关闭钩子
            Thread shutdownHook = new ShutdownHook(udpDiscovery, httpServer, wsServer);
            Runtime.getRuntime().addShutdownHook(shutdownHook);

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
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to open browser", e);
        }
    }

    // 关闭钩子类
    static class ShutdownHook extends Thread {
        private final UdpDiscovery udpDiscovery;
        private final HttpServer httpServer;
        private final WsServer wsServer;

        ShutdownHook(UdpDiscovery udp, HttpServer http, WsServer ws) {
            this.udpDiscovery = udp;
            this.httpServer = http;
            this.wsServer = ws;
        }

        @Override
        public void run() {
            LOGGER.info("Shutting down...");
            if (udpDiscovery != null) {
                try { udpDiscovery.close(); } catch (Exception e) { LOGGER.log(Level.WARNING, "Error closing UDP", e); }
            }
            if (httpServer != null) {
                httpServer.stop();
            }
            if (wsServer != null) {
                wsServer.stop();
            }
        }
    }
}