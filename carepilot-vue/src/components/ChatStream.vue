<template>
  <div ref="scroller" class="stream">
    <template v-for="(it, i) in items" :key="it.key || i">
      <!-- 普通消息 -->
      <div v-if="it.kind === 'msg'" class="row" :class="it.role.toLowerCase()">
        <div v-if="it.role !== 'USER'" class="avatar">{{ it.role === 'AGENT' ? '客' : 'AI' }}</div>
        <div class="bubble" :class="it.role.toLowerCase()">
          <div v-if="it.role === 'AI'" class="md" v-html="render(it.content)" />
          <div v-else class="plain">{{ it.content }}</div>
          <span v-if="it.streaming" class="caret" />
          <div v-if="it.citations?.length" class="cites">
            <div class="cites-title">引用来源</div>
            <div v-for="c in it.citations" :key="c.index" class="cite">
              <span class="idx">【{{ c.index }}】</span>
              <span class="doc">{{ c.docTitle }}</span>
              <span class="path">{{ c.titlePath }}</span>
              <span class="score mono">{{ c.score }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 工具调用 -->
      <div v-else-if="it.kind === 'tool'" class="tool" :class="it.status">
        <span class="tool-dot" />
        <span class="tool-name mono">{{ it.tool }}</span>
        <span class="tool-text">{{ it.status === 'running' ? it.hint : it.summary }}</span>
        <span v-if="it.costMs" class="mono muted">{{ it.costMs }}ms</span>
      </div>

      <!-- 审批卡（Human-in-the-loop） -->
      <div v-else-if="it.kind === 'approval'" class="approval" :class="it.status.toLowerCase()">
        <div class="ap-head">
          <span class="ap-badge">需要你确认</span>
          <span class="mono muted">{{ it.approvalNo }}</span>
        </div>
        <div class="ap-body">
          <div class="ap-line"><b>{{ typeLabel(it.args?.type) }}</b>{{ it.args?.productName }}</div>
          <div class="ap-line sub">订单 {{ it.args?.orderNo }} · 预计退款 <b>￥{{ it.args?.amount }}</b></div>
          <div class="ap-line sub">原因：{{ it.args?.reason }}</div>
        </div>
        <div v-if="it.status === 'PENDING'" class="ap-actions">
          <el-button size="small" type="primary" @click="$emit('approve', it)">确认提交</el-button>
          <el-button size="small" @click="$emit('reject', it)">取消</el-button>
        </div>
        <div v-else class="ap-actions">
          <el-tag :type="it.status === 'APPROVED' ? 'success' : 'info'" size="small">
            {{ it.status === 'APPROVED' ? '已创建工单' : it.status === 'REJECTED' ? '已取消' : it.status }}
          </el-tag>
        </div>
      </div>

      <!-- 系统提示 -->
      <div v-else-if="it.kind === 'sys'" class="sys">{{ it.content }}</div>
    </template>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref, watch } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

const props = defineProps({ items: { type: Array, default: () => [] } })
defineEmits(['approve', 'reject'])

const scroller = ref(null)

const HINTS = {
  queryOrder: '正在查询订单…',
  queryLogistics: '正在查询物流…',
  listMyOrders: '正在翻你的订单…',
  queryAfterSalePolicy: '正在检索售后政策…',
  submitAfterSaleRequest: '正在生成审批单…',
  transferToHuman: '正在转接人工…',
  listMyTickets: '正在查询工单…'
}

marked.setOptions({ breaks: true, gfm: true })
function render(md) {
  return DOMPurify.sanitize(marked.parse(md || ''))
}

function typeLabel(t) {
  return { REFUND: '退款', RETURN: '退货', EXCHANGE: '换货' }[t] || '售后'
}

onMounted(async () => {
  await nextTick()
  if (scroller.value) scroller.value.scrollTop = scroller.value.scrollHeight
})

watch(
  () => [props.items.length, props.items.at(-1)?.content?.length],
  async () => {
    await nextTick()
    if (scroller.value) scroller.value.scrollTop = scroller.value.scrollHeight
  }
)
</script>

<style scoped>
.stream {
  flex: 1; min-height: 0; overflow-y: auto; overscroll-behavior: contain;
  padding: 24px 28px 8px; display: flex; flex-direction: column; gap: 14px;
}
.row { display: flex; gap: 10px; align-items: flex-start; }
.row.user { justify-content: flex-end; }
.avatar {
  width: 28px; height: 28px; border-radius: 9px; background: var(--accent-soft); color: var(--accent);
  display: grid; place-items: center; font-size: 11px; font-weight: 700; flex: 0 0 28px;
}
.bubble { max-width: 640px; padding: 12px 14px; border-radius: 14px; line-height: 1.65; font-size: 14px; }
.bubble.user { background: var(--accent); color: #fff; border-top-right-radius: 4px; white-space: pre-wrap; }
.bubble.ai, .bubble.agent { background: var(--surface); box-shadow: var(--shadow-sm); border: 1px solid var(--line); border-top-left-radius: 4px; }
.caret { display: inline-block; width: 6px; height: 15px; background: var(--accent); margin-left: 2px; vertical-align: -3px; animation: blink 1s steps(2) infinite; }
@keyframes blink { 50% { opacity: 0; } }
.md :deep(p) { margin: 0 0 8px; }
.md :deep(p:last-child) { margin-bottom: 0; }
.md :deep(ul), .md :deep(ol) { margin: 6px 0; padding-left: 20px; }
.md :deep(code) { background: var(--surface-2); padding: 1px 5px; border-radius: 5px; font-size: 12px; }
.md :deep(strong) { font-weight: 600; }
.cites { margin-top: 10px; padding-top: 10px; border-top: 1px dashed var(--line-strong); display: grid; gap: 6px; }
.cites-title { font-size: 11px; color: var(--text-3); }
.cite { display: flex; gap: 6px; align-items: baseline; font-size: 12px; color: var(--text-2); }
.cite .idx { color: var(--accent); font-weight: 600; }
.cite .doc { font-weight: 600; }
.cite .path { color: var(--text-3); }
.cite .score { margin-left: auto; color: var(--text-3); }
.tool {
  align-self: flex-start; margin-left: 38px; display: flex; align-items: center; gap: 8px;
  background: var(--surface-2); border: 1px solid var(--line); border-radius: 20px;
  padding: 4px 12px; font-size: 12px; color: var(--text-2);
}
.tool-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--accent); }
.tool.running .tool-dot { animation: pulse 1.2s infinite; }
.tool.done .tool-dot { background: var(--ok); }
@keyframes pulse { 50% { opacity: 0.25; } }
.tool-name { color: var(--accent); }
.approval {
  align-self: flex-start; margin-left: 38px; width: 420px; background: var(--surface);
  border: 1px solid var(--line-strong); border-radius: var(--radius); box-shadow: var(--shadow);
  padding: 14px 16px; display: grid; gap: 10px;
}
.approval.approved { border-color: var(--ok); }
.approval.rejected, .approval.expired { opacity: 0.65; }
.ap-head { display: flex; justify-content: space-between; align-items: center; }
.ap-badge { font-size: 11px; font-weight: 700; color: var(--warn); background: var(--warn-soft); padding: 3px 8px; border-radius: 6px; }
.ap-line { font-size: 13px; line-height: 1.6; }
.ap-actions { display: flex; gap: 8px; }
.sys { align-self: center; font-size: 12px; color: var(--text-3); background: var(--surface-2); padding: 4px 12px; border-radius: 20px; }
</style>