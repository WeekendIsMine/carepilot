<template>
  <div class="wallet">
    <header class="head">
      <span class="title">我的钱包</span>
      <el-button size="small" @click="load">刷新</el-button>
    </header>

    <div class="cards">
      <div class="card balance-card">
        <div class="muted small">可用余额</div>
        <div class="balance"><span class="cny">¥</span>{{ wallet.balance }}</div>
        <div class="ops">
          <el-button size="small" type="primary" @click="recharge(100)">充值 100</el-button>
          <el-button size="small" @click="recharge(500)">充值 500</el-button>
          <el-button size="small" @click="customVisible = true">自定义金额</el-button>
        </div>
      </div>
    </div>

    <div class="section-title">收支明细</div>
    <el-table v-loading="loading" :data="ledger" size="small" class="table">
      <el-table-column label="时间" width="170"><template #default="{ row }">{{ fmtTime(row.createdAt) }}</template></el-table-column>
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.amount > 0 ? 'success' : 'info'">{{ typeLabel(row.changeType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="金额" width="120">
        <template #default="{ row }">
          <span :class="row.amount > 0 ? 'in' : 'out'">{{ row.amount > 0 ? '+' : '' }}{{ row.amount }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="balanceAfter" label="余额" width="110" />
      <el-table-column prop="bizNo" label="关联单号" min-width="180" class-name="mono" />
      <el-table-column prop="remark" label="备注" min-width="150" />
    </el-table>
    <el-empty v-if="!loading && !ledger.length" description="还没有收支记录，先充点余额试试" />
    <el-pagination v-if="total > PAGE_SIZE" v-model:current-page="page" :page-size="PAGE_SIZE" :total="total"
                   layout="total, prev, pager, next" class="pager" @current-change="loadLedger" />

    <el-dialog v-model="customVisible" title="自定义充值" width="380">
      <el-input-number v-model="customAmount" :min="1" :max="50000" :step="50" />
      <template #footer>
        <el-button @click="customVisible = false">取消</el-button>
        <el-button type="primary" @click="doCustomRecharge">确认充值</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { fmtTime } from '../utils/format'
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { walletApi } from '../api'

const wallet = ref({ balance: '0.00' })
const ledger = ref([])
const loading = ref(false)
const PAGE_SIZE = 10
const page = ref(1)
const total = ref(0)
const customVisible = ref(false)
const customAmount = ref(200)

const typeLabel = (t) => ({ RECHARGE: '充值', PAY: '支付', REFUND: '退款' }[t] || t)

async function load() {
  wallet.value = await walletApi.mine()
}

async function loadLedger() {
  loading.value = true
  try {
    const res = await walletApi.ledger({ page: page.value, size: PAGE_SIZE })
    ledger.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function recharge(amount) {
  await walletApi.recharge(amount)
  ElMessage.success('充值成功')
  await Promise.all([load(), loadLedger()])
}

async function doCustomRecharge() {
  await recharge(customAmount.value)
  customVisible.value = false
}

onMounted(async () => {
  await Promise.all([load(), loadLedger()])
})
</script>

<style scoped>
.wallet { padding: 20px 28px; height: 100%; overflow-y: auto; }
.head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.title { font-size: 18px; font-weight: 700; }
.cards { display: grid; grid-template-columns: 320px; gap: 16px; margin-bottom: 22px; }
.card { background: var(--surface); border-radius: var(--radius); box-shadow: var(--shadow-sm); padding: 18px 20px; }
.balance { font-size: 34px; font-weight: 700; margin: 6px 0 14px; }
.cny { font-size: 18px; margin-right: 2px; }
.ops { display: flex; gap: 8px; flex-wrap: wrap; }
.section-title { font-size: 14px; font-weight: 600; margin-bottom: 10px; }
.table { background: var(--surface); border-radius: var(--radius); }
.in { color: #16a34a; font-weight: 600; }
.out { color: var(--text-1); }
.pager { margin-top: 12px; justify-content: flex-end; }
</style>