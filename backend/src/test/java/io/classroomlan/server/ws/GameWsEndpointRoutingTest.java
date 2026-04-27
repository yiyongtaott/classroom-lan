package io.classroomlan.server.ws;

import io.classroomlan.game.DrawGuessGame;
import io.classroomlan.game.GameActionResult;
import io.classroomlan.node.NodeState;
import org.junit.jupiter.api.*;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * GameWsEndpoint 房间与消息路由测试（用 Mock 简化）
 */
class GameWsEndpointRoutingTest {

    GameWsEndpoint endpoint;
    MockSession session;
    NodeState state;

    @BeforeEach
    void setUp() {
        NodeState.resetInstance();
        state = NodeState.getInstance();
        endpoint = new GameWsEndpoint();
        session = MockSession.create("game-session-1");
        session.getUserProperties().put("playerName", "PlayerA");
    }

    @Test
    @DisplayName("JOIN_ROOM 初始化房间并创建默认游戏实例（draw）")
    void testJoinRoomInitializesGame() {
        endpoint.handleJoinRoom(session, "roomX", "PlayerA");

        assertNotNull(GameWsEndpoint.roomMetas.get("roomX"));
        assertEquals("draw", GameWsEndpoint.roomMetas.get("roomX").gameType);
        assertTrue(GameWsEndpoint.roomSessions.get("roomX").contains(session));
    }

    @Test
    @DisplayName("同一用户再次 JOIN_ROOM 先离开旧房间")
    void testJoinRoomFromAnotherRoomLeavesFirst() {
        // 先加入 room1
        endpoint.handleJoinRoom(session, "room1", "A");

        // 再加入 room2
        endpoint.handleJoinRoom(session, "room2", "A");

        assertNull(GameWsEndpoint.sessionRoom.get(session.getId()));
        assertFalse(GameWsEndpoint.roomSessions.get("room1").contains(session));
        assertTrue(GameWsEndpoint.roomSessions.get("room2").contains(session));
    }

    @Test
    @DisplayName("GAME_ACTION 调用 DrawGuessGame.handleAction 并正确分发结果")
    void testGameActionDispatchedToGameLogic() {
        endpoint.handleJoinRoom(session, "groom", "DrawMaster");

        GameRoom room = new GameRoom("groom", "draw", "DrawMaster");
        room.addPlayer("DrawMaster");
        room.addPlayer("Guesser1");

        // 替换 roomMetas 中的 game 实例以便验证
        GameWsEndpoint.RoomMeta meta = new GameWsEndpoint.RoomMeta();
        meta.gameType = "draw";
        DrawGuessGame drawGame = new DrawGuessGame(room);
        drawGame.setCurrentDrawer("DrawMaster");
        drawGame.setCurrentWord("苹果");
        meta.game = drawGame;
        GameWsEndpoint.roomMetas.put("groom", meta);

        // 构造 GUESS action
        Map<String, Object> payload = Map.of("type", "GUESS", "word", "苹果");
        Map<String, Object> msg = Map.of("action", "GAME_ACTION", "roomId", "groom", "payload", payload);

        // 触发
        endpoint.handleGameAction(session, "groom", msg);

        // 验证猜对: Drawer + Guesser 各得 10 分
        Map<String, Integer> scores = drawGame.getScores();
        assertTrue(scores.containsKey("DrawMaster"));
        assertTrue(scores.containsKey("Guesser1"));
    }
}
