package io.classroomlan.game;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天室（群聊）
 * 所有在线用户自动进入公共聊天室
 */
public class ChatRoom {
    private static ChatRoom instance;

    // 消息历史（最多保存100条）
    private final List<ChatMessage> messages = new ArrayList<>();
    private final int MAX_HISTORY = 100;

    // 在线玩家
    private final Set<String> onlinePlayers = ConcurrentHashMap.newKeySet();

    private ChatRoom() {
    }

    public static ChatRoom getInstance() {
        if (instance == null) {
            instance = new ChatRoom();
        }
        return instance;
    }

    /**
     * 用户加入群聊
     */
    public void join(String playerId, String playerName) {
        onlinePlayers.add(playerName);

        // 添加系统消息
        addSystemMessage(playerName + " 加入了群聊");
    }

    /**
     * 用户离开群聊
     */
    public void leave(String playerName) {
        onlinePlayers.remove(playerName);

        // 添加系统消息
        addSystemMessage(playerName + " 离开了群聊");
    }

    /**
     * 发送消息
     */
    public ChatMessage sendMessage(String playerName, String content) {
        ChatMessage msg = new ChatMessage(playerName, content, System.currentTimeMillis());
        messages.add(msg);

        // 限制历史消息数量
        while (messages.size() > MAX_HISTORY) {
            messages.remove(0);
        }

        return msg;
    }

    /**
     * 添加系统消息
     */
    private void addSystemMessage(String content) {
        ChatMessage msg = new ChatMessage("系统", content, System.currentTimeMillis());
        msg.setSystem(true);
        messages.add(msg);

        while (messages.size() > MAX_HISTORY) {
            messages.remove(0);
        }
    }

    public List<ChatMessage> getMessages() {
        return new ArrayList<>(messages);
    }

    public Set<String> getOnlinePlayers() {
        return onlinePlayers;
    }

    public int getOnlineCount() {
        return onlinePlayers.size();
    }

    /**
     * 消息类
     */
    public static class ChatMessage {
        private final String sender;
        private final String content;
        private final long timestamp;
        private boolean isSystem = false;

        public ChatMessage(String sender, String content, long timestamp) {
            this.sender = sender;
            this.content = content;
            this.timestamp = timestamp;
        }

        public String getSender() {
            return sender;
        }

        public String getContent() {
            return content;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public boolean isSystem() {
            return isSystem;
        }

        public void setSystem(boolean system) {
            isSystem = system;
        }

        public String toJson() {
            return String.format(
                "{\"sender\":\"%s\",\"content\":\"%s\",\"timestamp\":%d,\"isSystem\":%b}",
                escapeJson(sender), escapeJson(content), timestamp, isSystem
            );
        }

        private String escapeJson(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r");
        }
    }
}