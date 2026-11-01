import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { tokenExpired, clearSession, getRefreshToken } from '../utils/token'
import { refreshAccessToken } from '../api'

const routes = [
  { path: '/login', component: () => import('../views/Login.vue'), meta: { public: true } },
  { path: '/register', component: () => import('../views/Register.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('../layout/AppLayout.vue'),
    children: [
      { path: '', redirect: '/chat' },
      { path: 'chat', component: () => import('../views/ChatView.vue') },
      { path: 'shop', component: () => import('../views/ShopView.vue') },
      { path: 'orders', component: () => import('../views/OrdersView.vue') },
      { path: 'wallet', component: () => import('../views/WalletView.vue') },
      { path: 'workbench', component: () => import('../views/AgentDesk.vue'), meta: { roles: ['AGENT', 'ADMIN'] } },
      // 旧链接兼容
      { path: 'desk', redirect: '/workbench' },
      { path: 'admin', component: () => import('../views/AdminView.vue'), meta: { roles: ['ADMIN'] } },
    ],
  },
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach(async (to) => {
  let token = localStorage.getItem('cp_token')
  const user = JSON.parse(localStorage.getItem('cp_user') || 'null')

  if (token && tokenExpired(token)) {
    // 访问令牌过期但还有刷新令牌：静默续期，用户无感
    if (getRefreshToken()) {
      token = (await refreshAccessToken()) || ''
    }
    if (!token) {
      clearSession()
      ElMessage.warning('登录已过期，请重新登录')
    }
  }

  if (!to.meta.public && !token) {
    return { path: '/login', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } }
  }
  if (to.meta.roles && user && !to.meta.roles.includes(user.role)) {
    ElMessage.warning('当前账号无权访问该页面')
    return '/chat'
  }
  if (to.path === '/login' && token) return user?.role === 'USER' ? '/shop' : '/workbench'
  return true
})

export default router