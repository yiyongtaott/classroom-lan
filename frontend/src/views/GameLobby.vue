<template>
  <div class="game-lobby">
    <h2>🎮 游戏大厅</h2>

    <section class="room-section">
      <h3>我的房间</h3>
      <div v-if="currentRoom" class="current-room">
        <div class="room-header">
          <span class="room-id">房间: {{ currentRoom.id }}</span>
          <span class="room-type">{{ gameTypeLabel(currentRoom.type) }}</span>
        </div>
        <div class="player-list">
          <div v-for="player in currentRoom.players" :key="player" class="player-tag">
            👤 {{ player }}
          </div>
        </div>
        <div class="room-actions">
          <router-link :to="`/games/${roomGamePath(currentRoom.type)}`" class="enter-btn">
            🚀 进入游戏
          </router-link>
          <button @click="leaveRoom" class="leave-btn">⬅️ 离开房间</button>
        </div>
      </div>
      <div v-else class="no-room">
        <button @click="createRoom('draw')" class="create-btn">🎨 创建「你画我猜」房间</button>
        <button @click="createRoom('quiz')" class="create-btn">❓ 创建「快问快答」房间</button>
        <button @click="createRoom('werewolf')" class="create-btn">🐺 创建「谁是卧底」房间</button>
      </div>
    </section>

    <section class="rooms-section" v-if="rooms.length">
      <h3>📋 其他房间</h3>
      <div v-for="room in rooms" :key="room.id" class="room-card">
        <div class="room-info">
          <span class="room-id">#{{ room.id }}</span>
          <span class="room-type">{{ gameTypeLabel(room.type) }}</span>
          <span class="room-players">{{ room.players.length }} 人在线</span>
        </div>
        <button @click="joinRoom(room.id)" class="join-btn" :disabled="room.players.length >= roomMaxPlayers(room.type)">
          {{ room.players.length >= roomMaxPlayers(room.type) ? '已满' : '加入' }}
        </button>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useWebSocket } from '@/composables/useWebSocket'
import { useChatStore } from '@/stores/chat'
import router from '@/router'

const rooms = ref([])
const currentRoom = ref(null)
const chatStore = useChatStore()
let ws = null
let unsubscribe = null

const roomMaxPlayers = (type) => {
  switch (type) {
    case 'draw': return 8
    case 'quiz': return 20
    case 'werewolf': return 12
    default: return 10
  }
}

const gameTypeLabel = (type) => {
  const labels = { draw: '你画我猜', quiz: '快问快答', werewolf: '谁是卧底' }
  return labels[type] || type
}

const roomGamePath = (type) => {
  return type // routes use exactly these: draw, quiz, werewolf
}

function createRoom(type) {
  const roomId = 'room_' + Date.now().toString(36).slice(-6)
  joinRoom(roomId, type)
}

async function joinRoom(roomId, type = 'draw') {
  const playerName = chatStore.nickname || ('玩家' + Math.floor(Math.random() * 1000))
  ws = useWebSocket('/ws/game')
  await ws.connect()

  ws.send({
    action: 'JOIN_ROOM',
    roomId,
    playerName
  })

  currentRoom.value = {
    id: roomId,
    type,
    players: [playerName]
  }
}

function leaveRoom() {
  if (!ws || !currentRoom.value) return
  ws.send({
    action: 'LEAVE_ROOM',
    roomId: currentRoom.value.id
  })
  currentRoom.value = null
}

function setupWS() {
  ws = useWebSocket('/ws/game')
  ws.connect()

  unsubscribe = ws.onMessage((data) => {
    switch (data.event) {
      case 'ROOM_STATE':
        currentRoom.value = {
          id: data.roomId,
          type: 'draw', // todo: store gameType in room state
          players: data.players || []
        }
        break
      case 'PLAYER_JOINED':
        if (currentRoom.value && currentRoom.value.id === data.roomId) {
          currentRoom.value.players.push(data.playerName)
        }
        break
      case 'PLAYER_LEFT':
        if (currentRoom.value && currentRoom.value.id === data.roomId) {
          currentRoom.value.players = currentRoom.value.players.filter(p => p !== data.playerName)
        }
        break
      case 'ERROR':
        alert(data.message)
        break
    }
  })
}

onMounted(() => {
  setupWS()
  chatStore.join()
  if (!chatStore.nickname) {
    const name = prompt('请输入你的昵称：') || '匿名'
    chatStore.setNickname(name)
  }
})

onUnmounted(() => {
  if (unsubscribe) unsubscribe()
  if (ws) ws.disconnect()
})
</script>

<style scoped>
.game-lobby { max-width: 700px; margin: 0 auto; }
h2 { font-size: 22px; margin-bottom: 20px; color: #2d3748; }
h3 { font-size: 16px; color: #4a5568; margin: 16px 0 12px; }

.create-btn, .join-btn, .enter-btn {
  padding: 10px 16px; border: none; border-radius: 6px; cursor: pointer;
  font-size: 14px; margin: 4px;
}
.create-btn { background: #667eea; color: white; flex: 1; }
.join-btn { background: #48bb78; color: white; }
.join-btn:disabled { background: #ccc; cursor: not-allowed; }
.enter-btn { background: #ed8936; color: white; }
.leave-btn { background: #e53e3e; color: white; }

.no-room { display: flex; flex-direction: column; gap: 8px; }
.no-room .create-btn { display: block; }

.current-room, .room-card {
  background: white; border-radius: 12px; padding: 16px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
}
.room-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 12px;
}
.room-id { font-weight: bold; }
.room-type {
  font-size: 12px; padding: 2px 8px; border-radius: 4px;
  background: #667eea; color: white;
}
.player-list {
  display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px;
}
.player-tag {
  padding: 4px 10px; background: #edf2f7;
  border-radius: 12px; font-size: 13px;
}
.room-actions { display: flex; gap: 8px; }

.rooms-section { margin-top: 24px; }
.room-info {
  display: flex; align-items: center; gap: 12px;
  margin-bottom: 8px;
}
.room-players { margin-left: auto; font-size: 13px; color: #718096; }
</style>
