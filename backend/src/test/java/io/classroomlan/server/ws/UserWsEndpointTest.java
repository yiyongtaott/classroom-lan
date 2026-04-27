package io.classroomlan.server.ws;

import io.classroomlan.node.NodeState;
import jakarta.websocket.*;
import org.junit.jupiter.api.*;
import org.glassfish.tyrus.server.Server;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserWsEndpoint 多 Tab 去重逻辑测试
 *
 * 场景: 同一浏览器实例打开多个 Tab → 携带相同 localStorage.clientId
 * 期望: 旧连接被关闭，新连接保持；在线用户数不重复计数
 */
class UserWsEndpointTest {

    private Server tyrusServer;
    private Session session1, session2;
    private static NodeState nodeState;

    @BeforeAll
    static void setUpNodeState() {
        NodeState.resetInstance();
        nodeState = NodeState.getInstance();
    }

    @AfterAll
    static void tearDownNodeState() {
        NodeState.resetInstance();
    }

    /**
     * 启动 Tyrus 测试服务器（嵌入模式）
     */
    private void startTyrus() throws Exception {
        tyrusServer = new Server("localhost", 0, "/ws", null, UserWsEndpoint.class);
        // 注入 NodeState
        UserWsEndpoint.setNodeState(nodeState);

        ExecutorService exec = Executors.newSingleThreadExecutor();
        exec.submit(() -> tyrusServer.start());
        exec.shutdown();
        exec.awaitTermination(2, TimeUnit.SECONDS);
    }

    private void stopTyrus() throws Exception {
        if (tyrusServer != null) {
            tyrusServer.stop();
        }
    }

    @Test
    @DisplayName("两个 Tab 使用相同 clientId → 第二个连接会替换第一个")
    void testDeduplicationSameClientId() throws Exception {
        startTyrus();

        // 两个 WebSocket 连接（模拟两个 Tab）
        // 每个连接在 OPEN 后发送 init JSON，携带相同 clientId
        Client第一次连接 = createMockSession("client-123", "UserA", "avatar1");
        Client第二次连接 = createMockSession("client-123", "UserA", "avatar1");

        // 连接1 应被替换
        assertTrue(第一次连接.wasClosedByServer());
        assertFalse(第二次连接.wasClosedByServer());

        // 在线用户数应为 1
        assertEquals(1, nodeState.getOnlineUserCount());

        stopTyrus();
    }

    @Test
    @DisplayName("两个 Tab 使用不同 clientId → 视为两个独立用户")
    void testDifferentClientIdsAreIndependent() throws Exception {
        startTyrus();

        Client c1 = createMockSession("client-A", "UserA", "avatar1");
        Client c2 = createMockSession("client-B", "UserB", "avatar2");

        assertFalse(c1.wasClosedByServer());
        assertFalse(c2.wasClosedByServer());

        // 在线用户数应为 2
        assertEquals(2, nodeState.getOnlineUserCount());

        stopTyrus();
    }

    @Test
    @DisplayName("第一个 Tab 断开后，第二个 Tab 能正常重连")
    void testReconnectAfterFirstDisconnect() throws Exception {
        startTyrus();

        Client c1 = createMockSession("client-X", "UserX", "avatarX");
        assertFalse(c1.wasClosedByServer());

        // c1 断开
        c1.close();
        Thread.sleep(200);

        Client c2 = createMockSession("client-X", "UserX", "avatarX");
        assertFalse(c2.wasClosedByServer());
        assertEquals(1, nodeState.getOnlineUserCount());

        stopTyrus();
    }

    // ── 辅助：模拟 WS 连接 ───────────────────────────────────────────────

    private static class MockClient {
        private Session session;
        boolean closedByServer = false;

        MockClient(Session s) { this.session = s; }
        boolean wasClosedByServer() { return closedByServer; }
        void markClosed() { this.closedByServer = true; }
        void close() throws Exception {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    private MockClient createMockSession(String clientId, String nickname, String avatar) throws Exception {
        // 简化：直接调用 onOpen → onMessage 模拟完整流程
        // 在真实集成测试中应使用 Tyrus 客户端库
        throw new UnsupportedOperationException(
            "集成测试需要 Tyrus 客户端依赖，建议在 TestContainers 环境中运行。单元测试 stub 见下文。"
        );
    }
}

/**
 * UserWsEndpoint 单元测试（逻辑隔离）
 * 不启动真实 WebSocket 服务器，直接测试 handleInit 和 onClose 的逻辑
 */
class UserWsEndpointUnitTest {

    @BeforeEach
    void setUp() {
        NodeState.resetInstance();
        UserWsEndpoint.setNodeState(NodeState.getInstance());
    }

    @Test
    @DisplayName("clientId 重复时旧会话的 userId 被从在线列表移除")
    void testDuplicateClientIdRemovesOldUser() throws Exception {
        // 模拟：session1 (userId=u1) 携带 clientId=c1 连接
        Session session1 = MockSession.create("sess-1");
        Session session2 = MockSession.create("sess-2");

        UserWsEndpoint endpoint = new UserWsEndpoint();

        // 第一次连接
        endpoint.onOpen(session1);
        session1.getUserProperties().put("userId", "u1");
        endpoint.onMessage("{\"action\":\"init\",\"clientId\":\"c1\",\"nickname\":\"A\"}", session1);

        // 第二次连接，相同 clientId
        endpoint.onOpen(session2);
        session2.getUserProperties().put("userId", "u2");
        endpoint.onMessage("{\"action\":\"init\",\"clientId\":\"c1\",\"nickname\":\"A\"}", session2);

        // session1 应被关闭，u1 应被移除在线
        assertTrue(session1.isClosed());
        assertEquals(1, NodeState.getInstance().getOnlineUserCount());
    }

    @Test
    @DisplayName("同一 clientId 重新连接时保留用户身份")
    void testSameClientIdKeepsIdentity() throws Exception {
        NodeState state = NodeState.getInstance();
        UserWsEndpoint endpoint = new UserWsEndpoint();

        Session s1 = MockSession.create("s1");
        endpoint.onOpen(s1);
        endpoint.onMessage("{\"action\":\"init\",\"clientId\":\"cid\",\"nickname\":\"Bob\"}", s1);

        Session s2 = MockSession.create("s2");
        endpoint.onOpen(s2);
        endpoint.onMessage("{\"action\":\"init\",\"clientId\":\"cid\",\"nickname\":\"Bob\"}", s2);

        // 在线列表应只有 1 个 Bob
        assertEquals(1, state.getOnlineUserCount());
        assertTrue(state.getOnlineUsers().containsKey("cid"));
    }
}

// ── 简单的 Session Mock ────────────────────────────────────────────────

/**
 * 最小 Session 模拟，仅支持 UserProperties 和基本方法
 */
class MockSession implements Session {
    private final String id;
    private final Map<String, Object> userProperties = new ConcurrentHashMap<>();
    private boolean open = true;
    private CloseReason closeReason;

    MockSession(String id) { this.id = id; }

    static MockSession create(String id) {
        return new MockSession(id);
    }

    void close() throws Exception {
        this.open = false;
        this.closeReason = new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "test");
    }

    boolean isClosed() { return !open; }

    @Override public String getId() { return id; }
    @Override public boolean isOpen() { return open; }
    @Override public void close() throws IOException { close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "closed")); }
    @Override public void close(CloseReason reason) throws IOException { this.open = false; this.closeReason = reason; }
    @Override public CloseReason getCloseReason() { return closeReason; }
    @Override public java.util.Map<String, List<Extension>> getExtensions() { return Map.of(); }
    @Override public List<String> getRequestParameterMap() { return List.of(); }
    @Override public boolean isSecure() { return false; }
    @Override public boolean isEncrypted() { return false; }
    @Override public String getNegotiatedSubProtocol() { return null; }
    @Override public List<String> getNegotiatedExtensions() { return List.of(); }
    @Override public java.net.URI getRequestURI() { return null; }
    @Override public java.util.Map<String, String> getRequestParameterMap2() { return Map.of(); }
    @Override public java.net.InetSocketAddress getLocalAddress() { return null; }
    @Override public java.net.InetSocketAddress getRemoteAddress() { return null; }
    @Override public java.util.Map<String, String> getPathParameters() { return Map.of(); }
    @Override public <T> T getUserProperties2() { return null; }
    @Override public java.util.Map<String, Object> getUserProperties() { return userProperties; }
    @Override public void addMessageHandler(MessageHandler mh) {}
    @Override public void addMessageHandler(Class<?> c, MessageHandler mh) {}
    @Override public <T> MessageHandler getMessageHandlers(Class<T> c) { return null; }
    @Override public void removeMessageHandler(Class<?> c) {}
    @Override public java.util.Set<MessageHandler> getMessageHandlers() { return Set.of(); }
    @Override public javax.websocket.RemoteEndpoint.Async getAsyncRemote() { return null; }
    @Override public javax.websocket.RemoteEndpoint.Basic getBasicRemote() { return new MockBasicRemote(this); }
    @Override public java.util.Set<Session> getOpenSessions() { return Set.of(this); }
    @Override public void dispatch(String s) throws IOException {}
    @Override public Object getUserProperties3() { return userProperties; }
    @Override public java.util.Map<String, Object> getUserProperties4() { return userProperties; }

    // ── 仅用于测试的 BasicRemote ────────────────────────────────────
    private static class MockBasicRemote implements RemoteEndpoint.Basic {
        private final Session session;
        MockBasicRemote(Session s) { this.session = s; }
        @Override public void sendText(String s) {}
        @Override public void sendBinary(ByteBuffer bb) {}
        @Override public void sendBinary(ByteBuffer bb, int i) {}
        @Override public void sendText(String s, boolean b) {}
        @Override public void sendPong(Pong p) {}
        @Override	public void sendText(String s, int i) {}
        @Override public void sendObject(Object o) {}
        @Override public void sendObject(Object o, boolean b) {}
    }
}
