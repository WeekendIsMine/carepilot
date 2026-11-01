<template>
  <div class="page">
    <div class="card">
      <h2>登录</h2>
      <p class="muted">请使用平台账号登录</p>
      <el-form @submit.prevent="submit">
        <el-form-item><el-input v-model="username" size="large" placeholder="用户名" /></el-form-item>
        <el-form-item><el-input v-model="password" size="large" type="password" placeholder="密码" show-password @keyup.enter="submit" /></el-form-item>
        <el-button type="primary" size="large" class="submit" :loading="loading" @click="submit">进入</el-button>
      </el-form>
      <p class="switch">还没有账号？<router-link to="/register" class="link">注册</router-link></p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const username = ref('')
const password = ref('')
const loading = ref(false)

async function submit() {
  loading.value = true
  try {
    await auth.login(username.value, password.value)
    router.replace(String(route.query.redirect || (auth.role === 'USER' ? '/shop' : '/workbench')))
  } catch (e) {
    // 具体原因由请求拦截器提示（例如"用户名或密码错误"）
  } finally {
    loading.value = false
  }
}

</script>

<style scoped>
.page {
  height: 100%;
  display: grid;
  place-items: center;
  background: var(--bg);
}
.card {
  width: 360px;
  background: var(--surface);
  padding: 36px 32px 32px;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
}
h2 { margin: 0 0 4px; font-size: 20px; }
.muted { font-size: 12px; margin: 0 0 22px; }
.submit { width: 100%; margin-top: 4px; }
.switch { margin: 18px 0 0; text-align: center; font-size: 13px; color: var(--text-3); }
.link { color: var(--accent); text-decoration: none; }
.link:hover { text-decoration: underline; }
</style>