package io.classroomlan.server.handlers;

import com.sun.net.httpserver.*;
import io.classroomlan.game.*;
import io.classroomlan.node.NodeState;

import java.io.*;
import java.util.*;

/**
 * 游戏 WS 消息路由
 */
public class GameHandler implements HttpHandler {
    private final NodeState nodeState;
    private final Map<String, GameRoom> rooms = new HashMap<>();

    public GameHandler(NodeState nodeState) {
        this.nodeState = nodeState;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // WebSocket upgrade handled by WsServer
        exchange.sendResponseHeaders(426, 0);
    }

    public Map<String, GameRoom> getRooms() {
        return rooms;
    }
}