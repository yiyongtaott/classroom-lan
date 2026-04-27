import { ref, onUnmounted } from 'vue'

const WS_BASE = window.location.origin.replace(/^http/, 'ws')
const connections = {}

export function useWebSocket(path) {
  const url = `${WS_BASE}${path}`
  const ws = ref(null)
  const messageQueue = ref([])
  const handlers = ref([])
  const ready = ref(false)

  let connectAttempts = 0

  function connect() {
    if (connections[url] && connections[url].ready) {
      ws.value = connections[url].instance
      ready.value = true
      flushQueue()
      return Promise.resolve(ws.value)
    }

    return new Promise((resolve, reject) => {
      const w = new WebSocket(url)
      ws.value = w
      connections[url] = { instance: w, ready: false }

      w.onopen = () => {
        console.log(`[WS] Connected: ${url}`)
        ready.value = true
        connections[url].ready = true
        flushQueue()
        resolve(w)
      }

      w.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data)
          for (const h of handlers.value) h(data)
        } catch (e) {
          console.error('Failed to parse WS message:', e)
        }
      }

      w.onerror = (err) => {
        console.error(`[WS] Error: ${url}`, err)
        reject(err)
      }

      w.onclose = (ev) => {
        console.log(`[WS] Closed: ${url} code=${ev.code}`)
        ready.value = false
        connections[url].ready = false
        // Attempt reconnect after 3s
        setTimeout(() => {
          if (!connections[url]?.ready && connectAttempts < 5) {
            connectAttempts++
            connect()
          }
        }, 3000)
      }
    })
  }

  function send(data) {
    if (ready.value && ws.value) {
      ws.value.send(JSON.stringify(data))
    } else {
      messageQueue.value.push(data)
      // Auto-connect if not connected
      if (!ws.value || ws.value?.readyState !== WebSocket.OPEN) {
        connect().then(() => flushQueue())
      }
    }
  }

  function onMessage(handler) {
    handlers.value.push(handler)
    return () => {
      const idx = handlers.value.indexOf(handler)
      if (idx >= 0) handlers.value.splice(idx, 1)
    }
  }

  function flushQueue() {
    for (const msg of messageQueue.value) {
      ws.value.send(JSON.stringify(msg))
    }
    messageQueue.value = []
  }

  function disconnect() {
    if (ws.value) {
      ws.value.close()
    }
  }

  onUnmounted(() => {
    disconnect()
  })

  return {
    ws,
    ready,
    connect,
    send,
    onMessage,
    disconnect
  }
}
