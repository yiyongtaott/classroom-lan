<template>
  <div class="werewolf">
    <div class="header">
      <button @click="goBack" class="back-btn">⬅️ 返回</button>
      <div class="phase-badge">{{ phaseLabel }}</div>
      <div class="timer" v-if="timeLeft > 0">⏱️ {{ timeLeft }}s</div>
    </div>

    <div v-if="!gameStarted" class="lobby">
      <h3>🐺 谁是卧底</h3>
      <p>玩家 ({{ lobbyPlayers.length }}/12):</p>
      <div class="player-tags">
        <div v-for="p in lobbyPlayers" :key="p" class="tag">{{ p }}</div>
      </div>
      <div v-if="isHost">
        <button @click="startGame" class="start-btn" :disabled="lobbyPlayers.length < 4">
          🚀 开始游戏 (需至少4人)
        </button>
      </div>
      <div v-else class="waiting">等待主机开始游戏...</div>
    </div>

    <div v-else class="game-area">
      <div v-if="phase === 'ROLE_REVEAL' && myRole" class="role-reveal">
        <div class="role-card" :class="myRole.toLowerCase()">
          <h2>{{ roleLabel(myRole) }}</h2>
          <p v-if="myWord">你的词: <strong>{{ myWord }}</strong></p>
          <button @click="confirmRole" class="confirm-btn">✅ 我已记住</button>
        </div>
      </div>

      <div v-else-if="phase === 'DESCRIPTION'" class="description-phase">
        <h4>💬 描述你的词 (30秒)</h4>
        <textarea
          v-model="myDescription"
          @input="sendDescription"
          placeholder="输入对你的词的描述..."
          class="desc-input"
        ></textarea>
      </div>

      <div v-else-if="phase === 'VOTING'" class="voting-phase">
        <h4>🗳️ 投票淘汰</h4>
        <div class="vote-options">
          <div v-for="player in alivePlayers" :key="player"
               class="vote-card"
               @click="voteFor(player)"
               :class="{ selected: votedFor === player }">
            {{ player }}
          </div>
        </div>
        <button v-if="votedFor" @click="submitVote" class="submit-vote-btn">确认投票</button>
      </div>

      <div v-else-if="phase === 'RESULT'" class="result-phase">
        <div class="eliminated">
          被淘汰: <strong>{{ eliminatedPlayer }}</strong> ({{ eliminatedRole }})
        </div>
        <div class="survivors">
          剩余: {{ alivePlayers.join(', ') }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useWebSocket } from '@/composables/useWebSocket'
import { useChatStore } from '@/stores/chat'
import { useGameStore } from '@/stores/game'

const router = useRouter()
const chatStore = useChatStore()
const gameStore = useGameStore()
const ws = useWebSocket('/ws/game')

const gameStarted = ref(false)
const phase = ref('LOBBY')
const timeLeft = ref(0)
const timerInterval = ref(null)
const lobbyPlayers = ref([])
const alivePlayers = ref([])
const myRole = ref(null)
const myWord = ref('')
const myDescription = ref('')
const votedFor = ref(null)
const eliminatedPlayer = ref('')
const eliminatedRole = ref('')

const isHost = computed(() => lobbyPlayers.value[0] === chatStore.nickname)

const phaseLabel = computed(() => {
  const labels = {
    LOBBY: '大厅',
    ROLE_REVEAL: '角色揭示',
    DESCRIPTION: '描述阶段',
    VOTING: '投票阶段',
    RESULT: '结算',
    ENDED: '游戏结束'
  }
  return labels[phase.value] || phase.value
})

const roleLabel = (role) => role === 'CIVILIAN' ? '👥 平民' : '🕵️ 卧底'

function goBack() { router.push('/games') }

function handleMessage(data) {
  switch (data.event) {
    case 'ROOM_STATE':
      gameStarted.value = true
      lobbyPlayers.value = data.players
      break
    case 'PLAYER_JOINED':
      if (gameStarted.value) alivePlayers.value.push(data.playerName)
      else lobbyPlayers.value.push(data.playerName)
      break
    case 'PLAYER_LEFT':
      alivePlayers.value = alivePlayers.value.filter(p => p !== data.playerName)
      break
    case 'GAME_STATE':
      if (data.data) {
        if (data.data.phase) phase.value = data.data.phase
        if (data.data.players) alivePlayers.value = data.data.players
        if (data.data.role) myRole.value = data.data.role
        if (data.data.word) myWord.value = data.data.word
        if (data.data.eliminated) {
          eliminatedPlayer.value = data.data.eliminated.name
          eliminatedRole.value = data.data.eliminated.role
        }
      }
      break
  }
}

function startGame() {
  ws.send({
    action: 'GAME_ACTION',
    roomId: gameStore.currentRoomId,
    payload: { type: 'START_WEREWOLF', players: lobbyPlayers.value }
  })
}

function confirmRole() {
  ws.send({
    action: 'GAME_ACTION',
    roomId: gameStore.currentRoomId,
    payload: { type: 'ROLE_CONFIRMED' }
  })
}

function sendDescription() {
  ws.send({
    action: 'GAME_ACTION',
    roomId: gameStore.currentRoomId,
    payload: { type: 'DESCRIPTION', text: myDescription.value }
  })
}

function voteFor(player) {
  if (phase.value !== 'VOTING') return
  votedFor.value = player
}

function submitVote() {
  if (!votedFor.value) return
  ws.send({
    action: 'GAME_ACTION',
    roomId: gameStore.currentRoomId,
    payload: { type: 'VOTE', target: votedFor.value }
  })
  votedFor.value = null
}

onMounted(() => {
  ws.connect()
  ws.onMessage(handleMessage)
  if (!chatStore.nickname) chatStore.setNickname('玩家' + Math.floor(Math.random() * 1000))
})

onUnmounted(() => {
  ws.disconnect()
  if (timerInterval.value) clearInterval(timerInterval.value)
})
</script>

<style scoped>
.werewolf { max-width: 700px; margin: 0 auto; }
.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.back-btn { background: none; border: none; font-size: 20px; cursor: pointer; }
.phase-badge {
  padding: 6px 12px; background: #667eea; color: white;
  border-radius: 20px; font-weight: bold;
}

.lobby { text-align: center; padding: 40px; background: white; border-radius: 16px; }
.player-tags { display: flex; flex-wrap: wrap; justify-content: center; gap: 8px; margin: 16px 0; }
.tag { padding: 6px 12px; background: #edf2f7; border-radius: 12px; }
.start-btn {
  margin-top: 16px; padding: 12px 24px; background: #48bb78;
  color: white; border: none; border-radius: 8px; font-size: 16px; cursor: pointer;
}
.start-btn:disabled { background: #cbd5e0; cursor: not-allowed; }
.waiting { color: #718096; margin-top: 20px; }

.role-card {
  text-align: center; padding: 40px; border-radius: 16px;
}
.role-card.civilian { background: #c6f6d5; color: #22543d; }
.role-card.spy { background: #fed7d7; color: #742a2a; }
.confirm-btn {
  margin-top: 20px; padding: 10px 20px;
  background: #667eea; color: white; border: none;
  border-radius: 8px; font-size: 16px; cursor: pointer;
}

.desc-input {
  width: 100%; height: 100px; padding: 12px; border-radius: 8px;
  border: 2px solid #e2e8f0; font-size: 16px; resize: none;
}

.voting-phase { text-align: center; }
.vote-options { display: flex; flex-wrap: wrap; justify-content: center; gap: 12px; margin: 20px 0; }
.vote-card {
  padding: 12px 20px; background: #edf2f7;
  border-radius: 8px; cursor: pointer; transition: all 0.2s;
}
.vote-card.selected { background: #667eea; color: white; }
.submit-vote-btn {
  padding: 10px 20px; background: #e53e3e; color: white;
  border: none; border-radius: 8px; cursor: pointer;
}

.result-phase { text-align: center; padding: 40px; background: white; border-radius: 16px; }
.eliminated { font-size: 18px; margin-bottom: 12px; }
.survivors { color: #718096; }
</style>
