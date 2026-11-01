<template>
  <div class="desk">
    <aside class="side">
      <div class="tabs">
        <button :class="{ on: tab === 'waiting' }" @click="tab = 'waiting'">排队 {{ waiting.length }}</button>
        <button :class="{ on: tab === 'mine' }" @click="tab = 'mine'">接待 {{ mine.length }}</button>
      </div>
      <div class="list">
        <div v-for="s in currentList" :key="s.id" class="item" :class="{ on: s.id === currentId }" @click="open(s)">
          <div class="row1"><span class="mono">{{ s.sessionNo }}</span>
            <el-tag size="small" :type="s.priority >= 80 ? 'danger' : s.priority >= 50 ? 'warning' : 'info'">
              {{ s.transferReason || '等待' }}
            </el-tag>
          </div>
          <div class="row2">{{ s.lastMessage || '—' }}</div>
        </div>
        <el-empty v-if="!currentList.length" description="暂无会话" :image-size="50" />
      </div>
      <div class="foot"><span class="dot" :class="online ? 'ok' : 'off'" />{{ online ? '在线接待中' : '离线' }}</div>
    </aside>

    <section class="main">
      <header class="head">
        <div>{{ current?.sessionNo || '选择一个会话' }}</div>
        <div style="display: flex; gap: 8px">
          <el-button v-if="current && current.status === 'WAITING'" type="primary" size="small" @click="accept">接管</el-button>
          <el-button v-if="current" size="small" @click="closeSession">结束会话</el-button>
        </div>
      </header>
      <ChatStream :items="items" />
      <footer class="composer" v-if="current">
        <el-input v-model="reply" type="textarea" :rows="2" resize="none" placeholder="以客服身份回复…" @keydown.enter.exact.prevent="sendReply" />
        <div class="composer-foot"><span class="hint muted">Enter 发送</span><el-button type="primary" @click="sendReply">回复</el-button></div>
      </footer>
    </section>

    <aside class="ctx">
      <div class="block">
        <h4>交接摘要</h4>
        <p class="summary">{{ summary || '接管会话后自动生成' }}</p>
      </div>
      <div class="block">
        <h4>提示</h4>
        <ul class="muted small">
          <li>接管时会自动生成摘要，不用从零读聊天记录</li>
          <li>AI 在你服务期间不会再插话</li>
          <li>工单处理去「管理 → 工单」</li>
        </ul>
      </div>
    </aside>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { workbenchApi, ensureFreshToken } from '../api'
import { useAuthStore } from '../stores/auth'
import { ChatSocket } from '../utils/ws'
import ChatStream from '../components/ChatStream.vue'

const auth = useAuthStore()
const tab = ref('waiting')
const waiting = ref([])
const mine = ref([])
const current = ref(null)
const currentId = ref(null)
const items = ref([])
const summary = ref('')
const reply = ref('')
const online = ref(false)
let socket = null
let timer = null

const currentList = computed(() => (tab.value === 'waiting' ? waiting.value : mine.value))

async function refresh() {
  const [w, m] = await Promise.all([workbenchApi.waiting(), workbenchApi.mine()])
  waiting.value = w
  mine.value = m
}

async function open(s) {
  current.value = s
  currentId.value = s.id
  const list = await workbenchApi.messages(s.id)
  items.value = list.map((m) => ({
    kind: m.role === 'SYSTEM' ? 'sys' : 'msg',
    role: m.role,
    content: m.content,
  }))
  summary.value = ''
  try {
    const r = await workbenchApi.summary(s.id)
    if (r?.raw) summary.value = JSON.parse(r.raw).summary || ''
  } catch (e) {
    // 没摘要就不显示
  }
}

async function accept() {
  summary.value = '正在生成交接摘要…'
  let r
  try {
    r = await workbenchApi.accept(currentId.value)
  } catch (e) {
    summary.value = ''
    ElMessage.warning(e?.response?.data?.message || '这个会话已经被取消或接管了')
    await refresh()
    return
  }
  current.value = { ...current.value, status: 'AGENT' }
  if (r.summary) summary.value = r.summary
  ElMessage.success('已接管')
  // 列表刷新放到后面，不挡接管反馈
  refresh()
}

async function closeSession() {
  await workbenchApi.close(currentId.value)
  ElMessage.success('会话已结束')
  current.value = null
  items.value = []
  await refresh()
}

async function sendReply() {
  if (!reply.value.trim()) return
  const content = reply.value.trim()
  await workbenchApi.reply(currentId.value, content)
  items.value.push({ kind: 'msg', role: 'AGENT', content })
  reply.value = ''
}

let refreshLock = false
async function safeRefresh() {
  if (refreshLock) return
  refreshLock = true
  try {
    await refresh()
  } finally {
    setTimeout(() => { refreshLock = false }, 300)
  }
}

function onFrame(f) {
  if (f.type === 'QUEUE_CHANGED') {
    safeRefresh()
  }
  if (f.type === 'AGENT_LEFT') {
    if (f.sessionId === currentId.value) {
      current.value = null
      currentId.value = null
      items.value = []
      summary.value = ''
      ElMessage.info('用户已结束人工服务')
    }
    safeRefresh()
  }
  if (f.type === 'SESSION_CLOSED') {
    // 用户主动结束 / 坐席结束：清掉当前会话，避免继续往已结束的会话里发消息
    if (f.sessionId === currentId.value || !f.sessionId) {
      current.value = null
      currentId.value = null
      items.value = []
      summary.value = ''
      ElMessage.info(f.by === 'user' ? '用户已结束本次会话' : '会话已结束')
    }
    safeRefresh()
  }
  if (f.type === 'SESSION_SUMMARY' && f.sessionId === currentId.value) {
    summary.value = f.summary || ''
  }
  if (f.type === 'AGENT_MESSAGE' && f.from === 'USER' && f.sessionId === currentId.value) {
    items.value.push({ kind: 'msg', role: 'USER', content: f.content })
  }
  if (f.type === 'READY') online.value = true
}

onMounted(async () => {
  await refresh()
  socket = new ChatSocket(ensureFreshToken, onFrame, (st) => {
    online.value = st === 'open'
    if (st === 'open') resyncAfterReconnect()
  })
  socket.connect()
  await workbenchApi.heartbeat()
  timer = setInterval(async () => {
    await workbenchApi.heartbeat()
    await refresh()
  }, 5000)
})

/** 断线重连后补拉数据：断线期间的排队变化、客服消息都会丢 */
let deskFirstOpen = true
async function resyncAfterReconnect() {
  if (deskFirstOpen) {
    deskFirstOpen = false
    return
  }
  await refresh()
  if (currentId.value) {
    const list = await workbenchApi.messages(currentId.value)
    items.value = list.map((m) => ({
      kind: m.role === 'SYSTEM' ? 'sys' : 'msg',
      role: m.role,
      content: m.content,
    }))
  }
}

onBeforeUnmount(() => {
  socket?.close()
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.desk {
  display: grid; grid-template-columns: 280px 1fr 280px;
  height: 100%; min-height: 0; overflow: hidden;
}
.side { background: var(--surface); border-right: 1px solid var(--line); display: flex; flex-direction: column; }
.tabs { display: flex; gap: 4px; padding: 12px; }
.tabs button { flex: 1; border: none; background: var(--surface-2); padding: 7px; border-radius: 8px; cursor: pointer; font-size: 12px; color: var(--text-2); }
.tabs button.on { background: var(--accent-soft); color: var(--accent); font-weight: 600; }
.list { flex: 1; overflow-y: auto; padding: 0 10px; display: grid; grid-template-columns: minmax(0, 1fr); gap: 6px; align-content: start; }
.item { padding: 10px; border-radius: 10px; cursor: pointer; border: 1px solid transparent; min-width: 0; overflow: hidden; }
.item:hover { background: var(--surface-2); }
.item.on { background: var(--accent-soft); border-color: var(--accent-soft); }
.row1 { display: flex; justify-content: space-between; align-items: center; gap: 8px; font-size: 11px; min-width: 0; }
.row2 { font-size: 12px; color: var(--text-2); margin-top: 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.foot { padding: 12px; font-size: 12px; color: var(--text-3); display: flex; gap: 6px; align-items: center; }
.dot { width: 6px; height: 6px; border-radius: 50%; background: var(--text-3); }
.dot.ok { background: var(--ok); }
.dot.off { background: var(--danger); }
.main { display: flex; flex-direction: column; min-width: 0; min-height: 0; overflow: hidden; }
.head { height: 56px; display: flex; justify-content: space-between; align-items: center; padding: 0 20px; background: var(--surface); border-bottom: 1px solid var(--line); font-weight: 600; }
.composer { padding: 12px 20px 16px; background: var(--surface); border-top: 1px solid var(--line); }
.composer-foot { display: flex; justify-content: space-between; align-items: center; margin-top: 8px; }
.ctx { background: var(--surface); border-left: 1px solid var(--line); padding: 16px; display: grid; gap: 20px; align-content: start; }
.block h4 { margin: 0 0 8px; font-size: 12px; color: var(--text-2); }
.summary { font-size: 13px; line-height: 1.7; white-space: pre-wrap; color: var(--text); }
.small { font-size: 12px; }
.ctx ul { margin: 0; padding-left: 16px; display: grid; gap: 6px; }
</style>