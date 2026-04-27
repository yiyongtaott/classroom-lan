package io.classroomlan.node;

/**
 * 节点角色枚举
 */
public enum NodeRole {
    /**
     * 候选者 - 刚启动时的短暂状态
     */
    CANDIDATE,

    /**
     * Leader - 运行 HTTP/WS Server，托管前端，处理业务逻辑
     */
    LEADER,

    /**
     * Follower - 通过浏览器访问 Leader
     */
    FOLLOWER
}