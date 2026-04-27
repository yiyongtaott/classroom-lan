import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/Home.vue'),
    meta: { title: '首页' }
  },
  {
    path: '/transfer',
    name: 'FileTransfer',
    component: () => import('@/views/FileTransfer.vue'),
    meta: { title: '文件传输' }
  },
  {
    path: '/games',
    name: 'GameLobby',
    component: () => import('@/views/GameLobby.vue'),
    meta: { title: '游戏大厅' }
  },
  {
    path: '/games/draw',
    name: 'DrawGuess',
    component: () => import('@/views/games/DrawGuess.vue'),
    meta: { title: '你画我猜' }
  },
  {
    path: '/games/quiz',
    name: 'Quiz',
    component: () => import('@/views/games/Quiz.vue'),
    meta: { title: '快问快答' }
  },
  {
    path: '/games/werewolf',
    name: 'Werewolf',
    component: () => import('@/views/games/Werewolf.vue'),
    meta: { title: '谁是卧底' }
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to) => {
  document.title = `ClassroomLAN - ${to.meta.title || to.name}`
})

export default router
