package io.classroomlan.game;

/**
 * 谁是卧底逻辑
 */
public class WerewolfGame {
    private final GameRoom room;
    private String spyPlayerId;

    public WerewolfGame(GameRoom room) {
        this.room = room;
    }

    public void setSpyPlayer(String playerId) {
        this.spyPlayerId = playerId;
    }

    public String getSpyPlayerId() {
        return spyPlayerId;
    }
}