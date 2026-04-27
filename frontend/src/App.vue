<template>
  <div id="app-container">
    <header class="app-header">
      <button class="menu-btn" @click="toggleSidebar">☰</button>
      <h1>ClassroomLAN</h1>
      <div class="status-badge" :class="nodeRole.toLowerCase()">
        {{ nodeRoleLabel }}
      </div>
    </header>

    <aside class="sidebar" :class="{ open: sidebarOpen }" @click="closeSidebar">
      <nav>
        <router-link to="/">🏠 首页</router-link>
        <router-link to="/transfer">📁 文件传输</router-link>
        <router-link to="/games">🎮 游戏大厅</router-link>
      </nav>
      <div class="sidebar-info">
        <div v-if="leaderIp">Leader: {{ leaderIp }}</div>
        <div>在线节点: {{ peerCount }}</div>
      </div>
    </aside>

    <main class="main-content" @click="closeSidebar">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useNodeStore } from '@/stores/node'
import { useChatStore } from '@/stores/chat'

const nodeStore = useNodeStore()
const chatStore = useChatStore()

const sidebarOpen = ref(false)
const toggleSidebar = () => { sidebarOpen.value = !sidebarOpen.value }
const closeSidebar = () => { sidebarOpen.value = false }

const nodeRole = computed(() => nodeStore.role || 'UNKNOWN')

const nodeRoleLabel = computed(() => {
  switch (nodeStore.role) {
    case 'LEADER': return '👑 Leader'
    case 'FOLLOWER': return '👤 Follower'
    case 'CANDIDATE': return '⚡ Candidate'
    default: return nodeStore.role || 'Unknown'
  }
})

const peerCount = computed(() => nodeStore.peerCount)
const leaderIp = computed(() => nodeStore.leaderIp)

onMounted(() => {
  chatStore.join()  // connect to /ws/chat
  nodeStore.fetchNodeStatus()
})
</script>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  background: #f5f7fa;
  color: #333;
  height: 100vh;
  overflow: hidden;
}
#app-container { display: flex; flex-direction: column; height: 100%; }

.app-header {
  height: 60px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white; display: flex; align-items: center; padding: 0 16px;
  gap: 12px; box-shadow: 0 2px 8px rgba(0,0,0,0.15);
}
.menu-btn { font-size: 24px; background: none; border: none; color: white; cursor: pointer; }
.app-header h1 { flex: 1; font-size: 20px; font-weight: 600; }
.status-badge {
  padding: 4px 12px; border-radius: 12px; font-size: 12px; font-weight: bold;
}
.status-badge.leader { background: #f6e05e; color: #744210; }
.status-badge.follower { background: #68d391; color: #22543d; }
.status-badge.candidate { background: #fbd38d; color: #744210; }

.sidebar {
  position: fixed; left: -200px; top: 60px; bottom: 0; width: 200px;
  background: #2d3748; color: #e2e8f0; padding: 16px;
  transition: left 0.3s ease; z-index: 100;
}
.sidebar.open { left: 0; }
.sidebar nav { display: flex; flex-direction: column; gap: 8px; }
.sidebar a {
  padding: 12px; color: #e2e8f0; text-decoration: none;
  border-radius: 8px; transition: background 0.2s;
}
.sidebar a:hover { background: #4a5568; }
.sidebar a.router-link-active { background: #667eea; color: white; }
.sidebar-info {
  position: absolute; bottom: 16px; left: 16px; right: 16px;
  font-size: 12px; color: #a0aec0; line-height: 1.6;
}

.main-content {
  flex: 1; margin-top: 60px; padding: 20px;
  overflow-y: auto;
}

@media (min-width: 768px) {
  .sidebar { left: 0 !important; }
  .menu-btn { display: none; }
  .main-content { margin-left: 200px; }
}
</style>
