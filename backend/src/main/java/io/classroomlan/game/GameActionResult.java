package io.classroomlan.game;

import java.util.Map;

/**
 * 游戏动作结果封装
 *
 * 用于游戏逻辑向 WebSocket 端点返回处理结果，指示需要
 * 广播（BROADCAST）、私发（PRIVATE）或错误（ERROR）。
 */
public class GameActionResult {
    public enum Type { BROADCAST, PRIVATE, ERROR }

    public Type type;
    public String event;
    public Map<String, Object> data;
    public String targetPlayer;   // 用于 PRIVATE
    public String error;          // 用于 ERROR

    static GameActionResult broadcast(String event, Map<String, Object> data) {
        GameActionResult r = new GameActionResult();
        r.type = Type.BROADCAST;
        r.event = event;
        r.data = data;
        return r;
    }

    static GameActionResult privateMsg(String player, String event, Map<String, Object> data) {
        GameActionResult r = new GameActionResult();
        r.type = Type.PRIVATE;
        r.targetPlayer = player;
        r.event = event;
        r.data = data;
        return r;
    }

    static GameActionResult error(String msg) {
        GameActionResult r = new GameActionResult();
        r.type = Type.ERROR;
        r.error = msg;
        return r;
    }
}
