<template>
  <div class="quiz">
    <div class="header">
      <button @click="goBack" class="back-btn">⬅️ 返回</button>
      <div class="timer" :class="{ urgent: timeLeft < 5 }">⏱️ {{ timeLeft }}s</div>
      <div class="score">得分: {{ score }}</div>
    </div>

    <div v-if="currentQuestion" class="question-card">
      <h3 class="question-text">{{ currentQuestion.q }}</h3>
      <div class="options">
        <button
          v-for="(opt, idx) in currentQuestion.options"
          :key="idx"
          :class="optionClass(idx)"
          @click="selectAnswer(idx)"
          :disabled="answered"
        >
          {{ ['A','B','C','D'][idx] }}. {{ opt }}
        </button>
      </div>
    </div>

    <div v-else class="loading">
      🎯 等待问题开始...
    </div>

    <div class="ranking" v-if="Object.keys(leaderboard).length">
      <h4>🏆 排行榜</h4>
      <div v-for="(sc, name) in sortedScores" :key="name" class="rank-row">
        <span>{{ name }}</span>
        <span>{{ sc }}分</span>
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

const currentQuestion = ref(null)
const selected = ref(null)
const answered = ref(false)
const timeLeft = ref(15)
const timerInterval = ref(null)
const score = ref(0)
const leaderboard = ref({})

const sortedScores = computed(() =>
  Object.entries(leaderboard.value)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 10)
)

function goBack() { router.push('/games') }

function optionClass(idx) {
  if (!answered.value) return 'option'
  if (selected.value === idx) {
    return selected.value === currentQuestion.value.answer ? 'option correct' : 'option wrong'
  }
  if (idx === currentQuestion.value.answer) return 'option correct'
  return 'option disabled'
}

function selectAnswer(idx) {
  if (answered.value) return
  selected.value = idx
  answered.value = true

  ws.send({
    action: 'GAME_ACTION',
    roomId: gameStore.currentRoomId || 'default',
    payload: { type: 'ANSWER', answer: idx }
  })
}

function handleMessage(data) {
  switch (data.event) {
    case 'GAME_STATE':
      if (data.data?.type === 'NEW_QUESTION') {
        currentQuestion.value = data.data.question
        selected.value = null; answered.value = false
        startTimer()
      } else if (data.data?.type === 'ANSWER_RESULT') {
        if (data.data.correct) score.value += data.data.points || 10
      } else if (data.data?.type === 'LEADERBOARD') {
        leaderboard.value = data.data.scores || {}
      }
  }
}

function startTimer() {
  timeLeft.value = 15
  if (timerInterval.value) clearInterval(timerInterval.value)
  timerInterval.value = setInterval(() => {
    timeLeft.value--
    if (timeLeft.value <= 0) {
      clearInterval(timerInterval.value)
      answered.value = true
    }
  }, 1000)
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
.quiz { max-width: 600px; margin: 0 auto; }
.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.back-btn { background: none; border: none; font-size: 18px; cursor: pointer; }
.timer { font-size: 20px; font-weight: bold; color: #2d3748; }
.timer.urgent { color: #e53e3e; animation: pulse 1s infinite; }
.score { font-size: 16px; font-weight: 600; }

.question-card {
  background: white; border-radius: 16px; padding: 24px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}
.question-text { font-size: 18px; margin-bottom: 20px; text-align: center; }
.options { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.option {
  padding: 16px; border: 2px solid #e2e8f0; border-radius: 12px;
  background: #f7fafc; font-size: 16px; cursor: pointer;
  transition: all 0.2s;
}
.option:hover:not(:disabled) { border-color: #667eea; background: #ebf8ff; }
.option.correct { border-color: #48bb78; background: #c6f6d5; color: #22543d; }
.option.wrong { border-color: #e53e3e; background: #fed7d7; color: #742a2a; }

.loading { text-align: center; padding: 60px; color: #718096; }

.ranking { margin-top: 24px; background: white; border-radius: 12px; padding: 16px; }
.ranking h4 { margin-bottom: 12px; }
.rank-row { display: flex; justify-content: space-between; padding: 6px 0; }

@keyframes pulse { 50% { opacity: 0.6; } }
</style>
