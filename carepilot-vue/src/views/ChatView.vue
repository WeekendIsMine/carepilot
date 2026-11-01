<template>
  <div class="chat">
    <!-- 会话列表 -->
    <aside class="side">
      <div class="side-head">
        <span>我的会话</span>
        <el-button size="small" type="primary" plain @click="newSession">新对话</el-button>
      </div>
      <div v-loading="sessionLoading" class="sessions">
        <div v-for="s in sessions" :key="s.id" class="session" :class="{ on: s.id === currentId }" @click="selectSession(s.id)">
          <div class="s-top">
            <span class="mono s-no">{{ s.sessionNo }}</span>
            <span class="s-right">
              <span class="dot" :class="s.status" />
              <button class="del" title="删除会话" @click.stop="removeSession(s)">×</button>
            </span>
          </div>
          <div class="s-msg">{{ s.lastMessage || '（还没开始聊）' }}</div>
        </div>
        <el-empty v-if="!sessions.length" description="还没有会话" :image-size="60" />
      </div>
    </aside>

    <!-- 对话区 -->
    <section class="main">
      <header class="main-head">
        <div class="title">
          <span>{{ sessionLabel }}</span>
          <el-tag size="small" :type="statusType">{{ statusLabel }}</el-tag>
        </div>
        <div class="head-right">
          <el-button v-if="currentSession?.status === 'AI'" size="small" @click="transfer">转人工</el-button>
          <el-button v-else-if="currentSession?.status === 'WAITING'" size="small" @click="cancelTransfer">取消转人工</el-button>
          <el-button v-else-if="currentSession?.status === 'AGENT'" size="small" type="danger" plain @click="closeSession">结束会话</el-button>
          <template v-else-if="currentSession?.status === 'CLOSED'">
            <el-button size="small" type="primary" @click="reopenSession">继续对话</el-button>
            <el-button size="small" @click="newSession">新会话</el-button>
          </template>
          <div class="conn"><span class="dot" :class="connected ? 'ok' : 'off'" />{{ connected ? '已连接' : '连接中…' }}</div>
        </div>
      </header>

      <div v-if="queue" class="banner">{{ queue }}</div>

      <ChatStream :items="items" @approve="approve" @reject="reject" />

      <footer class="composer">
        <el-input
          v-model="input"
          type="textarea"
          :rows="2"
          resize="none"
          :placeholder="isClosed ? '本次会话已结束，直接发消息会自动重新开始' : '描述你的问题，例如：上周买的耳机还没发货，能退吗？'"
          @keydown.enter.exact.prevent="send"
        />
        <div class="composer-foot">
          <span class="hint">Enter 发送 · Shift+Enter 换行</span>
          <el-button type="primary" :disabled="!input.trim() || busy" @click="send">发送</el-button>
        </div>
      </footer>
    </section>

    <!-- 客户上下文 -->
    <aside class="ctx">
      <div class="ctx-block">
        <h4>最近订单</h4>
        <div v-if="!context.orders?.length" class="empty">暂无订单</div>
        <button v-for="o in context.orders" :key="o.orderNo" class="order" @click="quoteOrder(o)">
          <div class="o-top">
            <span class="mono no">{{ o.orderNo }}</span>
            <span class="state">{{ o.statusName }}</span>
          </div>
          <div class="o-sub">{{ o.productName }} · ¥{{ o.amount }}</div>
        </button>
        <p v-if="context.orders?.length" class="hint">点击引用订单号</p>
      </div>

      <div class="ctx-block">
        <h4>售后工单</h4>
        <div v-if="!context.tickets?.length" class="empty">暂无工单</div>
        <div v-for="t in context.tickets" :key="t.ticketNo" class="ticket">
          <div class="t-top">
            <span class="mono no">{{ t.ticketNo }}</span>
            <el-tag size="small" :type="ticketTag(t.status)">{{ t.statusLabel }}</el-tag>
          </div>
          <div class="o-sub">{{ t.type }} · 订单 {{ t.orderNo }}</div>
        </div>
      </div>
    </aside>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { chatApi, ensureFreshToken } from '../api'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { ChatSocket } from '../utils/ws'
import ChatStream from '../components/ChatStream.vue'

const auth = useAuthStore()
const route = useRoute()
const sessions = ref([])
const currentId = ref(null)
const items = ref([])
const input = ref('')
const connected = ref(false)
const busy = ref(false)
const queue = ref('')
let socket = null
let streamingItem = null

const context = ref({ orders: [], tickets: [] })

const currentSession = computed(() => sessions.value.find((s) => s.id === currentId.value))
const statusLabel = computed(() => {
  const s = currentSession.value?.status
  return { AI: 'AI 服务', WAITING: '排队中', AGENT: '人工服务', CLOSED: '已结束' }[s] || 'AI 服务'
})
const statusType = computed(() => ({ AI: 'info', WAITING: 'warning', AGENT: 'success', CLOSED: 'info' }[currentSession.value?.status] || 'info'))
const isClosed = computed(() => currentSession.value?.status === 'CLOSED')
const sessionLabel = computed(() => currentSession.value?.sessionNo || '选择或新建一个会话')

function pushAssets(msg) {
  const role = msg.role
  if (role === 'SYSTEM') {
    items.value.push({ kind: 'sys', content: msg.content })
    return
  }
  items.value.push({
    kind: 'msg',
    id: msg.id,
    role,
    content: msg.content || '',
    citations: msg.citations || msg.citationsJson || [],
  })
}

const sessionLoading = ref(false)

async function loadSessions() {
  sessionLoading.value = true
  try {
    sessions.value = await chatApi.sessions()
  } finally {
    sessionLoading.value = false
  }
}

async function newSession() {
  const s = await chatApi.createSession()
  await loadSessions()
  await selectSession(s.id)
}

async function selectSession(id) {
  currentId.value = id
  queue.value = ''
  items.value = []
  const list = await chatApi.messages(id, 100)
  list.forEach(pushAssets)
  await loadContext()
  await loadPendingApprovals()
}

async function loadPendingApprovals() {
  if (!currentId.value) return
  try {
    const list = await chatApi.pendingApprovals(currentId.value)
    for (const a of list || []) {
      const exists = items.value.some((x) => x.kind === 'approval' && x.approvalNo === a.approvalNo)
      if (!exists) {
        items.value.push({ kind: 'approval', approvalNo: a.approvalNo, args: a.args, status: 'PENDING' })
      }
    }
  } catch (e) {
    // 拉不到就算了，不影响聊天
  }
}

async function loadContext() {
  if (!currentId.value) return
  try {
    context.value = await chatApi.context(currentId.value)
    const st = context.value.session?.status
    if (st === 'CLOSED') {
      queue.value = ''
      if (currentSession.value) currentSession.value.status = 'CLOSED'
    } else if (st === 'WAITING' && context.value.queuePosition) {
      queue.value = queueText(context.value.queuePosition)
    } else if (st && st !== 'WAITING') {
      queue.value = ''
    }
  } catch (e) {
    context.value = { orders: [], tickets: [] }
  }
}

function quoteOrder(o) {
  const prefix = input.value.trim()
  input.value = (prefix ? prefix + ' ' : '') + '订单 ' + o.orderNo + ' '
}

const ticketTag = (s) => ({ PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', REFUNDING: 'warning', DONE: 'success', CLOSED: 'info' }[s] || 'info')

function onFrame(f) {
  switch (f.type) {
    case 'READY':
      connected.value = true
      break
    case 'ACK':
      if (currentSession.value?.status === 'AGENT') busy.value = false
      break
    case 'TOOL_CALL': {
      if (f.status === 'running') {
        items.value.push({ kind: 'tool', tool: f.tool, status: 'running', args: f.args, hint: hintOf(f.tool), key: 't' + Date.now() })
      } else {
        const t = [...items.value].reverse().find((x) => x.kind === 'tool' && x.tool === f.tool && x.status === 'running')
        if (t) {
          t.status = 'done'
          t.costMs = f.costMs
          t.summary = (f.result || '').slice(0, 40)
        }
      }
      break
    }
    case 'AI_DELTA': {
      if (!streamingItem) {
        streamingItem = reactive({ kind: 'msg', role: 'AI', content: '', streaming: true })
        items.value.push(streamingItem)
      }
      streamingItem.content += f.delta || ''
      break
    }
    case 'AI_DONE': {
      if (streamingItem) {
        streamingItem.streaming = false
        streamingItem.id = f.messageId
        streamingItem.citations = f.citations || []
        streamingItem = null
      }
      busy.value = false
      loadSessions()
      loadContext()
      loadPendingApprovals()
      break
    }
    case 'APPROVAL':
      items.value.push({ kind: 'approval', approvalNo: f.approvalNo, args: f.args, status: 'PENDING' })
      break
    case 'APPROVAL_RESULT': {
      const a = items.value.find((x) => x.kind === 'approval' && x.approvalNo === f.approvalNo)
      if (a) a.status = f.status
      if (f.notice) items.value.push({ kind: 'sys', content: f.notice })
      break
    }
    case 'AGENT_LEFT':
      if (f.sessionId && String(f.sessionId) !== String(currentId.value)) break
      applyAgentLeft()
      break
    case 'SESSION_REOPENED':
      applyReopened()
      break
    case 'QUEUE_CANCELED':
      queue.value = ''
      setStatus('AI')
      loadSessions()
      break
    case 'QUEUE_STATUS':
      queue.value = f.position ? '已转人工，正在排队，前面还有 ' + (f.position - 1) + ' 人' : '已转人工'
      setStatus('WAITING')
      break
    case 'AGENT_JOINED':
      queue.value = ''
      busy.value = false
      if (currentSession.value) currentSession.value.status = 'AGENT'
      {
        const row = sessions.value.find((x) => x.id === currentId.value)
        if (row) row.status = 'AGENT'
      }
      items.value.push({ kind: 'sys', content: '客服 ' + (f.agentName || '') + ' 已接入' })
      break
    case 'AGENT_MESSAGE':
      items.value.push({ kind: 'msg', role: 'AGENT', content: f.content })
      break
    case 'SESSION_CLOSED': {
      // 立刻把本地状态改掉：不依赖下次轮询，否则按钮会一直停在「取消转人工」
      queue.value = ''
      busy.value = false
      if (f.sessionId && String(f.sessionId) !== String(currentId.value)) break
      applyClosed(f.by === 'agent' ? 'agent' : 'me')
      break
    }
    case 'ERROR':
      ElMessage.error(f.message || '出错了')
      busy.value = false
      break
    default:
      break
  }
}

function hintOf(tool) {
  return ({ queryOrder: '正在查询订单…', queryLogistics: '正在查询物流…', queryAfterSalePolicy: '正在检索售后政策…', submitAfterSaleRequest: '正在生成审批单…', transferToHuman: '正在转接人工…', listMyOrders: '正在翻你的订单…', listMyTickets: '正在查询工单…' })[tool] || '调用工具…'
}

function send() {
  const text = input.value.trim()
  if (!text || !currentId.value) return
  if (!socket || !connected.value) {
    ElMessage.warning('连接还没建立，稍等一下')
    return
  }
  items.value.push({ kind: 'msg', role: 'USER', content: text })
  socket.send({ type: 'SEND', sessionId: currentId.value, clientMsgId: 'c-' + Date.now(), content: text })
  input.value = ''
  // 人工服务中消息只是转给坐席，不会收到 AI_DONE，别把按钮锁死
  busy.value = currentSession.value?.status !== 'AGENT'
}

function queueText(position) {
  return position <= 1
    ? '已转接人工，正在为您接入坐席，请稍候'
    : '已转接人工，当前排在第 ' + position + ' 位，请稍候'
}

async function removeSession(s) {
  await ElMessageBox.confirm('删除后该会话与聊天记录将不再显示，确定删除？', '删除会话', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await chatApi.remove(s.id)
  sessions.value = sessions.value.filter((x) => x.id !== s.id)
  if (currentId.value === s.id) {
    if (sessions.value.length) await selectSession(sessions.value[0].id)
    else await newSession()
  }
}

async function closeSession() {
  await chatApi.close(currentId.value)
  applyClosed('me')
}

function applyClosed(by) {
  if (currentSession.value) currentSession.value.status = 'CLOSED'
  const row = sessions.value.find((x) => x.id === currentId.value)
  if (row) row.status = 'CLOSED'
  queue.value = ''
  busy.value = false
  const last = [...items.value].reverse().find((x) => x.kind === 'sys')
  if (!last || !last.content.includes('已结束')) {
    items.value.push({ kind: 'sys', content: by === 'agent' ? '客服已结束本次会话' : '你已结束本次会话' })
  }
}

function applyAgentLeft() {
  if (currentSession.value) currentSession.value.status = 'AI'
  const row = sessions.value.find((x) => x.id === currentId.value)
  if (row) row.status = 'AI'
  queue.value = ''
  const last = [...items.value].reverse().find((x) => x.kind === 'sys')
  if (!last || !last.content.includes('已结束人工服务')) {
    items.value.push({ kind: 'sys', content: '已结束人工服务，继续由 AI 为你服务' })
  }
}

async function reopenSession() {
  await chatApi.reopen(currentId.value)
  applyReopened()
}

function applyReopened() {
  if (currentSession.value) currentSession.value.status = 'AI'
  const row = sessions.value.find((x) => x.id === currentId.value)
  if (row) row.status = 'AI'
  queue.value = ''
  const last = [...items.value].reverse().find((x) => x.kind === 'sys')
  if (!last || !last.content.includes('重新开始')) {
    items.value.push({ kind: 'sys', content: '会话已重新开始，可以继续提问或再次转人工' })
  }
}

async function cancelTransfer() {
  try {
    await chatApi.cancelTransfer(currentId.value)
    queue.value = ''
  } catch (e) {
    // 会话可能已被客服结束：刷新真实状态，别把「取消转人工」一直挂在那
    ElMessage.warning(e?.response?.data?.message || '当前已不在排队中')
  } finally {
    await loadSessions()
    await loadContext()
  }
}

async function transfer() {
  const r = await chatApi.transfer(currentId.value)
  queue.value = queueText(r.position)
  setStatus('WAITING')
  loadSessions()
}

/** 同步当前会话状态到本地（头部标签和按钮都依赖它） */
function setStatus(status) {
  if (currentSession.value) currentSession.value.status = status
  const row = sessions.value.find((x) => x.id === currentId.value)
  if (row) row.status = status
}

async function approve(it) {
  await chatApi.approve(it.approvalNo)
  it.status = 'APPROVED'
}
async function reject(it) {
  await chatApi.reject(it.approvalNo)
  it.status = 'REJECTED'
}

onMounted(async () => {
  await loadSessions()
  if (sessions.value.length) await selectSession(sessions.value[0].id)
  else await newSession()
  socket = new ChatSocket(ensureFreshToken, onFrame, (s) => {
    connected.value = s === 'open'
    if (s === 'open') resyncAfterReconnect()
  })
  socket.connect()
  if (route.query.ask) input.value = String(route.query.ask)
})

/**
 * WS 重连后补一次数据：断线期间后端推送的 AI 回复、排队状态、客服接入都会丢，
 * 只靠实时帧会让界面停旧（少一条回复、标签还写着 AI、按钮还是"转人工"）。
 */
let firstOpen = true
async function resyncAfterReconnect() {
  if (firstOpen) {
    firstOpen = false          // 首次连接时 onMounted 已经加载过
    return
  }
  await loadSessions()
  if (currentId.value) {
    const list = await chatApi.messages(currentId.value, 100)
    items.value = []
    list.forEach(pushAssets)
    await loadContext()
    await loadPendingApprovals()
  }
}

onBeforeUnmount(() => socket?.close())
</script>

<style scoped>
.chat {
  display: grid; grid-template-columns: 260px 1fr 300px;
  height: 100%; min-height: 0; overflow: hidden; background: var(--bg);
}
.side { background: var(--surface); border-right: 1px solid var(--line); display: flex; flex-direction: column; }
.side-head { display: flex; justify-content: space-between; align-items: center; padding: 16px; font-weight: 600; }
.sessions { flex: 1; overflow-y: auto; padding: 0 10px 10px; display: grid; grid-template-columns: minmax(0, 1fr); gap: 4px; align-content: start; }
.session { padding: 10px 12px; border-radius: 10px; cursor: pointer; min-width: 0; overflow: hidden; }
.session:hover { background: var(--surface-2); }
.session.on { background: var(--accent-soft); }
.s-top { display: flex; justify-content: space-between; align-items: center; gap: 8px; min-width: 0; }
.s-right { display: flex; align-items: center; gap: 6px; }
.del { border: none; background: transparent; color: var(--text-3); font-size: 15px; line-height: 1; cursor: pointer; padding: 0 2px; opacity: 0.3; transition: opacity 0.15s, color 0.15s; }
.session:hover .del { opacity: 1; }
.del:hover { color: var(--danger); }
.s-no { font-size: 11px; color: var(--text-3); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.s-msg { font-size: 12px; color: var(--text-2); margin-top: 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dot { width: 6px; height: 6px; border-radius: 50%; background: var(--text-3); display: inline-block; }
.dot.ok, .dot.AI { background: var(--ok); }
.dot.off { background: var(--danger); }
.dot.WAITING { background: var(--warn); }
.dot.AGENT { background: var(--accent); }
.dot.CLOSED { background: var(--text-3); }
.main { display: flex; flex-direction: column; min-width: 0; min-height: 0; overflow: hidden; }
.main-head { height: 56px; display: flex; justify-content: space-between; align-items: center; padding: 0 20px; background: var(--surface); border-bottom: 1px solid var(--line); }
.title { display: flex; gap: 10px; align-items: center; font-weight: 600; }
.head-right { display: flex; align-items: center; gap: 12px; }
.conn { font-size: 12px; color: var(--text-3); display: flex; gap: 6px; align-items: center; }
.banner { background: var(--warn-soft); color: var(--warn); font-size: 12px; padding: 6px 20px; display: flex; align-items: center; gap: 12px; }
.banner .el-button { color: var(--warn); }
.composer { padding: 12px 20px 16px; background: var(--surface); border-top: 1px solid var(--line); }
.composer-foot { display: flex; justify-content: space-between; align-items: center; margin-top: 8px; }
.hint { font-size: 11px; color: var(--text-3); }
.ctx { background: var(--surface); border-left: 1px solid var(--line); padding: 16px; overflow-y: auto; min-height: 0; display: grid; gap: 20px; align-content: start; }
.ctx-block h4 { margin: 0 0 6px; font-size: 12px; color: var(--text-2); font-weight: 600; }
.small { font-size: 12px; }
.orders { display: grid; gap: 6px; margin-top: 8px; }
.o-top, .t-top { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.o-sub { font-size: 11px; color: var(--text-3); margin-top: 3px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ticket { background: var(--surface-2); border: 1px solid var(--line); border-radius: 8px; padding: 8px 10px; margin-top: 6px; }
.hint { font-size: 11px; color: var(--text-3); margin: 8px 0 0; }
.empty { font-size: 12px; color: var(--text-3); padding: 8px 0; }
.order { display: block; text-align: left; width: 100%; background: var(--surface-2); border: 1px solid var(--line); border-radius: 8px; padding: 8px 10px; cursor: pointer; font-size: 12px; }
.order:hover { border-color: var(--accent); }
.no { font-size: 11px; color: var(--text); }
.state { color: var(--text-3); font-size: 11px; }
</style>