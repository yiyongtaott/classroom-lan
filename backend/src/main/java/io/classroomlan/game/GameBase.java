package io.classroomlan.game;

import java.util.Map;

/**
 * 游戏基类 — 定义所有游戏共有的接口
 *
 * 游戏通过 handleAction 处理客户端动作，通过 tick 进行定时器推进
 */
public abstract class GameBase {
    /**
     * 处理游戏动作
     *
     * @param playerName 发起动作的玩家名
     * @param actionType 动作类型
     * @param payload    动作参数
     * @return 处理结果（BROADCAST / PRIVATE / ERROR）
     */
    public abstract GameActionResult handleAction(String playerName, String actionType, Map<String, Object> payload);

    /**
     * 定时器 tick — 每秒由 GameWsEndpoint 调用
     * 用于处理倒计时、超时等逻辑
     */
    public abstract void tick();
}
