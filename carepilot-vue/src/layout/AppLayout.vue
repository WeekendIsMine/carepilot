<template>
  <div class="shell">
    <header class="topbar">
      <div class="brand">
        <div class="dot" />
        <span class="name">CarePilot</span>
        <span class="tag">售后 Agent</span>
      </div>
      <nav class="nav">
        <router-link to="/shop" class="nav-item" active-class="on">商城</router-link>
        <router-link to="/orders" class="nav-item" active-class="on">我的订单</router-link>
        <router-link to="/wallet" class="nav-item" active-class="on">我的钱包</router-link>
        <router-link to="/chat" class="nav-item" active-class="on">客服对话</router-link>
        <router-link v-if="auth.isStaff" to="/workbench" class="nav-item" active-class="on">工作台</router-link>
        <router-link v-if="auth.isAdmin" to="/admin" class="nav-item" active-class="on">管理</router-link>
      </nav>
      <div class="me">
        <span class="who">{{ auth.user?.nickname || auth.user?.username }}</span>
        <span class="role">{{ roleLabel }}</span>
        <el-button link size="small" @click="logout">退出</el-button>
      </div>
    </header>
    <main class="main"><router-view /></main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const roleLabel = computed(() => ({ USER: '顾客', AGENT: '客服', ADMIN: '管理员' }[auth.role] || ''))
function logout() {
  auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.shell { display: flex; flex-direction: column; height: 100%; }
.topbar {
  height: 56px; flex: 0 0 56px; display: flex; align-items: center; gap: 28px;
  padding: 0 20px; background: var(--surface); border-bottom: 1px solid var(--line);
}
.brand { display: flex; align-items: center; gap: 8px; }
.dot { width: 10px; height: 10px; border-radius: 3px; background: var(--accent); }
.name { font-weight: 700; letter-spacing: -0.2px; font-size: 15px; }
.tag { font-size: 11px; color: var(--text-3); background: var(--surface-2); padding: 2px 8px; border-radius: 20px; }
.nav { display: flex; gap: 4px; flex: 1; }
.nav-item {
  padding: 6px 12px; border-radius: 8px; color: var(--text-2); text-decoration: none; font-size: 13px;
}
.nav-item.on { background: var(--accent-soft); color: var(--accent); font-weight: 600; }
.me { display: flex; align-items: center; gap: 10px; font-size: 13px; }
.who { font-weight: 600; }
.role { color: var(--text-3); font-size: 12px; }
.main { flex: 1; min-height: 0; }
</style>