<template>
  <div class="home">
    <h2>📋 节点状态</h2>
    <div class="status-cards">
      <div class="card role-card">
        <div class="label">当前角色</div>
        <div class="value" :class="roleClass">{{ nodeStore.role || '检测中...' }}</div>
      </div>
      <div class="card">
        <div class="label">本机 IP</div>
        <div class="value">{{ nodeStore.selfIp || '获取中...' }}</div>
      </div>
      <div class="card">
        <div class="label">在线节点</div>
        <div class="value">{{ nodeStore.peerCount }}</div>
      </div>
      <div class="card">
        <div class="label">Leader 地址</div>
        <div class="value">{{ nodeStore.leaderIp || '无' }}</div>
      </div>
    </div>

    <h2>🚀 快速访问</h2>
    <div class="quick-actions">
      <router-link to="/transfer" class="action-card">
        <span class="icon">📁</span>
        <span class="title">文件传输</span>
        <span class="desc">上传 / 下载文件</span>
      </router-link>

      <router-link to="/games" class="action-card">
        <span class="icon">🎮</span>
        <span class="title">联机游戏</span>
        <span class="desc">你画我猜 / 快问快答 / 谁是卧底</span>
      </router-link>
    </div>

    <div v-if="nodeStore.isFollower && nodeStore.leaderIp" class="leader-banner">
      已连接到 Leader: {{ nodeStore.leaderIp }}
    </div>
    <div v-if="nodeStore.peerCount === 0 && nodeStore.role === 'FOLLOWER'" class="empty-banner">
      ⚠️ 暂无可用的 Leader 节点 — 建议在电脑端运行程序
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useNodeStore } from '@/stores/node'

const nodeStore = useNodeStore()

const roleClass = computed(() => {
  switch (nodeStore.role) {
    case 'LEADER': return 'leader'
    case 'FOLLOWER': return 'follower'
    case 'CANDIDATE': return 'candidate'
    default: return ''
  }
})

onMounted(() => {
  nodeStore.fetchNodeStatus()
})
</script>

<style scoped>
.home {
  max-width: 800px; margin: 0 auto;
}
h2 {
  font-size: 18px; margin: 24px 0 12px;
  color: #4a5568;
}
.status-cards {
  display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 12px;
}
.card {
  background: white; border-radius: 12px; padding: 16px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1); text-align: center;
}
.label {
  font-size: 12px; color: #718096; text-transform: uppercase; letter-spacing: 0.5px;
}
.value {
  font-size: 16px; font-weight: bold; color: #2d3748; margin-top: 6px;
}
.role-card .value.leader { color: #d69e2e; }
.role-card .value.follower { color: #38a169; }
.role-card .value.candidate { color: #ed8936; }

.quick-actions {
  display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 16px;
}
.action-card {
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  background: white; border-radius: 16px; padding: 32px;
  text-decoration: none; color: #2d3748;
  box-shadow: 0 4px 12px rgba(0,0,0,0.08);
  transition: transform 0.2s, box-shadow 0.2s;
}
.action-card:hover {
  transform: translateY(-2px); box-shadow: 0 8px 24px rgba(0,0,0,0.12);
}
.icon { font-size: 40px; margin-bottom: 12px; }
.title { font-size: 18px; font-weight: 600; }
.desc { font-size: 13px; color: #718096; margin-top: 4px; }

.leader-banner {
  margin-top: 24px; padding: 12px 16px;
  background: #c6f6d5; color: #22543d; border-radius: 8px;
  text-align: center;
}
.empty-banner {
  margin-top: 24px; padding: 12px 16px;
  background: #fed7d7; color: #742a2a; border-radius: 8px;
  text-align: center;
}
</style>
