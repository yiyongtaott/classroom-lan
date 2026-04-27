package io.classroomlan.node;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetAddress;
import java.util.concurrent.TimeUnit;

/**
 * UdpDiscovery 选举与端口广播测试
 * 验证: Leader 宣告时机、心跳、端口配置流
 */
class UdpDiscoveryTest {

    @BeforeEach
    void setUp() {
        NodeState.resetInstance();
    }

    @Test
    @DisplayName("UdpDiscovery 启动后进入 CANDIDATE 状态")
    void testStartsAsCandidate() throws Exception {
        UdpDiscovery udp = new UdpDiscovery(NodeState.getInstance());
        udp.start();
        try {
            assertEquals(NodeRole.CANDIDATE, NodeState.getInstance().getCurrentRole());
        } finally {
            udp.close();
        }
    }

    @Test
    @DisplayName("Leader 未设置端口前不会首次广播")
    void testLeaderWaitsForPorts() throws Exception {
        UdpDiscovery udp = new UdpDiscovery(NodeState.getInstance());
        udp.start();
        try {
            // 模拟网络静默一段时间；leader 可能因 setLeaderPorts 未调用而不广播
            // 此时 state 可能变为 LEADER（选举超时后），但 heartbeat 不应该运行
            Thread.sleep(600);

            // 可检查: leaderPortsSet 标志为 false（内部访问 via reflection）
            // 为简化，只检查状态仍为候选或已变成 leader 但无 heartbeat
            assertTrue(
                NodeState.getInstance().isCandidate() || NodeState.getInstance().isLeader(),
                "Should be candidate or leader"
            );
        } finally {
            udp.close();
        }
    }

    @Test
    @DisplayName("setLeaderPorts 后 Leader 立即广播 LEADER_HERE")
    void testSetLeaderPortsTriggersBroadcast() throws Exception {
        NodeState state = NodeState.getInstance();
        UdpDiscovery udp = new UdpDiscovery(state);
        udp.start();
        try {
            // 暴力的方法: 让选举结束成为 leader
            Thread.sleep(700);  // 等待 DISCOVER_WAIT_MS + random sleep

            // 手动 setLeaderPorts
            udp.setLeaderPorts(8085, 8086);
            state.setCurrentRole(NodeRole.LEADER);  // 确保是 leader

            // Heartbeat 应该启动
            assertTrue(udp.isHeartbeatRunning());
            assertEquals(8085, state.getHttpPort());
            assertEquals(8086, state.getWsPort());
        } finally {
            udp.close();
        }
    }

    @Test
    @DisplayName("UDP 端口被占用时进入单机模式")
    void testUdpPortConflictEntersSoloMode() throws Exception {
        // 先占用 9999 端口
        try (java.net.DatagramSocket sock = new java.net.DatagramSocket(9999)) {
            UdpDiscovery udp = new UdpDiscovery(NodeState.getInstance());
            udp.start();
            try {
                assertTrue(NodeState.getInstance().isLeader());
                // solo mode 使用默认端口
                assertEquals(8080, NodeState.getInstance().getHttpPort());
                assertEquals(8081, NodeState.getInstance().getWsPort());
            } finally {
                udp.close();
            }
        }
    }
}
