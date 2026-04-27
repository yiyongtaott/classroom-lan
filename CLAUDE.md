# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ClassroomLAN - 局域网文件传输与联机娱乐平台

双击 EXE 即可运行，无需安装 Java 或任何依赖。自动在局域网内竞选 Leader，其他同学的浏览器自动连接，提供文件传输 + 多人联机游戏的完整体验。

## Common Commands

### Backend (Java 17)
```bash
cd java/classroom-lan-java
mvn compile       # 编译
mvn package       # 打包 JAR
mvn exec:java     # 运行
```

### Frontend (Vue 3 + Vite)
```bash
cd vue3/classroom-lan-vue
npm install       # 安装依赖
npm run dev       # 开发模式
npm run build     # 构建
```

### Full Build (Windows)
```powershell
# 一键构建
cd build
./build-all.ps1
```

## Architecture

### Backend Structure (`java/classroom-lan-java/`)
```
io.classroomlan/
├── Main.java              # 入口：启动 UDP + HTTP + WS
├── node/
│   ├── NodeRole.java       # 枚举：CANDIDATE / LEADER / FOLLOWER
│   ├── NodeState.java      # 全局状态（当前角色、LeaderIP）
│   └── UdpDiscovery.java   # UDP 广播、心跳、选主（端口 9999）
├── server/
│   ├── HttpServer.java     # HTTP 服务（端口 8080）
│   ├── WsServer.java       # WebSocket 服务（端口 8081）
│   └── handlers/
│       ├── FileHandler.java  # 文件上传/下载 API
│       └── GameHandler.java  # 游戏 WS 消息路由
└── game/
    ├── GameRoom.java       # 游戏房间模型
    ├── DrawGuessGame.java  # 你画我猜逻辑
    ├── QuizGame.java       # 快问快答逻辑
    └── WerewolfGame.java  # 谁是卧底逻辑
```

### Node State Machine
- **CANDIDATE**: 发送 DISCOVER 广播 → 等待 LEADER_HERE 回复 → 随机退避后成为 Leader
- **LEADER**: 启动 HTTP/WS Server → 广播 LEADER_HERE → 定时发送心跳
- **FOLLOWER**: 保存 LeaderIP → 浏览器打开 Leader 页面 → 检测 Leader 存活

### Frontend Structure (`vue3/classroom-lan-vue/`)
Vue 3 前端，开发完成后构建产物需拷贝到 `backend/src/main/resources/static/`

## Key Implementation Details

### UDP Protocol (端口 9999)
```json
{"type":"DISCOVER","nodeId":"uuid-xxxx"}
{"type":"LEADER_HERE","leaderIp":"192.168.1.42","port":8080}
{"type":"HEARTBEAT","leaderIp":"192.168.1.42"}
{"type":"LEADER_DOWN"}
```

### HTTP API
- `GET /` → 静态页面
- `GET/POST /api/files` → 文件列表/上传
- `GET /api/download/{id}` → 文件下载
- `GET /api/peers` → 在线节点列表
- `GET /api/status` → 节点状态

### WebSocket (端口 8081)
- `/ws/game` → 游戏房间状态同步
- 消息协议：JOIN_ROOM / GAME_STATE / GAME_ACTION

## Development Notes

- 后端使用原生 Java 17，无第三方依赖（使用 `com.sun.net.httpserver`）
- 前端构建产物需嵌入后端 JAR 的 `static/` 目录
- 打包使用 jpackage 生成单 EXE（包含 JRE）
- 防火墙需放行：UDP 9999、TCP 8080、TCP 8081