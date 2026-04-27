package io.classroomlan.game;

/**
 * 快问快答逻辑
 */
public class QuizGame {
    private final GameRoom room;
    private int currentQuestionIndex;

    public QuizGame(GameRoom room) {
        this.room = room;
        this.currentQuestionIndex = 0;
    }

    public int getCurrentQuestionIndex() {
        return currentQuestionIndex;
    }

    public void nextQuestion() {
        currentQuestionIndex++;
    }
}