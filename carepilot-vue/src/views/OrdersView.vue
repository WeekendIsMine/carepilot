<template>
  <div class="orders">
    <header class="head"><span class="title">我的订单</span>
      <el-button size="small" @click="load">刷新</el-button>
    </header>

    <div v-loading="loading" class="order-list">
    <div v-for="o in orders" :key="o.id" class="order">
      <div class="o-head">
        <span class="mono no">{{ o.orderNo }}</span>
        <el-tag size="small" :type="tagType(o.status)">{{ statusLabel(o.status) }}</el-tag>
        <span class="muted time">{{ fmtTime(o.createdAt) }}</span>
      </div>
      <div class="o-body">
        <div class="amount">实付 <b class="price">¥{{ o.payAmount }}</b></div>
        <div class="ops">
          <el-button v-if="o.status === 0" size="small" type="primary" @click="openPay(o)">支付</el-button>
          <el-button v-if="o.status === 0 || o.status === 1" size="small" @click="cancel(o)">取消</el-button>
          <el-button v-if="o.status === 2" size="small" type="success" @click="receive(o)">确认收货</el-button>
          <el-button v-if="o.status === 3" size="small" @click="afterSale(o)">申请售后</el-button>
          <el-tag v-if="o.status === 5" size="small" type="warning" class="sale-tag">售后处理中</el-tag>
          <el-button size="small" link @click="showDetail(o)">详情</el-button>
        </div>
      </div>
    </div>
    </div>
    <el-empty v-if="!loading && !orders.length" description="还没有订单，去商城逛逛" />

    <el-pagination v-if="total > PAGE_SIZE" v-model:current-page="page" :page-size="PAGE_SIZE" :total="total"
                   layout="total, prev, pager, next" class="pager" @current-change="load" />

    <el-dialog v-model="payVisible" title="收银台" width="460">
      <div v-if="payOrder" class="pay-box">
        <div class="pay-amount">应付 <b class="price">¥{{ payOrder.payAmount }}</b></div>
        <div class="pay-no muted small">订单号 {{ payOrder.orderNo }}</div>
        <div class="pay-channel">
          <span class="pay-channel-name">余额支付</span>
          <span class="muted small">当前余额 ¥{{ balance }}</span>
        </div>
        <p v-if="balanceEnough === false" class="warn small">
          余额不足，还差 ¥{{ (Number(payOrder.payAmount) - Number(balance)).toFixed(2) }}
          <el-button link type="primary" size="small" @click="goWallet">去充值</el-button>
        </p>
      </div>
      <template #footer>
        <el-button @click="payVisible = false">取消</el-button>
        <el-button type="primary" :loading="paying" @click="confirmPay">确认支付</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="订单详情" width="520">
      <div v-if="detail" class="detail">
        <div class="d-row"><span class="k">订单号</span><span class="mono">{{ detail.order.orderNo }}</span></div>
        <div class="d-row"><span class="k">状态</span><span>{{ detail.statusName }}</span></div>
        <div class="d-row"><span class="k">收货人</span><span>{{ detail.order.receiverName }} {{ detail.order.receiverPhone }}</span></div>
        <div class="d-row"><span class="k">地址</span><span>{{ detail.order.receiverAddress }}</span></div>
        <div class="d-row"><span class="k">备注</span><span>{{ detail.order.remark || '—' }}</span></div>
        <el-divider />
        <div v-for="i in detail.items" :key="i.id" class="item">
          <span>{{ i.productName }} × {{ i.quantity }}</span>
          <span class="price">¥{{ i.amount }}</span>
        </div>
        <div v-if="detail.logistics" class="d-row"><span class="k">物流</span><span>{{ detail.logistics.carrier }} {{ detail.logistics.trackingNo }} · {{ detail.logistics.currentInfo }}</span></div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { fmtTime } from '../utils/format'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { orderApi, walletApi } from '../api'

const router = useRouter()
const orders = ref([])
const PAGE_SIZE = 10
const page = ref(1)
const total = ref(0)
const detailVisible = ref(false)
const detail = ref(null)

const statusLabel = (s) => ({ 0: '待付款', 1: '待发货', 2: '已发货', 3: '已完成', 4: '已取消', 5: '售后中', 6: '已退款' }[s] || s)
const tagType = (s) => ({ 0: 'warning', 1: 'warning', 2: 'primary', 3: 'success', 4: 'info', 5: 'warning', 6: 'info' }[s] || 'info')

const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await orderApi.list({ page: page.value, size: PAGE_SIZE })
    orders.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

// 支付：仅支持余额支付
const payVisible = ref(false)
const payOrder = ref(null)
const balanceEnough = ref(true)
const paying = ref(false)
const balance = ref('0.00')

async function openPay(o) {
  payOrder.value = o
  try {
    balance.value = (await walletApi.mine()).balance
  } catch (e) {
    balance.value = '0.00'
  }
  balanceEnough.value = Number(balance.value) >= Number(payOrder.value.payAmount)
  payVisible.value = true
}

async function confirmPay() {
  if (!payOrder.value) return
  paying.value = true
  try {
    const rec = await orderApi.pay(payOrder.value.orderNo, 'BALANCE')
    ElMessage.success('支付成功，支付单号 ' + rec.payNo)
    payVisible.value = false
    await load()
  } catch (e) {
    // 余额不足等具体原因由拦截器提示
  } finally {
    paying.value = false
  }
}

async function cancel(o) {
  await ElMessageBox.confirm('确定取消订单 ' + o.orderNo + ' 吗？', '取消订单')
  await orderApi.cancel(o.orderNo)
  ElMessage.success('已取消')
  await load()
}

async function receive(o) {
  await orderApi.receive(o.orderNo)
  ElMessage.success('已确认收货')
  await load()
}

function afterSale(o) {
  router.push({ path: '/chat', query: { ask: '订单 ' + o.orderNo + ' 我想申请售后' } })
}

async function showDetail(o) {
  const [d, funds] = await Promise.all([
    orderApi.detail(o.orderNo),
    orderApi.payments(o.orderNo).catch(() => ({ payments: [], refunds: [] }))
  ])
  detail.value = { ...d, payments: funds.payments || [], refunds: funds.refunds || [] }
  detailVisible.value = true
}

function goWallet() {
  payVisible.value = false
  router.push('/wallet')
}

onMounted(load)
</script>

<style scoped>
.orders { padding: 20px 28px; height: 100%; overflow-y: auto; }
.pay-box { display: flex; flex-direction: column; gap: 10px; }
.pay-amount { font-size: 15px; }
.pay-channel { display: flex; align-items: baseline; gap: 10px; border: 1px solid var(--accent); background: var(--accent-soft); border-radius: 10px; padding: 10px 12px; }
.pay-channel-name { font-weight: 600; color: var(--accent); }
.warn { color: var(--warn); }
.pay-channels :deep(.el-radio) { margin-right: 0; }
.funds { margin-top: 14px; border-top: 1px dashed var(--border); padding-top: 10px; }
.funds-title { font-size: 13px; font-weight: 600; margin: 8px 0 6px; }
.funds-row { display: grid; grid-template-columns: 1fr 90px 80px 70px; align-items: center; gap: 8px; font-size: 12px; padding: 4px 0; }
.head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.title { font-size: 18px; font-weight: 700; }
.order { background: var(--surface); border-radius: var(--radius); box-shadow: var(--shadow-sm); margin-bottom: 12px; padding: 14px 16px; }
.o-head { display: flex; align-items: center; gap: 10px; }
.no { font-size: 12px; color: var(--text-2); }
.time { margin-left: auto; font-size: 12px; }
.o-body { display: flex; justify-content: space-between; align-items: center; margin-top: 12px; }
.price { color: var(--danger); }
.ops { display: flex; gap: 8px; }
.d-row { display: flex; gap: 12px; font-size: 13px; margin-bottom: 8px; }
.d-row .k { color: var(--text-3); width: 60px; flex: 0 0 60px; }
.item { display: flex; justify-content: space-between; font-size: 13px; padding: 4px 0; }
</style>