package io.classroomlan.game;

import java.util.*;

/**
 * 游戏房间
 */
public class GameRoom {
    private final String roomId;
    private final String gameType;
    private final List<String> players = new ArrayList<>();
    private String hostId;

    public GameRoom(String roomId, String gameType, String hostId) {
        this.roomId = roomId;
        this.gameType = gameType;
        this.hostId = hostId;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getGameType() {
        return gameType;
    }

    public List<String> getPlayers() {
        return players;
    }

    public void addPlayer(String playerId) {
        if (!players.contains(playerId)) {
            players.add(playerId);
        }
    }

    public void removePlayer(String playerId) {
        players.remove(playerId);
    }
}