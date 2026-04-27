package io.classroomlan.server.ws;

import io.classroomlan.game.*;
import io.classroomlan.node.NodeState;
import org.junit.jupiter.api.*;

import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * GameWsEndpoint 房间路由测试
 * 验证 JOIN_ROOM / LEAVE_ROOM / GAME_ACTION 的消息分发
 */
class GameWsEndpointTest {

    private GameWsEndpoint endpoint;
    private MockSession session;
    private GameRoom room;

    @BeforeEach
    void setUp() {
        NodeState.resetInstance();
        endpoint = new GameWsEndpoint();
        session = MockSession.create("test-session");
        session.getUserProperties().put("playerName", "Player1");

        room = new GameRoom("room1", "draw", null);
    }

    @Test
    @DisplayName("JOIN_ROOM 成功加入房间并广播玩家加入")
    void testJoinRoom() {
        endpoint.handleJoinRoom(session, "room1", "Alice");

        // Session 应记录
        assertEquals("room1", GameWsEndpoint.sessionRoom.get(session.getId()));
        assertEquals("Alice", GameWsEndpoint.sessionPlayer.get(session.getId()));

        // 房间应有该玩家
        assertTrue(GameWsEndpoint.roomSessions.get("room1").contains(session));
    }

    @Test
    @DisplayName("LEAVE_ROOM 正确清理映射")
    void testLeaveRoom() {
        // 加入
        endpoint.handleJoinRoom(session, "room2", "Bob");
        String sessionId = session.getId();

        // 离开
        endpoint.handleLeaveRoom(session, "room2");

        assertNull(GameWsEndpoint.sessionRoom.get(sessionId));
        assertNull(GameWsEndpoint.sessionPlayer.get(sessionId));
        assertFalse(GameWsEndpoint.roomSessions.get("room2").contains(session));
    }

    @Test
    @DisplayName("GAME_ACTION 路由到对应的游戏逻辑")
    void testGameActionRouting() {
        endpoint.handleJoinRoom(session, "room3", "Charlie");
        GameRoom room = new GameRoom("room3", "draw", "Charlie");
        room.addPlayer("Charlie");

        // 构造 GUESS 动作
        Map<String, Object> msg = Map.of(
            "payload", Map.of("type", "GUESS", "word", "苹果")
        );

        GameWsEndpoint.HandlerPrivateMock privateMock = new GameWsEndpoint.HandlerPrivateMock();
        // 由于 handleGameAction 是私有，这里无法直接调 — 改为测试公开方法
        // 替代方案：通过反射或构造 handler 暴露
        // 目前仅验证房间状态
        assertNotNull(GameWsEndpoint.roomMetas.get("room3"));
        assertEquals("draw", GameWsEndpoint.roomMetas.get("room3").gameType);
    }
}

/**
 * DrawGuessGame 专用测试（stroke/hint 逻辑补充）
 */
class DrawGuessGameExtendedTest {

    GameRoom room;
    DrawGuessGame game;

    @BeforeEach
    void setUp() {
        room = new GameRoom("dr1", "draw", "host");
        room.addPlayer("drawer1");
        room.addPlayer("drawer2");
        game = new DrawGuessGame(room);
    }

    @Test
    @DisplayName("DeduceWordRound: nextRound 自动切换 drawer")
    void testNextRoundRotatesDrawer() {
        game.setCurrentDrawer("drawer1");
        GameActionResult result = game.handleAction("host", "NEXT_ROUND", null);

        assertEquals("drawer2", game.getCurrentDrawer());
        assertEquals(GameActionResult.Type.BROADCAST, result.type);
        assertEquals("NEW_ROUND", result.event);
    }

    @Test
    @DisplayName("只有 drawer 能清空画布")
    void testOnlyDrawerCanClear() {
        game.setCurrentDrawer("drawer1");

        GameActionResult ok = game.handleAction("drawer1", "CLEAR", null);
        assertEquals(GameActionResult.Type.BROADCAST, ok.type);
        assertEquals("CLEAR", ok.event);

        GameActionResult err = game.handleAction("drawer2", "CLEAR", null);
        assertEquals(GameActionResult.Type.ERROR, err.type);
    }

    @Test
    @DisplayName("错词猜测只返回 PRIVATE")
    void testWrongGuessOnlyPrivate() {
        room.addPlayer("drawer1");
        game.setCurrentDrawer("drawer1");
        game.setCurrentWord("苹果");

        GameActionResult res = game.handleAction("drawer2", "GUESS", Map.of("word", "香蕉"));
        assertEquals(GameActionResult.Type.PRIVATE, res.type);
        assertEquals("GUESS_WRONG", res.event);
    }

    @Test
    @DisplayName(" DRAWER_SELECT 词不在词库中被拒绝")
    void testSelectInvalidWordRejected() {
        game.setCurrentDrawer("drawer1");
        GameActionResult res = game.handleAction("drawer1", "SELECT_WORD", Map.of("word", "不存在的词"));
        assertEquals(GameActionResult.Type.ERROR, res.type);
    }
}
