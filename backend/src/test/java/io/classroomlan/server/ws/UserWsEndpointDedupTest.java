package io.classroomlan.server.ws;

import io.classroomlan.node.NodeState;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserWsEndpoint deduplication logic — 单元测试（模拟 Session）
 */
class UserWsEndpointDedupTest {

    @BeforeEach
    void initNodeState() {
        NodeState.resetInstance();
        UserWsEndpoint.setNodeState(NodeState.getInstance());
    }

    @Test
    @DisplayName("初始化客户端后 userId = clientId，且加入在线列表")
    void testInitAddsUser() throws Exception {
        MockSession session = MockSession.create("s1");
        UserWsEndpoint endpoint = new UserWsEndpoint();

        endpoint.onOpen(session);
        // 模拟 client 发送 init JSON
        endpoint.onMessage("{\"action\":\"init\",\"clientId\":\"client-001\",\"nickname\":\"张三\",\"avatar\":\"1\"}", session);

        // 验证 userId 即 clientId
        assertEquals("client-001", session.getUserProperties().get("userId"));
        assertEquals("client-001", session.getUserProperties().get("clientId"));

        // 验证 NodeState 在线人数
        assertEquals(1, NodeState.getInstance().getOnlineCount());
    }

    @Test
    @DisplayName("相同 clientId 第二次连接会关闭旧会话并替换")
    void testSameClientIdReplacesOldSession() throws Exception {
        NodeState state = NodeState.getInstance();
        UserWsEndpoint endpoint = new UserWsEndpoint();

        // Tab 1
        MockSession s1 = MockSession.create("sess-1");
        endpoint.onOpen(s1);
        endpoint.onMessage("{\"action\":\"init\",\"clientId\":\"same-id\",\"nickname\":\"User\"}", s1);
        assertEquals(1, state.getOnlineCount());
        assertTrue(s1.isOpen());

        // Tab 2（相同 clientId）
        MockSession s2 = MockSession.create("sess-2");
        endpoint.onOpen(s2);
        endpoint.onMessage("{\"action\":\"init\",\"clientId\":\"same-id\",\"nickname\":\"User\"}", s2);

        // s1 应被关闭
        assertFalse(s1.isOpen(), "Old session should be closed");
        // 在线人数仍为 1
        assertEquals(1, state.getOnlineCount());
        // s2 保持开放且是激活会话
        assertTrue(s2.isOpen());
        assertEquals("same-id", s2.getUserProperties().get("userId"));
    }

    @Test
    @DisplayName("不同 clientId 各自计数为独立用户")
    void testDifferentClientIdsCountSeparately() throws Exception {
        NodeState state = NodeState.getInstance();
        UserWsEndpoint endpoint = new UserWsEndpoint();

        MockSession s1 = MockSession.create("sA");
        endpoint.onOpen(s1);
        endpoint.onMessage("{\"action\":\"init\",\"clientId\":\"id-A\",\"nickname\":\"A\"}", s1);

        MockSession s2 = MockSession.create("sB");
        endpoint.onOpen(s2);
        endpoint.onMessage("{\"action\":\"init\",\"clientId\":\"id-B\",\"nickname\":\"B\"}", s2);

        assertEquals(2, state.getOnlineCount());

        s1.close();
        Thread.sleep(50);
        assertEquals(1, state.getOnlineCount());
    }
}
