<template>
  <div class="draw-guess">
    <div class="header">
      <button @click="goBack" class="back-btn">⬅️ 返回</button>
      <div class="timer" :class="{ urgent: timeLeft < 10 }">⏱️ {{ timeLeft }}s</div>
    </div>

    <div class="layout">
      <aside class="sidebar">
        <div class="players">
          <h4>玩家 ({{ players.length }})</h4>
          <div v-for="p in players" :key="p" class="player" :class="{ drawer: p === drawer }">
            {{ p }} <span v-if="p === drawer">🎨</span>
          </div>
        </div>
      </aside>

      <main class="canvas-area">
        <canvas
          ref="canvas"
          width="600" height="400"
          @mousedown="startStroke"
          @mousemove="drawStroke"
          @mouseup="endStroke"
          @mouseleave="endStroke"
        ></canvas>

        <div class="tools" v-if="isDrawer">
          <input type="color" v-model="color" class="color-picker" />
          <input type="range" v-model="brushSize" min="1" max="20" class="size-slider" />
          <button @click="clearCanvas" class="tool-btn">🗑️ 清空</button>
        </div>

        <div class="guess-area">
          <input
            v-model="guessInput"
            @keyup.enter="submitGuess"
            placeholder="输入你的猜测..."
            :disabled="!isDrawer && !canGuess"
            class="guess-input"
          />
          <button @click="submitGuess" class="guess-btn" :disabled="!guessInput.trim()">
            {{ isDrawer ? '提示' : '猜测' }}
          </button>
        </div>

        <div class="hint" v-if="drawerHint">
          🎯 提示：{{ drawerHint }}
        </div>

        <div class="scoreboard">
          <h4>📊 得分</h4>
          <div v-for="(score, name) in scores" :key="name" class="score-row">
            <span>{{ name }}</span>
            <strong>{{ score }}</strong>
          </div>
        </div>
      </main>
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

const canvas = ref(null)
const ctx = ref(null)
const drawing = ref(false)
const color = ref('#000000')
const brushSize = ref(3)
const guessInput = ref('')
const drawerHint = ref('')

const players = ref([])
const scores = ref({})
const drawer = ref('')
const timeLeft = ref(80)
const timerInterval = ref(null)
const isDrawer = computed(() => drawer.value === chatStore.nickname)
const canGuess = computed(() => drawer.value && drawer.value !== chatStore.nickname)

const ws = useWebSocket('/ws/game')

onMounted(() => {
  ws.connect()
  ctx.value = canvas.value.getContext('2d')
  ctx.value.lineCap = 'round'

  ws.onMessage(handleMessage)

  // Set nickname if none
  if (!chatStore.nickname) {
    const name = prompt('请输入昵称：') || '匿名'
    chatStore.setNickname(name)
  }
})

onUnmounted(() => {
  ws.disconnect()
  if (timerInterval.value) clearInterval(timerInterval.value)
})

function goBack() { router.push('/games') }

function startStroke(e) {
  if (!isDrawer.value) return
  drawing.value = true
  const { x, y } = getCoords(e)
  ctx.value.beginPath()
  ctx.value.moveTo(x, y)
}

function drawStroke(e) {
  if (!drawing.value || !isDrawer.value) return
  const { x, y } = getCoords(e)
  ctx.value.strokeStyle = color.value
  ctx.value.lineWidth = brushSize.value
  ctx.value.lineTo(x, y)
  ctx.value.stroke()
  // Optionally send stroke to server for sync (omitted for brevity)
}

function endStroke() {
  if (drawing.value) {
    drawing.value = false
    ctx.value.closePath()
  }
}

function getCoords(e) {
  const rect = canvas.value.getBoundingClientRect()
  return { x: e.clientX - rect.left, y: e.clientY - rect.top }
}

function clearCanvas() {
  if (!isDrawer.value) return
  ctx.value.clearRect(0, 0, canvas.value.width, canvas.value.height)
}

function submitGuess() {
  if (!guessInput.value.trim()) return
  const roomId = gameStore.currentRoomId || 'room_default'

  if (isDrawer.value) {
    ws.send({
      action: 'GAME_ACTION',
      roomId,
      payload: { type: 'HINT', text: guessInput.value }
    })
    drawerHint.value = guessInput.value
    guessInput.value = ''
  } else {
    ws.send({
      action: 'GAME_ACTION',
      roomId,
      payload: { type: 'GUESS', word: guessInput.value }
    })
    guessInput.value = ''
  }
}

function handleMessage(data) {
  switch (data.event) {
    case 'ROOM_STATE':
      players.value = data.players
      // For demo: first player becomes drawer
      drawer.value = data.players[0] || ''
      if (drawer.value) startTimer()
      break

    case 'PLAYER_JOINED':
      players.value.push(data.playerName)
      break

    case 'PLAYER_LEFT':
      players.value = players.value.filter(p => p !== data.playerName)
      break

    case 'GAME_STATE':
      if (data.data?.type === 'GUESS_RESULT') {
        if (data.data.correct) {
          alert('🎉 猜对了！')
        }
      } else if (data.data?.type === 'NEW_DRAWER') {
        drawer.value = data.data.drawer
      }
      break
  }
}

function startTimer() {
  if (timerInterval.value) clearInterval(timerInterval.value)
  timeLeft.value = 80
  timerInterval.value = setInterval(() => {
    timeLeft.value--
    if (timeLeft.value <= 0) {
      clearInterval(timerInterval.value)
      // TODO: next round
    }
  }, 1000)
}
</script>

<style scoped>
.draw-guess { height: 100%; display: flex; flex-direction: column; }
.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.back-btn { background: none; border: none; font-size: 18px; cursor: pointer; }
.timer { font-size: 18px; font-weight: bold; color: #2d3748; }
.timer.urgent { color: #e53e3e; animation: pulse 1s infinite; }
@keyframes pulse { 50% { opacity: 0.5; } }

.layout { display: flex; flex: 1; gap: 16px; min-height: 0; }
.sidebar { width: 180px; background: #f7fafc; border-radius: 12px; padding: 12px; }
.players h4 { margin-bottom: 8px; font-size: 14px; color: #4a5568; }
.player { padding: 6px 0; border-bottom: 1px solid #e2e8f0; }
.player.drawer { color: #667eea; font-weight: bold; }

.canvas-area { flex: 1; display: flex; flex-direction: column; gap: 12px; }
canvas {
  border: 2px solid #e2e8f0; border-radius: 8px;
  background: white; cursor: crosshair;
}
.tools, .guess-area { display: flex; gap: 8px; align-items: center; }
.color-picker { width: 40px; height: 40px; border: none; padding: 0; }
.size-slider { width: 120px; }
.tool-btn { padding: 8px 12px; background: #e2e8f0; border: none; border-radius: 6px; cursor: pointer; }

.guess-input {
  flex: 1; padding: 10px; border: 2px solid #e2e8f0;
  border-radius: 8px; font-size: 16px;
}
.guess-btn {
  padding: 10px 16px; background: #667eea; color: white;
  border: none; border-radius: 8px; cursor: pointer;
}

.hint { padding: 8px 12px; background: #fefcbf; color: #744210;
  border-radius: 6px; text-align: center;
}

.scoreboard { background: #f7fafc; border-radius: 8px; padding: 12px; }
.scoreboard h4 { margin-bottom: 8px; font-size: 14px; }
.score-row { display: flex; justify-content: space-between; padding: 4px 0; }
</style>
