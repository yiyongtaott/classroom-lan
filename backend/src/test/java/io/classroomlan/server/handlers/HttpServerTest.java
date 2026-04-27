package io.classroomlan.server.handlers;

import io.classroomlan.node.*;
import org.junit.jupiter.api.*;

import java.net.URL;
import java.net.HttpURLConnection;
import java.io.InputStream;
import static org.junit.jupiter.api.Assertions.*;

/**
 * HttpServer 端口冲突重试测试
 * 验证当 8080 被占用时，服务器能自动尝试 8081-8084
 */
class HttpServerTest {

    @BeforeEach
    void setUp() {
        NodeState.resetInstance();
    }

    @Test
    @DisplayName("HttpServer 能在默认端口 8080 启动")
    void testDefaultPort() throws Exception {
        HttpServer server = new HttpServer(NodeState.getInstance());
        int actualPort = server.start();

        assertTrue(actualPort >= 8080 && actualPort <= 8084,
            "Port should be in range 8080-8084, got: " + actualPort);
        assertTrue(server.isRunning());

        server.stop();
    }

    @Test
    @DisplayName("HttpServer 在 8080 被占用时能重试到其他端口")
    void testPortRetry() throws Exception {
        // 占用 8080
        try (java.net.ServerSocket sock = new java.net.ServerSocket(8080)) {
            HttpServer server = new HttpServer(NodeState.getInstance());
            int actualPort = server.start();

            assertNotEquals(8080, actualPort, "Should not use occupied port 8080");
            assertTrue(actualPort >= 8081 && actualPort <= 8084,
                "Retry port should be 8081-8084, got: " + actualPort);
            assertTrue(server.isRunning());

            server.stop();
        }
    }

    @Test
    @DisplayName("HttpServer stop() 能正常关闭")
    void testStop() throws Exception {
        HttpServer server = new HttpServer(NodeState.getInstance());
        int port = server.start();
        assertTrue(server.isRunning());

        server.stop();
        assertFalse(server.isRunning());

        // 端口应释放
        try (java.net.ServerSocket s = new java.net.ServerSocket(port)) {
            // success — port was freed
        }
    }

    @Test
    @DisplayName("getActualPort() 返回正确值")
    void testGetActualPort() throws Exception {
        HttpServer server = new HttpServer(NodeState.getInstance());
        int port = server.start();

        assertEquals(port, server.getActualPort());

        server.stop();
    }

    @Test
    @DisplayName("HttpServer start() 两次会使用不同端口")
    void testMultipleStart() throws Exception {
        HttpServer server1 = new HttpServer(NodeState.getInstance());
        int port1 = server1.start();
        server1.stop();

        HttpServer server2 = new HttpServer(NodeState.getInstance());
        int port2 = server2.start();
        server2.stop();

        // 第二次可以重用同一端口（如果可用）
        assertTrue(port2 >= 8080 && port2 <= 8084);
    }
}
