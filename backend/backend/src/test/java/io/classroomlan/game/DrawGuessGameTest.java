package io.classroomlan.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

/**
 * DrawGuessGame 单元测试
 * 测试：猜词验证、得分、轮换、词库加载
 */
class DrawGuessGameTest {

    @Test
    void testCorrectGuessAddsScore() {
        GameRoom room = new GameRoom("r1", "draw", "drawer1");
        room.addPlayer("drawer1");
        room.addPlayer("guesser1");
        DrawGuessGame game = new DrawGuessGame(room);
        game.setCurrentDrawer("drawer1");
        game.setCurrentWord("苹果");

        // 模拟选中词语（实际流程中主持人选词后 startRound）
        // 这里直接触发猜测
        GameActionResult result = game.handleAction("guesser1", "GUESS", Map.of("word", "苹果"));

        assertEquals(GameActionResult.Type.BROADCAST, result.type);
        assertTrue(result.data.get("correct") instanceof Boolean && (Boolean) result.data.get("correct"));
        assertEquals(10, game.getScores().get("guesser1"));
        assertEquals(10, game.getScores().get("drawer1"));
    }

    @Test
    void testWrongGuessDoesNotAwardPoints() {
        GameRoom room = new GameRoom("r2", "draw", "drawer1");
        room.addPlayer("drawer1");
        room.addPlayer("guesser1");
        DrawGuessGame game = new DrawGuessGame(room);
        game.setCurrentDrawer("drawer1");
        game.setCurrentWord("苹果");

        GameActionResult result = game.handleAction("guesser1", "GUESS", Map.of("word", "香蕉"));

        assertEquals(GameActionResult.Type.PRIVATE, result.type);
        assertTrue(result.data.get("yourGuess").equals("香蕉"));
        assertNull(game.getScores().get("guesser1"));
    }

    @Test
    void testOnlyDrawerCanSendHints() {
        GameRoom room = new GameRoom("r3", "draw", "drawer1");
        room.addPlayer("drawer1");
        room.addPlayer("other1");
        DrawGuessGame game = new DrawGuessGame(room);
        game.setCurrentDrawer("drawer1");

        GameActionResult result = game.handleAction("other1", "HINT", Map.of("text", "这是提示"));
        assertEquals(GameActionResult.Type.ERROR, result.type);
        assertTrue(result.error.contains("Only the drawer"));
    }

    @Test
    void testDrawerCanSendHint() {
        GameRoom room = new GameRoom("r4", "draw", "drawer1");
        room.addPlayer("drawer1");
        DrawGuessGame game = new DrawGuessGame(room);
        game.setCurrentDrawer("drawer1");

        GameActionResult result = game.handleAction("drawer1", "HINT", Map.of("text", "提示"));
        assertEquals(GameActionResult.Type.BROADCAST, result.type);
        assertEquals("HINT", result.event);
    }
}
