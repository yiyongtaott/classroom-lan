import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useNodeStore = defineStore('node', () => {
  const role = ref('UNKNOWN')
  const selfIp = ref('')
  const leaderIp = ref('')
  const peerCount = ref(0)

  const isLeader = computed(() => role.value === 'LEADER')
  const isFollower = computed(() => role.value === 'FOLLOWER')

  async function fetchNodeStatus() {
    try {
      const res = await fetch('/api/status')
      const data = await res.json()
      role.value = data.role
      selfIp.value = data.selfIp || ''
      peerCount.value = data.peers || 0
    } catch (e) {
      console.error('Failed to fetch node status:', e)
    }
  }

  async function fetchPeers() {
    try {
      const res = await fetch('/api/peers')
      const data = await res.json()
      peerCount.value = data.length
    } catch (e) {
      console.error('Failed to fetch peers:', e)
    }
  }

  return {
    role,
    selfIp,
    leaderIp,
    peerCount,
    isLeader,
    isFollower,
    fetchNodeStatus,
    fetchPeers
  }
})
