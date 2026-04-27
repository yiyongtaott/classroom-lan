package io.classroomlan.game;

/**
 * 游戏类型枚举
 * CONTEXT.txt 中的 GameType
 */
public enum GameType {
    /**
     * 你画我猜 - 3~8人，Canvas 实时绘图
     */
    DRAW_GUESS,

    /**
     * 快问快答 - 2~20人，题目库 + 倒计时
     */
    QUIZ,

    /**
     * 谁是卧底 - 4~12人，纯文字，状态机驱动
     */
    WEREWOLF
}