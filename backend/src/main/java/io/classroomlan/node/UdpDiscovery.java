package io.classroomlan.node;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.logging.*;

/**
 * UDP 广播、心跳、选主
 * 端口：UDP 9999
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

    public UdpDiscovery(NodeState state) {
        this.state = state;
    }

    /**
     * 启动 UDP 发现服务
     */
    public void start() throws IOException {
        socket = new DatagramSocket(UDP_PORT);
        socket.setBroadcast(true);
        broadcastAddr = new InetSocketAddress(BROADCAST_ADDRESS, UDP_PORT);
        running = true;

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "udp-discovery"));
        listenerThread = new Thread(this::listenLoop, "udp-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();

        // 开始竞选
        startElection();
    }

    /**
     * 开始竞选 Leader
     */
    private void startElection() {
        sendBroadcast(createDiscoverMsg());

        scheduler.schedule(() -> {
            if (state.isCandidate()) {
                // 随机退避
                int delay = (int) (Math.random() * RANDOM_WAIT_MAX_MS);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                if (state.isCandidate()) {
                    // 再监听 200ms
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    if (state.isCandidate()) {
                        becomeLeader();
                    }
                }
            }
        }, DISCOVER_WAIT_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * 成为 Leader
     */
    private void becomeLeader() {
        state.setCurrentRole(NodeRole.LEADER);
        String localIp = getLocalIp();
        state.setSelfIp(localIp);
        state.setLeaderIp(localIp);

        LOGGER.info("Became LEADER: " + localIp);
        sendBroadcast(createLeaderHereMsg());

        // 定时发送心跳
        scheduler.scheduleAtFixedRate(() -> {
            if (state.isLeader()) {
                sendBroadcast(createHeartbeatMsg());
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * 成为 Follower
     */
    public void becomeFollower(String leaderIp) {
        if (state.isLeader()) return;

        state.setCurrentRole(NodeRole.FOLLOWER);
        state.setLeaderIp(leaderIp);

        LOGGER.info("Became FOLLOWER, leader: " + leaderIp);

        // 启动心跳检测
        scheduler.scheduleAtFixedRate(() -> {
            if (!state.isFollower()) return;
            // TODO: 检测 Leader 存活
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * 监听循环
     */
    private void listenLoop() {
        byte[] buffer = new byte[4096];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String msg = new String(packet.getData(), 0, packet.getLength());
                handleMessage(msg, packet.getAddress());
            } catch (IOException e) {
                if (running) {
                    LOGGER.log(Level.WARNING, "UDP receive error", e);
                }
            }
        }
    }

    /**
     * 处理接收到的消息
     */
    private void handleMessage(String json, InetAddress sender) {
        try {
            if (json.contains("\"type\":\"DISCOVER\"")) {
                if (state.isLeader()) {
                    sendUnicast(sender, createLeaderHereMsg());
                }
            } else if (json.contains("\"type\":\"LEADER_HERE\"")) {
                String leaderIp = extractField(json, "leaderIp");
                if (leaderIp != null) {
                    becomeFollower(leaderIp);
                }
            } else if (json.contains("\"type\":\"HEARTBEAT\"")) {
                // Leader 存活，继续保持 Follower
            } else if (json.contains("\"type\":\"LEADER_DOWN\"")) {
                if (state.isFollower()) {
                    state.setCurrentRole(NodeRole.CANDIDATE);
                    startElection();
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error handling UDP message", e);
        }
    }

    /**
     * 发送广播
     */
    private void sendBroadcast(String msg) {
        try {
            byte[] data = msg.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, broadcastAddr);
            socket.send(packet);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "UDP broadcast error", e);
        }
    }

    /**
     * 发送单播
     */
    private void sendUnicast(InetAddress addr, String msg) {
        try {
            byte[] data = msg.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, addr, UDP_PORT);
            socket.send(packet);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "UDP unicast error", e);
        }
    }

    /**
     * 创建 DISCOVER 消息
     */
    private String createDiscoverMsg() {
        return String.format("{\"type\":\"DISCOVER\",\"nodeId\":\"%s\"}", state.getNodeId());
    }

    /**
     * 创建 LEADER_HERE 消息
     */
    private String createLeaderHereMsg() {
        return String.format("{\"type\":\"LEADER_HERE\",\"leaderIp\":\"%s\",\"port\":%d}",
            getLocalIp(), state.getHttpPort());
    }

    /**
     * 创建心跳消息
     */
    private String createHeartbeatMsg() {
        return String.format("{\"type\":\"HEARTBEAT\",\"leaderIp\":\"%s\"}", state.getLeaderIp());
    }

    /**
     * 创建 LEADER_DOWN 消息
     */
    private String createLeaderDownMsg() {
        return "{\"type\":\"LEADER_DOWN\"}";
    }

    /**
     * 从 JSON 提取字段
     */
    private String extractField(String json, String field) {
        String search = "\"" + field + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return null;
        return json.substring(start, end);
    }

    /**
     * 获取本机 IP
     */
    private String getLocalIp() {
        try (DatagramSocket sock = new DatagramSocket()) {
            sock.connect(InetAddress.getByName("8.8.8.8"), 9999);
            return sock.getLocalAddress().getHostAddress();
        } catch (IOException e) {
            return "127.0.0.1";
        }
    }

    /**
     * 广播 Leader 下线
     */
    public void broadcastLeaderDown() {
        if (state.isLeader()) {
            sendBroadcast(createLeaderDownMsg());
        }
    }

    @Override
    public void close() {
        running = false;
        broadcastLeaderDown();

        if (socket != null) {
            socket.close();
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }
}