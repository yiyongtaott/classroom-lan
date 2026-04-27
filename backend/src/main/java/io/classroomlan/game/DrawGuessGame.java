package io.classroomlan.game;

/**
 * 你画我猜逻辑
 */
public class DrawGuessGame {
    private final GameRoom room;
    private String currentDrawer;
    private String currentWord;

    public DrawGuessGame(GameRoom room) {
        this.room = room;
    }

    public void setCurrentWord(String word) {
        this.currentWord = word;
    }

    public String getCurrentWord() {
        return currentWord;
    }

    public void setCurrentDrawer(String playerId) {
        this.currentDrawer = playerId;
    }

    public String getCurrentDrawer() {
        return currentDrawer;
    }
}