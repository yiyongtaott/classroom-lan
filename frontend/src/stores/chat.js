import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useWebSocket } from '@/composables/useWebSocket'

// 生成/获取一个跨 Tab 共享的 clientId（localStorage 内同一浏览器实例共享）
function getClientId() {
  const key = 'classroomlan_clientId'
  let id = localStorage.getItem(key)
  if (!id) {
    id = 'client-' + Math.random().toString(36).slice(2) + Date.now().toString(36)
    localStorage.setItem(key, id)
  }
  return id
}

export const useChatStore = defineStore('chat', () => {
  const messages = ref([])
  const onlineUsers = ref([])
  const nickname = ref(localStorage.getItem('nickname') || '')
  const userId = ref('')      // stable user id assigned by server
  const clientId = ref(getClientId())

  const isConnected = ref(false)

  function setNickname(name) {
    nickname.value = name
    localStorage.setItem('nickname', name)
  }

  function addMessage(msg) {
    messages.value.push(msg)
    if (messages.value.length > 100) messages.value.shift()
  }

  function setHistory(history) {
    messages.value = history.messages || []
  }

  function setOnline(users) {
    onlineUsers.value = users
  }

  function connect() {
    const ws = useWebSocket('/ws/user')
    ws.connect()

    ws.onMessage(data => {
      if (data.type === 'welcome') {
        userId.value = data.userId
        // send init immediately (nickname already set?)
        ws.send({
          action: 'init',
          clientId: clientId.value,
          nickname: nickname.value || '匿名',
          avatar: '1'
        })
        isConnected.value = true
      } else if (data.type === 'userList') {
        onlineUsers.value = data.users || []
      } else if (data.type === 'message' || data.type === 'history') {
        // chat messages (使用旧的 action 约定)
        if (data.type === 'message') {
          addMessage({
            sender: data.sender,
            content: data.content,
            timestamp: data.timestamp,
            isSystem: data.isSystem || false
          })
        } else if (data.type === 'history') {
          setHistory({ messages: data.messages || [] })
        }
      }
    })

    return ws
  }

  function join() {
    const ws = connect()
  }

  function sendMessage(content) {
    // 使用 chat action 约定（群聊）
    const ws = useWebSocket('/ws/chat')
    ws.send({ action: 'send', content })
  }

  function sendChat(action, payload) {
    const ws = useWebSocket('/ws/chat')
    ws.send({ action, ...payload })
  }

  function ping() {
    const ws = useWebSocket('/ws/user')
    ws.send({ action: 'ping' })
  }

  return {
    messages,
    onlineUsers,
    nickname,
    userId,
    clientId,
    isConnected,
    setNickname,
    addMessage,
    setHistory,
    setOnline,
    connect,
    join,
    sendMessage,
    sendChat,
    ping
  }
})
