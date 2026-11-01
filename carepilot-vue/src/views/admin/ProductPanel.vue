<template>
  <div class="panel-body">
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜商品名" clearable style="max-width: 240px" @keyup.enter="load(true)" @clear="load(true)" />
      <el-button @click="load(true)">搜索</el-button>
      <el-button type="primary" @click="openCreate">新建商品</el-button>
    </div>

    <el-table v-loading="loading" :data="products" size="small" class="table">
      <el-table-column label="图" width="72">
        <template #default="{ row }">
          <img v-if="row.coverUrl" :src="row.coverUrl" class="thumb" />
          <div v-else class="thumb placeholder">无</div>
        </template>
      </el-table-column>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="商品" min-width="220">
        <template #default="{ row }">
          <div class="p-name">{{ row.name }}</div>
          <div class="muted small">{{ row.subtitle }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="price" label="价格" width="90" />
      <el-table-column prop="stock" label="库存" width="80" />
      <el-table-column prop="returnDays" label="退货天" width="80" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '在售' : '已下架' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280">
        <template #default="{ row }">
          <el-button link size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link size="small" :type="row.status === 1 ? 'warning' : 'success'" @click="toggle(row)">
            {{ row.status === 1 ? '下架' : '上架' }}
          </el-button>
          <el-button link size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination v-if="total > size" v-model:current-page="page" :page-size="size"
                   :total="total" layout="total, prev, pager, next" class="pager" @current-change="() => load()" />

    <el-dialog v-model="visible" :title="form.id ? '编辑商品' : '新建商品'" width="620">
      <el-form label-width="90">
        <el-form-item label="主图">
          <div class="cover-upload">
            <div v-if="form.coverUrl" class="cover-box has-img">
              <img :src="form.coverUrl" alt="主图" />
              <div class="cover-mask">
                <el-button link size="small" @click="pickCover">更换</el-button>
                <el-button link size="small" type="danger" @click="form.coverUrl = ''">移除</el-button>
              </div>
            </div>
            <div v-else class="cover-box empty" @click="pickCover">
              <span class="plus">＋</span>
              <span class="tip">上传主图</span>
            </div>
            <div class="cover-hint muted small">支持 jpg / png / webp，单张不超过 5MB</div>
            <input ref="coverInput" type="file" accept="image/*" hidden @change="onCoverPicked" />
          </div>
        </el-form-item>
        <el-form-item label="商品名"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="副标题"><el-input v-model="form.subtitle" /></el-form-item>
        <el-form-item label="详情"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="类目">
          <el-select v-model="form.categoryId" style="width: 100%">
            <el-option :value="1" label="数码家电" />
            <el-option :value="2" label="手机配件" />
            <el-option :value="3" label="服饰鞋包" />
          </el-select>
        </el-form-item>
        <div class="form-row">
          <el-form-item label="价格"><el-input-number v-model="form.price" :min="0" :precision="2" /></el-form-item>
          <el-form-item label="原价"><el-input-number v-model="form.originalPrice" :min="0" :precision="2" /></el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="库存"><el-input-number v-model="form.stock" :min="0" /></el-form-item>
          <el-form-item label="退货天数"><el-input-number v-model="form.returnDays" :min="1" /></el-form-item>
        </div>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">在售</el-radio>
            <el-radio :value="0">下架</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminProductApi } from '../../api'
import { usePagedList } from '../../composables/usePagedList'

const keyword = ref('')
const visible = ref(false)
const coverInput = ref(null)

const emptyProduct = () => ({
  id: null, name: '', subtitle: '', description: '', categoryId: 1,
  price: 0, originalPrice: 0, stock: 0, returnDays: 7, status: 1, coverUrl: ''
})
const form = ref(emptyProduct())

const { rows: products, page, total, loading, size, load } = usePagedList((p) =>
  adminProductApi.list({ page: p.page, size: p.size, keyword: keyword.value || undefined })
)

function openCreate() {
  form.value = emptyProduct()
  visible.value = true
}

function openEdit(row) {
  form.value = { ...row }
  visible.value = true
}

async function save() {
  const f = form.value
  if (!f.name) return ElMessage.warning('请填商品名')
  if (f.id) await adminProductApi.update(f.id, f)
  else await adminProductApi.create(f)
  ElMessage.success('已保存')
  visible.value = false
  await load()
}

async function toggle(row) {
  await adminProductApi.toggle(row.id)
  await load()
}

async function remove(row) {
  await ElMessageBox.confirm(
    '删除《' + row.name + '》后，商城不再展示该商品，购物车中的对应条目会被清空，图片也会从对象存储删除。\n\n注意：已经产生过订单的商品不能删除，会提示改为下架。',
    '删除商品',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
  )
  try {
    await adminProductApi.remove(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    // 拦截器已经弹出具体原因（例如"已有订单，请改为下架"）
  }
}

function pickCover() {
  coverInput.value && coverInput.value.click()
}

async function onCoverPicked(e) {
  const file = e.target.files && e.target.files[0]
  if (!file) return
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片不能超过 5MB')
    e.target.value = ''
    return
  }
  const r = await adminProductApi.uploadImage(file)
  form.value.coverUrl = r.url
  ElMessage.success('主图已上传')
  e.target.value = ''
}

load()
</script>
