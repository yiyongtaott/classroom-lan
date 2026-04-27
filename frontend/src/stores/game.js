import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useWebSocket } from '@/composables/useWebSocket'

export const useGameStore = defineStore('game', () => {
  const currentRoomId = ref(null)
  const players = ref([])
  const gameState = ref({})
  const gameEvents = ref([])

  const isInRoom = computed(() => currentRoomId.value !== null)

  async function joinRoom(roomId, playerName) {
    const ws = useWebSocket('/ws/game')
    await ws.connect()
    ws.send(JSON.stringify({
      action: 'JOIN_ROOM',
      roomId,
      playerName
    }))

    currentRoomId.value = roomId
    return true
  }

  function leaveRoom() {
    const ws = useWebSocket('/ws/game')
    if (currentRoomId.value) {
      ws.send(JSON.stringify({
        action: 'LEAVE_ROOM',
        roomId: currentRoomId.value
      }))
    }
    currentRoomId.value = null
    players.value = []
    gameState.value = {}
  }

  function sendGameAction(payload) {
    if (!currentRoomId.value) return
    const ws = useWebSocket('/ws/game')
    ws.send(JSON.stringify({
      action: 'GAME_ACTION',
      roomId: currentRoomId.value,
      payload
    }))
  }

  function handleRoomState(event) {
    players.value = event.players || []
    gameState.value = { ...event }
  }

  function handleGameState(event) {
    gameState.value = { ...event.data }
  }

  return {
    currentRoomId,
    players,
    gameState,
    gameEvents,
    isInRoom,
    joinRoom,
    leaveRoom,
    sendGameAction,
    handleRoomState,
    handleGameState
  }
})
