import { defineStore } from 'pinia'

export const useUserStore = defineStore('user', () => {
  // Stub — functionality now in chatStore + WebSocket
  const currentUser = ref(null)

  function setUser(user) {
    currentUser.value = user
  }

  return { currentUser, setUser }
})
