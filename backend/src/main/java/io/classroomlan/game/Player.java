package io.classroomlan.game;

/**
 * 玩家模型
 * CONTEXT.txt 中的 Player
 */
public class Player {
    private final String id;
    private final String name;
    private int score;
    private boolean ready;

    public Player(String id, String name) {
        this.id = id;
        this.name = name;
        this.score = 0;
        this.ready = false;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getScore() {
        return score;
    }

    public void addScore(int points) {
        this.score += points;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }
}