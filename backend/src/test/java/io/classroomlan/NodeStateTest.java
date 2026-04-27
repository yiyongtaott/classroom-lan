package io.classroomlan;

import io.classroomlan.node.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NodeState 单例模式测试
 */
class NodeStateTest {

    @BeforeEach
    void setUp() {
        // 重置单例以确保测试隔离
        NodeState.resetInstance();
    }

    @AfterEach
    void tearDown() {
        NodeState.resetInstance();
    }

    @Test
    @DisplayName("初始状态应为 CANDIDATE")
    void testInitialRole() {
        NodeState state = NodeState.getInstance();
        assertEquals(NodeRole.CANDIDATE, state.getCurrentRole());
    }

    @Test
    @DisplayName("nodeId 应该自动生成且唯一")
    void testNodeIdGeneration() {
        NodeState state1 = NodeState.getInstance();
        NodeState.resetInstance();
        NodeState state2 = NodeState.getInstance();

        assertNotNull(state1.getNodeId());
        assertNotNull(state2.getNodeId());
        assertNotEquals(state1.getNodeId(), state2.getNodeId());
    }

    @Test
    @DisplayName("切换到 LEADER 角色")
    void testBecomeLeader() {
        NodeState state = NodeState.getInstance();
        state.setCurrentRole(NodeRole.LEADER);

        assertTrue(state.isLeader());
        assertFalse(state.isFollower());
        assertFalse(state.isCandidate());
    }

    @Test
    @DisplayName("切换到 FOLLOWER 角色并设置 Leader IP")
    void testBecomeFollower() {
        NodeState state = NodeState.getInstance();
        state.setCurrentRole(NodeRole.FOLLOWER);
        state.setLeaderIp("192.168.1.100");

        assertTrue(state.isFollower());
        assertEquals("192.168.1.100", state.getLeaderIp());
    }

    @Test
    @DisplayName("默认端口配置正确")
    void testDefaultPorts() {
        NodeState state = NodeState.getInstance();
        assertEquals(8080, state.getHttpPort());
        assertEquals(8081, state.getWsPort());
        assertEquals(9999, state.getUdpPort());
    }

    @Test
    @DisplayName("单例模式：多次获取返回同一实例")
    void testSingleton() {
        NodeState state1 = NodeState.getInstance();
        NodeState state2 = NodeState.getInstance();
        assertSame(state1, state2);
    }
}