<template>
  <div class="shop">
    <header class="head">
      <div class="title">商品</div>
      <el-input v-model="keyword" placeholder="搜商品，例如 耳机" clearable class="search" @keyup.enter="load" @clear="load">
        <template #append><el-button @click="load">搜索</el-button></template>
      </el-input>
      <el-button class="cart-btn" @click="openCart">
        购物车
        <span v-if="cartCount" class="badge">{{ cartCount }}</span>
      </el-button>
    </header>

    <div class="cats">
      <button :class="{ on: categoryId === null }" @click="pick(null)">全部</button>
      <button v-for="c in categories" :key="c.id" :class="{ on: categoryId === c.id }" @click="pick(c.id)">{{ c.name }}</button>
    </div>

    <div v-loading="loading" class="grid">
      <article v-for="p in products" :key="p.id" class="card" @click="openDetail(p)">
        <img v-if="p.coverUrl" class="cover-img" :src="p.coverUrl" :alt="p.name" loading="lazy" />
        <div v-else class="cover" :style="{ background: coverBg(p.id) }">{{ p.name.slice(0, 1) }}</div>
        <div class="body">
          <div class="name">{{ p.name }}</div>
          <div class="sub">{{ p.subtitle }}</div>
          <div class="meta">
            <span class="price">¥{{ p.price }}</span>
            <span class="tag">{{ p.returnDays }} 天可退</span>
          </div>
          <div class="stock">库存 {{ p.stock }}</div>
        </div>
        <div class="actions">
          <el-button size="small" @click.stop="addToCart(p)">加入购物车</el-button>
          <el-button size="small" type="primary" @click.stop="buyNow(p)">立即购买</el-button>
        </div>
      </article>
      <template v-if="loading">
        <div v-for="n in 6" :key="'sk' + n" class="card skeleton">
          <el-skeleton animated>
            <template #template>
              <el-skeleton-item variant="image" style="width: 100%; height: 148px" />
              <div style="padding: 12px">
                <el-skeleton-item variant="text" style="width: 70%" />
                <el-skeleton-item variant="text" style="width: 40%; margin-top: 8px" />
              </div>
            </template>
          </el-skeleton>
        </div>
      </template>
      <el-empty v-else-if="!products.length" description="没有找到商品" />
    </div>

    <!-- 商品详情 -->
    <el-dialog v-model="detailVisible" :title="current?.name" width="520">
      <div v-if="current" class="detail">
        <img v-if="current.coverUrl" class="cover-img big" :src="current.coverUrl" :alt="current.name" />
        <div v-else class="cover big" :style="{ background: coverBg(current.id) }">{{ current.name.slice(0, 1) }}</div>
        <p v-if="current.description" class="desc">{{ current.description }}</p>
        <p class="sub">{{ current.subtitle }}</p>
        <div class="rows">
          <div><span class="k">价格</span><span class="v price">¥{{ current.price }}</span></div>
          <div><span class="k">库存</span><span class="v">{{ current.stock }}</span></div>
          <div><span class="k">无理由退货</span><span class="v">{{ current.returnDays }} 天</span></div>
        </div>
        <div class="qty">数量 <el-input-number v-model="detailQty" :min="1" :max="current.stock" size="small" /></div>
      </div>
      <template #footer>
        <el-button @click="addToCart(current, detailQty)">加入购物车</el-button>
        <el-button type="primary" @click="buyNow(current, detailQty)">立即购买</el-button>
      </template>
    </el-dialog>

    <!-- 购物车 -->
    <el-drawer v-model="cartVisible" title="购物车" size="420px">
      <div v-if="!cart.length" class="empty">购物车是空的</div>
      <div v-for="it in cart" :key="it.id" class="cart-item">
        <div class="mini" :style="{ background: coverBg(it.productId) }">{{ it.name.slice(0, 1) }}</div>
        <div class="ci-main">
          <div class="ci-name">{{ it.name }}</div>
          <div class="ci-price">¥{{ it.price }}</div>
          <div class="ci-ops">
            <el-input-number :model-value="it.quantity" :min="1" :max="it.stock" size="small" @change="(v) => changeQty(it, v)" />
            <el-button link type="danger" size="small" @click="removeItem(it)">移除</el-button>
          </div>
        </div>
      </div>
      <template #footer>
        <div class="cart-foot">
          <span>合计 <b class="price">¥{{ cartTotal }}</b></span>
          <el-button type="primary" :disabled="!cart.length" @click="openCheckout">去结算</el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 结算 -->
    <el-dialog v-model="checkoutVisible" title="确认订单" width="560">
      <div class="block-title">收货地址</div>
      <el-radio-group v-model="addressId" class="addr-list">
        <div v-for="a in addresses" :key="a.id" class="addr-row">
          <el-radio :value="a.id" class="addr">
            <b>{{ a.receiverName }}</b> {{ a.receiverPhone }}
            <el-tag v-if="a.isDefault === 1" size="small" type="success" class="def-tag">默认</el-tag>
            <span class="muted">{{ a.province }}{{ a.city }}{{ a.district }}{{ a.detailAddress }}</span>
          </el-radio>
          <div class="addr-ops">
            <el-button v-if="a.isDefault !== 1" link size="small" @click.stop="makeDefault(a)">设为默认</el-button>
            <el-button link size="small" @click.stop="editAddress(a)">编辑</el-button>
            <el-button link size="small" type="danger" @click.stop="removeAddress(a)">删除</el-button>
          </div>
        </div>
      </el-radio-group>
      <el-empty v-if="!addresses.length && !showAddrForm" description="还没有收货地址" :image-size="60" />
      <el-button link type="primary" size="small" @click="openAddrForm()">
        {{ showAddrForm && editingId ? '取消编辑' : '+ 新增地址' }}
      </el-button>
      <div v-if="showAddrForm" class="addr-form">
        <el-input v-model="addrForm.receiverName" placeholder="收货人" />
        <el-input v-model="addrForm.receiverPhone" placeholder="手机号" />
        <div class="addr-region">
          <el-input v-model="addrForm.province" placeholder="省 / 直辖市" />
          <el-input v-model="addrForm.city" placeholder="市" />
          <el-input v-model="addrForm.district" placeholder="区 / 县" />
        </div>
        <el-input v-model="addrForm.detailAddress" placeholder="详细地址：街道、门牌号" />
        <div class="addr-form-foot">
          <el-button size="small" type="primary" @click="saveAddress">
            {{ editingId ? '保存修改' : '保存地址' }}
          </el-button>
          <el-button size="small" @click="cancelAddrForm">取消</el-button>
        </div>
      </div>
      <div class="block-title">备注</div>
      <el-input v-model="remark" type="textarea" :rows="2" placeholder="选填，例如：请放门口" />
      <div class="sum">应付 <b class="price">¥{{ checkoutTotal }}</b></div>
      <template #footer>
        <el-button @click="checkoutVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitOrder">提交订单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { mallApi, orderApi } from '../api'

const router = useRouter()
const products = ref([])
const categories = ref([])
const keyword = ref('')
const categoryId = ref(null)
const cart = ref([])
const cartVisible = ref(false)
const detailVisible = ref(false)
const current = ref(null)
const detailQty = ref(1)
const checkoutVisible = ref(false)
const addresses = ref([])
const addressId = ref(null)
const remark = ref('')
const showAddrForm = ref(false)
const emptyAddress = () => ({ receiverName: '', receiverPhone: '', province: '', city: '', district: '', detailAddress: '' })
const addrForm = ref(emptyAddress())
const editingId = ref(null)
const submitting = ref(false)
const buyNowLines = ref(null)

const PALETTE = ['#dbe7ff', '#ffe8d6', '#e4f5e9', '#f3e6ff', '#fff2cc', '#dff3f7']
const coverBg = (id) => PALETTE[id % PALETTE.length]

const cartCount = computed(() => cart.value.reduce((s, i) => s + i.quantity, 0))
const cartTotal = computed(() => cart.value.reduce((s, i) => s + Number(i.amount), 0).toFixed(2))
const checkoutTotal = computed(() => {
  if (buyNowLines.value) return buyNowLines.value.reduce((s, l) => s + Number(l.price) * l.quantity, 0).toFixed(2)
  return cartTotal.value
})

const loading = ref(false)

async function load() {
  const params = {}
  if (keyword.value) params.keyword = keyword.value
  if (categoryId.value) params.categoryId = categoryId.value
  loading.value = true
  try {
    products.value = await mallApi.products(params)
  } finally {
    loading.value = false
  }
}

function pick(id) {
  categoryId.value = id
  load()
}

function openDetail(p) {
  current.value = p
  detailQty.value = 1
  detailVisible.value = true
}

async function addToCart(p, qty = 1) {
  try {
    await mallApi.addCart(p.id, qty)
  } catch (e) {
    return // 错误提示由拦截器统一弹出（例如库存不足）
  }
  ElMessage.success('已加入购物车')
  detailVisible.value = false
  await loadCart()
}

function buyNow(p, qty = 1) {
  buyNowLines.value = [{ productId: p.id, quantity: qty, price: p.price }]
  detailVisible.value = false
  openCheckout()
}

async function loadCart() {
  cart.value = await mallApi.cart()
}

async function openCart() {
  await loadCart()
  cartVisible.value = true
}

async function changeQty(it, v) {
  try {
    await mallApi.updateCart(it.id, v)
  } catch (e) {
    await loadCart() // 失败时把输入框拉回真实数量
    return
  }
  await loadCart()
}

async function removeItem(it) {
  await mallApi.removeCart(it.id)
  await loadCart()
}

async function openCheckout() {
  if (!buyNowLines.value) await loadCart()
  if (!buyNowLines.value && !cart.value.length) return ElMessage.warning('购物车是空的')
  cartVisible.value = false
  newIdemKey()
  addresses.value = await mallApi.addresses()
  if (!addressId.value && addresses.value.length) addressId.value = addresses.value[0].id
  checkoutVisible.value = true
}

async function saveAddress() {
  const f = addrForm.value
  if (!f.receiverName || !f.receiverPhone) return ElMessage.warning('请填写收货人和手机号')
  if (!f.province || !f.city || !f.district) return ElMessage.warning('请填写省 / 市 / 区')
  if (!f.detailAddress) return ElMessage.warning('请填写详细地址')
  const payload = { ...f }
  const saved = editingId.value
    ? await mallApi.updateAddress(editingId.value, payload)
    : await mallApi.addAddress(payload)
  addresses.value = await mallApi.addresses()
  addressId.value = saved.id
  cancelAddrForm()
}

function openAddrForm() {
  if (showAddrForm.value && editingId.value) {
    cancelAddrForm()
    return
  }
  editingId.value = null
  addrForm.value = emptyAddress()
  showAddrForm.value = true
}

function editAddress(a) {
  editingId.value = a.id
  addrForm.value = {
    receiverName: a.receiverName,
    receiverPhone: a.receiverPhone,
    province: a.province || '',
    city: a.city || '',
    district: a.district || '',
    detailAddress: a.detailAddress
  }
  showAddrForm.value = true
}

function cancelAddrForm() {
  showAddrForm.value = false
  editingId.value = null
  addrForm.value = emptyAddress()
}

async function makeDefault(a) {
  await mallApi.setDefaultAddress(a.id)
  addresses.value = await mallApi.addresses()
  ElMessage.success('已设为默认地址')
}

async function removeAddress(a) {
  await ElMessageBox.confirm(
    '删除「' + a.receiverName + ' ' + a.detailAddress + '」这个收货地址？',
    '删除地址',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
  )
  await mallApi.removeAddress(a.id)
  addresses.value = await mallApi.addresses()
  if (addressId.value === a.id) {
    addressId.value = addresses.value.length ? addresses.value[0].id : null
  }
  if (editingId.value === a.id) cancelAddrForm()
  ElMessage.success('已删除')
}

// 幂等键：同一轮结算里重复点击/重试都复用同一个 key，服务端只会真正下单一次
const orderIdemKey = ref('')
function newIdemKey() {
  orderIdemKey.value = (crypto.randomUUID && crypto.randomUUID()) ||
    ('k-' + Date.now() + '-' + Math.random().toString(36).slice(2, 10))
}

async function submitOrder() {
  if (!addressId.value) return ElMessage.warning('请选择收货地址')
  submitting.value = true
  try {
    const payload = { addressId: addressId.value, remark: remark.value }
    if (buyNowLines.value) payload.items = buyNowLines.value.map((l) => ({ productId: l.productId, quantity: l.quantity }))
    if (!orderIdemKey.value) newIdemKey()
    const order = await orderApi.create(payload, orderIdemKey.value)
    ElMessage.success('下单成功：' + order.orderNo)
    checkoutVisible.value = false
    buyNowLines.value = null
    orderIdemKey.value = ''
    await loadCart()
    router.push('/orders')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  categories.value = await mallApi.categories()
  await load()
  await loadCart()
})
</script>

<style scoped>
.skeleton { pointer-events: none; }
.shop { padding: 20px 28px; overflow-y: auto; height: 100%; }
.head { display: flex; align-items: center; gap: 16px; margin-bottom: 14px; }
.title { font-size: 18px; font-weight: 700; }
.search { max-width: 360px; }
.cart-btn { margin-left: auto; position: relative; }
.badge { background: var(--accent); color: #fff; border-radius: 10px; padding: 0 6px; font-size: 11px; margin-left: 6px; }
.cats { display: flex; gap: 8px; margin-bottom: 18px; }
.cats button { border: 1px solid var(--line); background: var(--surface); padding: 6px 14px; border-radius: 20px; font-size: 13px; color: var(--text-2); cursor: pointer; }
.cats button.on { background: var(--accent-soft); border-color: var(--accent-soft); color: var(--accent); font-weight: 600; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(230px, 1fr)); gap: 16px; }
.card { background: var(--surface); border-radius: var(--radius); overflow: hidden; box-shadow: var(--shadow-sm); cursor: pointer; transition: box-shadow 0.2s, transform 0.2s; display: flex; flex-direction: column; }
.card:hover { box-shadow: var(--shadow); transform: translateY(-2px); }
.cover { height: 130px; display: grid; place-items: center; font-size: 40px; font-weight: 800; color: rgba(20,23,31,0.18); }
.cover-img { width: 100%; height: 170px; object-fit: contain; display: block; background: #fff; padding: 8px; }
.cover-img.big { height: 240px; border-radius: var(--radius); object-fit: contain; }
.desc { font-size: 13px; color: var(--text-2); line-height: 1.7; margin: 10px 0; }
.cover.big { height: 160px; border-radius: var(--radius); font-size: 54px; }
.body { padding: 12px 14px; flex: 1; }
.name { font-weight: 600; font-size: 14px; line-height: 1.4; }
.sub { color: var(--text-3); font-size: 12px; margin-top: 4px; }
.meta { display: flex; align-items: center; justify-content: space-between; margin-top: 10px; }
.price { color: var(--danger); font-weight: 700; }
.tag { font-size: 11px; color: var(--text-3); background: var(--surface-2); padding: 2px 6px; border-radius: 6px; }
.stock { font-size: 11px; color: var(--text-3); margin-top: 6px; }
.actions { display: flex; gap: 8px; padding: 0 14px 14px; }
.rows { display: grid; gap: 8px; margin: 12px 0; }
.rows .k { color: var(--text-3); font-size: 12px; display: inline-block; width: 90px; }
.qty { display: flex; align-items: center; gap: 10px; font-size: 13px; }
.cart-item { display: flex; gap: 12px; padding: 12px 0; border-bottom: 1px solid var(--line); }
.mini { width: 44px; height: 44px; border-radius: 10px; display: grid; place-items: center; font-weight: 700; color: rgba(20,23,31,0.2); flex: 0 0 44px; }
.ci-main { flex: 1; }
.ci-name { font-size: 13px; }
.ci-price { color: var(--danger); font-weight: 600; margin: 4px 0; }
.ci-ops { display: flex; align-items: center; gap: 10px; }
.cart-foot { display: flex; justify-content: space-between; align-items: center; width: 100%; }
.block-title { font-size: 12px; color: var(--text-3); margin: 10px 0 8px; }
.addr-list { display: grid; gap: 6px; }
.addr { height: auto; padding: 6px 0; display: block; }
.addr-form { display: grid; gap: 8px; margin-top: 10px; }
.addr-row { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 2px 0; }
.addr-row .el-radio { flex: 1; min-width: 0; }
.addr-ops { flex: 0 0 auto; display: flex; gap: 2px; }
.addr-ops .el-button { margin-left: 0; }
.def-tag { margin: 0 4px; }
.addr-form-foot { display: flex; gap: 8px; }
.addr-region { display: grid; grid-template-columns: 1.2fr 1fr 1fr; gap: 8px; }
.sum { margin-top: 14px; text-align: right; }
.empty { color: var(--text-3); text-align: center; padding: 40px 0; }
</style>