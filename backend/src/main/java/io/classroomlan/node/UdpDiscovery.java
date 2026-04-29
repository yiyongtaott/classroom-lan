package io.classroomlan.node;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.logging.*;

/**
 * UDP 广播、心跳、选主 — 支持延迟 Leader 宣告
 *
 * 流程:
 * 1. 启动 → 开始选举 (send DISCOVER)
 * 2. 成为 Leader → 等待外部调用 setLeaderPorts() 后首次广播 LEADER_HERE
 * 3. 心跳循环 → 每 3s 广播一次 HEARTBEAT
 *
 * 注意: 这确保 Follower 收到的 LEADER_HERE 包含真实的 HTTP/WS 端口
 */
public class UdpDiscovery implements Closeable {
    private static final Logger LOGGER = Logger.getLogger(UdpDiscovery.class.getName());
    private static final int UDP_PORT = 9999;
    private static final String BROADCAST_ADDRESS = "255.255.255.255";
    private static final int DISCOVER_WAIT_MS = 500;
    private static final int RANDOM_WAIT_MAX_MS = 300;
    private static final int HEARTBEAT_INTERVAL_MS = 3000;

    private final NodeState state;
    private DatagramSocket socket;
    private volatile boolean running;
    private ScheduledExecutorService scheduler;
    private Thread listenerThread;
    private InetSocketAddress broadcastAddr;

    // Leader 宣告状态
    private volatile boolean leaderPortsSet = false;
    private volatile int effectiveHttpPort = 8080;
    private volatile int effectiveWsPort   = 8081;
    private ScheduledFuture<?> heartbeatFuture;  // 心跳任务句柄

    public UdpDiscovery(NodeState state) {
        this.state = state;
    }

    /**
     * 启动 UDP 发现服务
     * UDP 端口被占用时进入单机模式（无广播）
     */
    public void start() throws IOException {
        try {
            socket = new DatagramSocket(UDP_PORT);
            socket.setBroadcast(true);
            broadcastAddr = new InetSocketAddress(BROADCAST_ADDRESS, UDP_PORT);
            running = true;

            scheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "udp-discovery"));
            listenerThread = new Thread(this::listenLoop, "udp-listener");
            listenerThread.setDaemon(true);
            listenerThread.start();

            LOGGER.info("UDP discovery bound to port " + UDP_PORT);
            startElection();
        } catch (SocketException e) {
            LOGGER.warning("UDP port " + UDP_PORT + " in use - starting in standalone mode (no election)");
            running = true;
            try { socket = new DatagramSocket(); } catch (SocketException ignored) {}
            // 即使独立模式也需创建 scheduler 以支持心跳
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "udp-discovery-solo"));
            becomeLeaderSolo();
        }
    }

    /**
     * Leader 在 HTTP/WS 端口绑定完成后调用此方法
     * 接收到实际端口后，首次广播 LEADER_HERE 并启动心跳
     */
    public void setLeaderPorts(int httpPort, int wsPort) {
        this.effectiveHttpPort = httpPort;
        this.effectiveWsPort   = wsPort;
        this.leaderPortsSet    = true;
        LOGGER.info("Leader ports confirmed: HTTP=" + httpPort + " WS=" + wsPort);

        // 如果已经是 Leader 但尚未广播，立即发送首次宣告
        if (state.isLeader() && !isHeartbeatRunning()) {
            sendBroadcast(createLeaderHereMsg());
            startHeartbeat();
        }
    }

    boolean isHeartbeatRunning() {
        return heartbeatFuture != null && !heartbeatFuture.isDone();
    }

    // ── 选举 ────────────────────────────────────────────────────────────

    private void startElection() {
        sendBroadcast(createDiscoverMsg());

        scheduler.schedule(() -> {
            if (state.isCandidate()) {
                int delay = (int) (Math.random() * RANDOM_WAIT_MAX_MS);
                try { Thread.sleep(delay); } catch (InterruptedException e) { return; }

                if (state.isCandidate()) {
                    try { Thread.sleep(200); } catch (InterruptedException e) { /* ignore */ }
                    if (state.isCandidate()) becomeLeader();
                }
            }
        }, DISCOVER_WAIT_MS, TimeUnit.MILLISECONDS);
    }

    // ── Leader 路径 ────────────────────────────────────────────────────

    private void becomeLeader() {
        state.setCurrentRole(NodeRole.LEADER);
        String localIp = getLocalIp();
        state.setSelfIp(localIp);
        state.setLeaderIp(localIp);

        LOGGER.info("Became LEADER: " + localIp);

        // 等待端口绑定后才宣告（由 Main 调用 setLeaderPorts 触发首次广播）
        if (leaderPortsSet) {
            sendBroadcast(createLeaderHereMsg());
            startHeartbeat();
        } else {
            LOGGER.info("Leader ports not yet configured; awaiting setLeaderPorts()");
        }
    }

    private void becomeLeaderSolo() {
        state.setCurrentRole(NodeRole.LEADER);
        String localIp = getLocalIp();
        state.setSelfIp(localIp);
        state.setLeaderIp(localIp);
        // Solo mode: ports unknown, but we assume default
        this.effectiveHttpPort = 8080;
        this.effectiveWsPort   = 8081;
        this.leaderPortsSet    = true;
        LOGGER.info("Became LEADER (solo): " + localIp);
    }

    private void startHeartbeat() {
        if (heartbeatFuture != null) return;  // already running
        heartbeatFuture = scheduler.scheduleAtFixedRate(() -> {
            if (state.isLeader() && leaderPortsSet) {
                sendBroadcast(createHeartbeatMsg());
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
        LOGGER.info("Heartbeat started (every " + HEARTBEAT_INTERVAL_MS + "ms)");
    }

    // ── Follower 路径 ──────────────────────────────────────────────────

    private void becomeFollower(String leaderIp, int leaderHttpPort) {
        if (state.isLeader()) return;

        state.setCurrentRole(NodeRole.FOLLOWER);
        state.setLeaderIp(leaderIp);
        state.setLeaderPort(leaderHttpPort);

        LOGGER.info("Became FOLLOWER: leader=" + leaderIp + ":" + leaderHttpPort);

        // 启动心跳检测（当前为占位，TODO: 超时重选）
    }

    // ── 消息处理 ───────────────────────────────────────────────────────

    private void handleMessage(String json, InetAddress sender) {
        try {
            if (json.contains("\"type\":\"DISCOVER\"")) {
                if (state.isLeader()) {
                    sendUnicast(sender, createLeaderHereMsg());
                }
            } else if (json.contains("\"type\":\"LEADER_HERE\"")) {
                String leaderIp = extractField(json, "leaderIp");
                String portStr  = extractField(json, "port");
                int    port     = (portStr != null) ? Integer.parseInt(portStr) : 8080;
                if (leaderIp != null) becomeFollower(leaderIp, port);
            } else if (json.contains("\"type\":\"HEARTBEAT\"")) {
                // heartbeat keeps follower alive
            } else if (json.contains("\"type\":\"LEADER_DOWN\"")) {
                if (state.isFollower()) {
                    state.setCurrentRole(NodeRole.CANDIDATE);
                    leaderPortsSet = false;
                    startElection();
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error handling UDP message", e);
        }
    }

    // ── 消息构造 ───────────────────────────────────────────────────────

    private String createDiscoverMsg() {
        return String.format("{\"type\":\"DISCOVER\",\"nodeId\":\"%s\"}", state.getNodeId());
    }

    private String createLeaderHereMsg() {
        return String.format("{\"type\":\"LEADER_HERE\",\"leaderIp\":\"%s\",\"port\":%d}",
            state.getSelfIp(), effectiveHttpPort);
    }

    private String createHeartbeatMsg() {
        return String.format("{\"type\":\"HEARTBEAT\",\"leaderIp\":\"%s\"}", state.getLeaderIp());
    }

    private String createLeaderDownMsg() {
        return "{\"type\":\"LEADER_DOWN\"}";
    }

    // ── 网络工具 ───────────────────────────────────────────────────────

    private void sendBroadcast(String msg) {
        if (broadcastAddr == null) return;
        try {
            byte[] data = msg.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, broadcastAddr);
            socket.send(packet);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "UDP broadcast error", e);
        }
    }

    private void sendUnicast(InetAddress addr, String msg) {
        try {
            byte[] data = msg.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, addr, UDP_PORT);
            socket.send(packet);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "UDP unicast error", e);
        }
    }

    // ── 解析与工具 ─────────────────────────────────────────────────────

    private String extractField(String json, String field) {
        String search = "\"" + field + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        return end == -1 ? null : json.substring(start, end);
    }

    private String getLocalIp() {
        try (DatagramSocket sock = new DatagramSocket()) {
            sock.connect(InetAddress.getByName("8.8.8.8"), 9999);
            return sock.getLocalAddress().getHostAddress();
        } catch (IOException e) {
            return "127.0.0.1";
        }
    }

    private void listenLoop() {
        byte[] buffer = new byte[4096];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String msg = new String(packet.getData(), 0, packet.getLength());
                handleMessage(msg, packet.getAddress());
            } catch (IOException e) {
                if (running) LOGGER.log(Level.WARNING, "UDP receive error", e);
            }
        }
    }

    @Override
    public void close() {
        running = false;
        broadcastLeaderDown();
        if (scheduler  != null) scheduler.shutdown();
        if (socket     != null) socket.close();
    }

    public void broadcastLeaderDown() {
        if (state.isLeader()) {
            sendBroadcast(createLeaderDownMsg());
        }
    }
}
