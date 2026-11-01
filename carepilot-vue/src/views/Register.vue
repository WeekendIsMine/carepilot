<template>
  <div class="page">
    <div class="card">
      <h2>注册</h2>
      <p class="muted">注册后即可购物并咨询售后</p>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" size="large" placeholder="4-20 位字母、数字或下划线" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" size="large" placeholder="展示名称" />
        </el-form-item>
        <el-form-item label="手机号（选填）">
          <el-input v-model="form.phone" size="large" placeholder="用于接收售后通知" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" size="large" type="password" show-password placeholder="6-32 位" />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="form.confirm" size="large" type="password" show-password @keyup.enter="submit" />
        </el-form-item>
        <el-button type="primary" size="large" class="submit" :loading="loading" @click="submit">注册并登录</el-button>
      </el-form>
      <p class="switch">已有账号？<router-link to="/login" class="link">登录</router-link></p>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const form = reactive({ username: '', nickname: '', phone: '', password: '', confirm: '' })

function validate() {
  if (!/^[A-Za-z0-9_]{4,20}$/.test(form.username)) return '用户名需为 4-20 位字母、数字或下划线'
  if (!form.nickname.trim()) return '请填写昵称'
  if (form.password.length < 6 || form.password.length > 32) return '密码长度需为 6-32 位'
  if (form.password !== form.confirm) return '两次输入的密码不一致'
  if (form.phone && !/^1[3-9]\d{9}$/.test(form.phone)) return '手机号格式不正确'
  return ''
}

async function submit() {
  const err = validate()
  if (err) return ElMessage.warning(err)
  loading.value = true
  try {
    await auth.register({
      username: form.username,
      nickname: form.nickname,
      phone: form.phone || '',
      password: form.password,
    })
    ElMessage.success('注册成功')
    router.push('/shop')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.page { height: 100%; display: grid; place-items: center; background: var(--bg); }
.card { width: 380px; background: var(--surface); padding: 32px; border-radius: var(--radius-lg); box-shadow: var(--shadow-lg); }
h2 { margin: 0 0 4px; font-size: 20px; }
.muted { font-size: 12px; margin: 0 0 18px; }
.submit { width: 100%; }
.switch { margin: 16px 0 0; text-align: center; font-size: 13px; color: var(--text-3); }
.link { color: var(--accent); text-decoration: none; }
.link:hover { text-decoration: underline; }
</style>