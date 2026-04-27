package io.classroomlan.server.handlers;

import io.classroomlan.node.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApiFileHandler 核心逻辑测试
 */
class FileHandlerTest {

    @BeforeEach
    void setUp() {
        NodeState.resetInstance();
    }

    @Test
    @DisplayName("ApiFileHandler 能创建")
    void testFileHandlerCreate() {
        NodeState state = NodeState.getInstance();
        ApiFileHandler handler = new ApiFileHandler(state);

        // 验证 handler 不为 null
        assertNotNull(handler);
    }

    @Test
    @DisplayName("NodeState 作为参数正常传递")
    void testNodeStatePassed() {
        NodeState state = NodeState.getInstance();
        state.setCurrentRole(NodeRole.LEADER);

        ApiFileHandler handler = new ApiFileHandler(state);

        assertNotNull(handler);
        assertEquals(NodeRole.LEADER, state.getCurrentRole());
    }
}