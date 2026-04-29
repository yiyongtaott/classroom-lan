package io.classroomlan.node;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局节点状态 (singleton)
 * CONTEXT.txt 中的 NODE STATE (singleton)
 */
public class NodeState {
    private static volatile NodeState instance;

    private NodeRole currentRole;
    private String leaderIp;           // null if self is Leader
    private int leaderPort;            // default 8080
    private String selfIp;
    private String nodeId;              // random UUID, generated once at startup
    private List<String> peerIds;      // known online peers

    // 在线用户信息 (Leader 端维护)
    private final Map<String, OnlineUser> onlineUsers = new ConcurrentHashMap<>();

    // 自己的用户信息 (发送到 Leader)
    private String nickname = "用户" + new Random().nextInt(1000);
    private String avatar = "1";

    private int httpPort;
    private int wsPort;
    private int udpPort;

    private NodeState() {
        this.currentRole = NodeRole.CANDIDATE;
        this.nodeId = UUID.randomUUID().toString();
        this.peerIds = new ArrayList<>();
        this.leaderPort = 8080;
        this.httpPort = 8080;
        this.wsPort = 8081;
        this.udpPort = 9999;
    }

    public static NodeState getInstance() {
        if (instance == null) {
            synchronized (NodeState.class) {
                if (instance == null) {
                    instance = new NodeState();
                }
            }
        }
        return instance;
    }

    /**
     * 重置单例（用于测试）
     */
    public static void resetInstance() {
        instance = null;
    }

    // === Getters and Setters ===

    public NodeRole getCurrentRole() {
        return currentRole;
    }

    public void setCurrentRole(NodeRole currentRole) {
        this.currentRole = currentRole;
    }

    public String getLeaderIp() {
        return leaderIp;
    }

    public void setLeaderIp(String leaderIp) {
        this.leaderIp = leaderIp;
    }

    public int getLeaderPort() {
        return leaderPort;
    }

    public void setLeaderPort(int leaderPort) {
        this.leaderPort = leaderPort;
    }

    public String getSelfIp() {
        return selfIp;
    }

    public void setSelfIp(String selfIp) {
        this.selfIp = selfIp;
    }

    public String getNodeId() {
        return nodeId;
    }

    public List<String> getPeerIds() {
        return peerIds;
    }

    public void addPeerId(String peerId) {
        if (!peerIds.contains(peerId)) {
            peerIds.add(peerId);
        }
    }

    public void removePeerId(String peerId) {
        peerIds.remove(peerId);
    }

    public int getHttpPort() {
        return httpPort;
    }

    public void setHttpPort(int httpPort) {
        this.httpPort = httpPort;
    }

    public int getWsPort() {
        return wsPort;
    }

    public void setWsPort(int wsPort) {
        this.wsPort = wsPort;
    }

    public int getUdpPort() {
        return udpPort;
    }

    // === Convenience methods ===

    public boolean isLeader() {
        return currentRole == NodeRole.LEADER;
    }

    public boolean isFollower() {
        return currentRole == NodeRole.FOLLOWER;
    }

    public boolean isCandidate() {
        return currentRole == NodeRole.CANDIDATE;
    }

    /**
     * 兼容旧代码 - 使用 setCurrentRole
     */
    public void setRole(NodeRole role) {
        this.currentRole = role;
    }

    /**
     * 获取当前角色（旧接口兼容）
     */
    public NodeRole getRole() {
        return currentRole;
    }

    // === 在线用户管理 (Leader 端) ===

    public void addOnlineUser(String userId, String ip, String nickname, String avatar) {
        onlineUsers.put(userId, new OnlineUser(userId, ip, nickname, avatar));
    }

    public void removeOnlineUser(String userId) {
        onlineUsers.remove(userId);
    }

    public void updateOnlineUser(String userId, String nickname, String avatar) {
        OnlineUser user = onlineUsers.get(userId);
        if (user != null) {
            user.nickname = nickname;
            user.avatar = avatar;
        }
    }

    public void updateOnlineUserTimestamp(String userId) {
        OnlineUser user = onlineUsers.get(userId);
        if (user != null) {
            user.lastSeen = System.currentTimeMillis();
        }
    }

    public Collection<OnlineUser> getOnlineUsers() {
        return onlineUsers.values();
    }

    public int getOnlineCount() {
        return onlineUsers.size();
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    /**
     * 在线用户信息
     */
    public static class OnlineUser {
        public String userId;
        public String ip;
        public String nickname;
        public String avatar;
        public long lastSeen;

        public OnlineUser(String userId, String ip, String nickname, String avatar) {
            this.userId = userId;
            this.ip = ip;
            this.nickname = nickname;
            this.avatar = avatar;
            this.lastSeen = System.currentTimeMillis();
        }

        public String toJson() {
            return String.format("{\"userId\":\"%s\",\"ip\":\"%s\",\"nickname\":\"%s\",\"avatar\":\"%s\"}",
                escapeJson(userId), escapeJson(ip), escapeJson(nickname), escapeJson(avatar));
        }

        private String escapeJson(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "\\r");
        }
    }
}