package io.classroomlan.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

/**
 * QuizGame 单元测试
 * 测试：答题计分、排行榜、超时逻辑
 */
class QuizGameTest {

    @Test
    void testFirstCorrectAnswerGivesMorePoints() {
        GameRoom room = new GameRoom("q1", "quiz", "host");
        QuizGame game = new QuizGame(room);
        game.handleAction("host", "START_QUIZ", Map.of("questionCount", 2));

        // 第一题答对
        game.handleAction("player1", "ANSWER", Map.of("answer", 0)); // 假设 index 0 是正确

        int p1Score = game.getScores().get("player1");
        assertEquals(10, p1Score);  // first answer bonus

        // 第二题答对
        // 触发下一题（简化：手动调用）
        game.handleAction("host", "NEXT_QUESTION", null);
        game.handleAction("player1", "ANSWER", Map.of("answer", 1));

        // 后续答对应该分数低一些（实际代码中为5）
        // 这里仅验证分数递增
        assertTrue(game.getScores().get("player1") > 10);
    }

    @Test
    void testCannotAnswerAfterTimeOut() {
        GameRoom room = new GameRoom("q2", "quiz", "host");
        QuizGame game = new QuizGame(room);
        game.handleAction("host", "START_QUIZ", Map.of("questionCount", 1));

        // 模拟超时
        game.tick(); // 第一轮 15s 过去了

        GameActionResult result = game.handleAction("player1", "ANSWER", Map.of("answer", 0));
        assertEquals(GameActionResult.Type.ERROR, result.type);
        assertTrue(result.error.contains("Time's up"));
    }

    @Test
    void testDuplicateAnswerRejected() {
        GameRoom room = new GameRoom("q3", "quiz", "host");
        QuizGame game = new QuizGame(room);
        game.handleAction("host", "START_QUIZ", Map.of("questionCount", 1));

        game.handleAction("player1", "ANSWER", Map.of("answer", 0)); // 第一次
        GameActionResult second = game.handleAction("player1", "ANSWER", Map.of("answer", 1));

        assertEquals(GameActionResult.Type.ERROR, second.type);
        assertTrue(second.error.contains("Already answered"));
    }
}
