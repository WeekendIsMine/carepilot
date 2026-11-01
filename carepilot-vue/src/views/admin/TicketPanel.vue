<template>
  <div class="panel-body">
    <div class="toolbar">
      <el-radio-group v-model="status" size="small" @change="load(true)">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="PENDING">待审核</el-radio-button>
        <el-radio-button value="APPROVED">已通过</el-radio-button>
        <el-radio-button value="REFUNDING">退款中</el-radio-button>
        <el-radio-button value="DONE">已完成</el-radio-button>
      </el-radio-group>
    </div>

    <el-table v-loading="loading" :data="tickets" size="small" class="table">
      <el-table-column prop="ticketNo" label="工单号" width="170" class-name="mono" />
      <el-table-column prop="orderNo" label="订单号" width="160" class-name="mono" />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">{{ typeLabel(row.type) }}</template>
      </el-table-column>
      <el-table-column prop="reason" label="原因" min-width="160" />
      <el-table-column prop="amount" label="金额" width="90" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="statusType(row.status)">{{ label(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="退款" width="190">
        <template #default="{ row }">
          <template v-if="row.refundNo">
            <el-tag size="small" :type="row.refundStatus === 'SUCCESS' ? 'success' : 'warning'">
              {{ row.refundStatus === 'SUCCESS' ? '已退款' : '退款中' }}
            </el-tag>
            <div class="muted small mono">{{ row.refundNo }}</div>
            <div class="muted small">¥{{ row.refundAmount }} · {{ row.refundChannel === 'BALANCE' ? '退回余额' : '原路退回' }}</div>
          </template>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260">
        <template #default="{ row }">
          <el-button v-if="row.status === 'PENDING'" link size="small" type="success" @click="transit(row, 'APPROVE')">通过</el-button>
          <el-button v-if="row.status === 'PENDING'" link size="small" type="danger" @click="transit(row, 'REJECT')">拒绝</el-button>
          <el-button v-if="row.status === 'APPROVED'" link size="small" @click="transit(row, 'REFUND')">发起退款</el-button>
          <el-button v-if="row.status === 'REFUNDING'" link size="small" type="success" @click="transit(row, 'FINISH')">完成</el-button>
          <el-button v-if="!['DONE','CLOSED'].includes(row.status)" link size="small" @click="transit(row, 'CLOSE')">关闭</el-button>
          <el-button link size="small" @click="showLogs(row)">日志</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination v-if="total > size" v-model:current-page="page" :page-size="size"
                   :total="total" layout="total, prev, pager, next" class="pager" @current-change="() => load()" />

    <el-dialog v-model="logVisible" title="流转日志" width="520">
      <el-timeline>
        <el-timeline-item v-for="l in logs" :key="l.id" :timestamp="l.createdAt">
          <span class="log-action">{{ actionLabel(l.action) }}</span>
          <span class="muted"> · </span>
          {{ statusLabel(l.fromStatus) }} → <b>{{ statusLabel(l.toStatus) }}</b>
          <div class="muted small">{{ l.remark }}</div>
        </el-timeline-item>
      </el-timeline>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { workbenchApi } from '../../api'
import { usePagedList } from '../../composables/usePagedList'

const status = ref('')
const logs = ref([])
const logVisible = ref(false)

const { rows: tickets, page, total, loading, size, load } = usePagedList((p) =>
  workbenchApi.tickets({ page: p.page, size: p.size, status: status.value || undefined })
)

async function transit(row, action) {
  await workbenchApi.transit(row.id, action)
  ElMessage.success('已处理')
  await load()
}

async function showLogs(row) {
  logs.value = await workbenchApi.logs(row.id)
  logVisible.value = true
}

const label = (s) => ({ PENDING: '待审核', APPROVED: '已通过', REJECTED: '已拒绝', REFUNDING: '退款中', DONE: '已完成', CLOSED: '已关闭' }[s] || s)
const typeLabel = (t) => ({ REFUND: '退款', RETURN: '退货', EXCHANGE: '换货' }[t] || t || '—')
const actionLabel = (a) => ({ CREATE: '创建工单', APPROVE: '审核通过', REJECT: '审核拒绝',
                              REFUND: '发起退款', FINISH: '处理完成', CLOSE: '关闭工单' }[a] || a)
// 日志里的状态可能为空（创建前是 "-"）
const statusLabel = (s) => (!s || s === '-' ? '无' : label(s))
const statusType = (s) => ({ PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', REFUNDING: 'warning', DONE: 'success', CLOSED: 'info' }[s] || 'info')

load()
</script>
<style scoped>
.log-action { font-weight: 600; }
</style>
