<template>
  <div class="panel-body">
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜用户名/昵称" clearable style="max-width: 220px" @keyup.enter="load(true)" @clear="load(true)" />
      <el-select v-model="role" placeholder="全部角色" clearable style="width: 140px" @change="load(true)">
        <el-option value="USER" label="顾客" />
        <el-option value="AGENT" label="客服" />
        <el-option value="ADMIN" label="管理员" />
      </el-select>
      <el-button @click="load(true)">搜索</el-button>
      <el-button type="primary" @click="openCreate">新建账号</el-button>
      <span class="muted small" style="margin-left: auto">客服与管理员账号只能在这里创建</span>
    </div>

    <el-table v-loading="loading" :data="users" size="small" class="table">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="username" label="用户名" width="150" class-name="mono" />
      <el-table-column prop="nickname" label="昵称" min-width="120" />
      <el-table-column label="角色" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="roleTag(row.role)">{{ roleLabel(row.role) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="phone" label="手机号" width="130" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '正常' : '已停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="接待上限" width="100">
        <template #default="{ row }">{{ row.role === 'AGENT' ? row.maxConcurrent : '-' }}</template>
      </el-table-column>
      <el-table-column prop="lastLoginAt" label="最后登录" width="170" />
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button link size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link size="small" @click="openReset(row)">重置密码</el-button>
          <el-button link size="small" :type="row.status === 1 ? 'danger' : 'success'" @click="toggle(row)">
            {{ row.status === 1 ? '停用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination v-if="total > size" v-model:current-page="page" :page-size="size"
                   :total="total" layout="total, prev, pager, next" class="pager" @current-change="() => load()" />

    <el-dialog v-model="visible" :title="form.id ? '编辑账号' : '新建账号'" width="520">
      <el-form label-width="90">
        <el-form-item label="用户名">
          <el-input v-model="form.username" :disabled="!!form.id" placeholder="4-20 位字母、数字或下划线" />
        </el-form-item>
        <el-form-item label="昵称"><el-input v-model="form.nickname" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" placeholder="选填" /></el-form-item>
        <el-form-item label="角色">
          <el-radio-group v-model="form.role">
            <el-radio value="USER">顾客</el-radio>
            <el-radio value="AGENT">客服</el-radio>
            <el-radio value="ADMIN">管理员</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.role === 'AGENT'" label="接待上限">
          <el-input-number v-model="form.maxConcurrent" :min="1" :max="50" />
        </el-form-item>
        <el-form-item :label="form.id ? '重置密码' : '初始密码'">
          <el-input v-model="form.password" type="password" show-password
                    :placeholder="form.id ? '留空表示不修改' : '6-32 位'" />
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
import { adminUserApi } from '../../api'
import { usePagedList } from '../../composables/usePagedList'

const keyword = ref('')
const role = ref('')

const { rows: users, page, total, loading, size, load } = usePagedList((p) =>
  adminUserApi.list({
    page: p.page,
    size: p.size,
    keyword: keyword.value || undefined,
    role: role.value || undefined
  })
)

const visible = ref(false)
const emptyUser = () => ({ id: null, username: '', nickname: '', phone: '', role: 'AGENT', maxConcurrent: 5, password: '' })
const form = ref(emptyUser())

function openCreate() {
  form.value = emptyUser()
  visible.value = true
}

function openEdit(row) {
  form.value = { ...row, password: '' }
  visible.value = true
}

async function save() {
  const f = form.value
  if (!f.username || !f.nickname) return ElMessage.warning('请填写用户名与昵称')
  if (!f.id && (!f.password || f.password.length < 6)) return ElMessage.warning('初始密码至少 6 位')
  if (f.id) await adminUserApi.update(f.id, f)
  else await adminUserApi.create(f)
  ElMessage.success('已保存')
  visible.value = false
  await load()
}

async function toggle(row) {
  await adminUserApi.toggle(row.id)
  await load()
}

async function openReset(row) {
  const { value } = await ElMessageBox.prompt('输入新密码（6-32 位）', '重置密码 ' + row.username, {
    inputType: 'password',
    inputPattern: /^.{6,32}$/,
    inputErrorMessage: '密码长度需为 6-32 位'
  })
  await adminUserApi.resetPassword(row.id, value)
  ElMessage.success('密码已重置')
}

const roleLabel = (r) => ({ USER: '顾客', AGENT: '客服', ADMIN: '管理员' }[r] || r)
const roleTag = (r) => ({ USER: 'info', AGENT: 'primary', ADMIN: 'warning' }[r] || 'info')

load()
</script>
