<template>
  <div class="panel-body">
    <div class="toolbar">
      <el-radio-group v-model="status" size="small" @change="load(true)">
        <el-radio-button :value="-1">全部</el-radio-button>
        <el-radio-button :value="0">待付款</el-radio-button>
        <el-radio-button :value="1">待发货</el-radio-button>
        <el-radio-button :value="2">已发货</el-radio-button>
        <el-radio-button :value="3">已完成</el-radio-button>
        <el-radio-button :value="4">已取消</el-radio-button>
        <el-radio-button :value="5">售后中</el-radio-button>
        <el-radio-button :value="6">已退款</el-radio-button>
      </el-radio-group>
      <div class="tool-right">
        <span class="muted small">{{ autoHint }}</span>
        <el-button size="small" @click="load()">刷新</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="orders" size="small" class="table">
      <el-table-column prop="orderNo" label="订单号" width="180" class-name="mono" />
      <el-table-column prop="receiverName" label="收货人" width="100" />
      <el-table-column prop="payAmount" label="金额" width="100" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="orderTag(row.status)">{{ orderLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="下单时间" min-width="160"><template #default="{ row }">{{ fmtTime(row.createdAt) }}</template></el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button v-if="row.status === 1" link size="small" type="primary" @click="openShip(row)">发货</el-button>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination v-if="total > size" v-model:current-page="page" :page-size="size"
                   :total="total" layout="total, prev, pager, next" class="pager" @current-change="() => load()" />

    <el-dialog v-model="shipVisible" title="订单发货" width="420">
      <el-form label-width="80">
        <el-form-item label="订单号"><span class="mono">{{ shipping?.orderNo }}</span></el-form-item>
        <el-form-item label="快递公司"><el-input v-model="shipForm.carrier" /></el-form-item>
        <el-form-item label="运单号"><el-input v-model="shipForm.trackingNo" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shipVisible = false">取消</el-button>
        <el-button type="primary" @click="doShip">确认发货</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { fmtTime } from '../../utils/format'
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { orderApi } from '../../api'
import { usePagedList } from '../../composables/usePagedList'

const status = ref(-1)
const shipVisible = ref(false)
const shipping = ref(null)
const shipForm = ref({ carrier: '顺丰速运', trackingNo: '' })

const { rows: orders, page, total, loading, size, load } = usePagedList((p) =>
  orderApi.adminList({ page: p.page, size: p.size, status: status.value < 0 ? undefined : status.value })
)

function openShip(row) {
  shipping.value = row
  shipForm.value = { carrier: '顺丰速运', trackingNo: 'SF' + Date.now() }
  shipVisible.value = true
}

async function doShip() {
  try {
    await orderApi.ship(shipping.value.orderNo, shipForm.value)
  } catch (e) {
    // 典型场景：用户在下单后取消了，列表还没刷新
    shipVisible.value = false
    await load()
    return
  }
  ElMessage.success('已发货')
  shipVisible.value = false
  await load()
}

const orderLabel = (s) => ({ 0: '待付款', 1: '待发货', 2: '已发货', 3: '已完成', 4: '已取消', 5: '售后中', 6: '已退款' }[s] || s)
const orderTag = (s) => ({ 0: 'warning', 1: 'warning', 2: 'primary', 3: 'success', 4: 'info', 5: 'warning', 6: 'info' }[s] || 'info')

// 订单状态会被用户端随时改变（取消/申请售后），这里定时静默刷新，避免列表停在旧状态
const autoHint = ref('每 10 秒自动刷新')
let timer = null
onMounted(() => {
  load()
  timer = setInterval(() => load(), 10000)
})
onBeforeUnmount(() => timer && clearInterval(timer))
</script>