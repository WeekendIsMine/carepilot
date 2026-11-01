<template>
  <div class="panel-body">
    <div class="stats">
      <div class="stat"><span class="k">文档</span><span class="v">{{ stats.documents || 0 }}</span></div>
      <div class="stat"><span class="k">知识块</span><span class="v">{{ stats.chunks || 0 }}</span></div>
      <div class="stat"><span class="k">检索</span><span class="v small">Milvus + ES · RRF · rerank</span></div>
    </div>

    <div class="toolbar">
      <el-button @click="loadKb">刷新</el-button>
      <el-button :loading="busy" @click="reindex">全量重建索引</el-button>
      <el-upload :show-file-list="false" :http-request="upload" :multiple="true"
                 accept=".md,.markdown,.txt,.pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.html,.htm,.csv,.rtf">
        <el-button type="primary">上传文档</el-button>
      </el-upload>
      <el-button v-if="stats.failures" type="warning" plain @click="openFailures">
        解析失败 {{ stats.failures }}
      </el-button>
      <span v-if="uploading" class="muted small">上传中 {{ uploading }} 个…</span>
      <span v-else class="muted small">可多选、可混合格式：md / txt / pdf / doc / pptx / xlsx / html / csv，单文件 ≤ 10MB</span>
    </div>

    <el-empty v-if="!docs.length" description="知识库还是空的，点上方「上传文档」添加" />
    <el-table v-else v-loading="loading" :data="docs" size="small" class="table">
      <el-table-column prop="id" label="#" width="60" />
      <el-table-column prop="title" label="标题" min-width="220" />
      <el-table-column label="分类" width="110">
        <template #default="{ row }">{{ row.category || '—' }}</template>
      </el-table-column>
      <el-table-column label="来源" width="90">
        <template #default="{ row }">{{ sourceLabel(row.sourceType) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="docStatusTag(row.status)">{{ docStatusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="chunkCount" label="块数" width="80" />
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button link size="small" type="primary" @click="viewDoc(row)">查看</el-button>
          <el-button link size="small" @click="reingest(row)">重新解析</el-button>
          <el-button link size="small" type="danger" @click="removeDoc(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination v-if="kbTotal > PAGE_SIZE" v-model:current-page="kbPage" :page-size="PAGE_SIZE"
                   :total="kbTotal" layout="total, prev, pager, next" class="pager" @current-change="() => loadKb()" />

    <el-dialog v-model="failVisible" title="解析失败（死信）" width="760">
      <p class="muted small">解析重试 3 次仍失败的任务会落到这里，可手动重试。</p>
      <el-table :data="failures" size="small" class="table">
        <el-table-column prop="documentTitle" label="文档" min-width="140" />
        <el-table-column prop="error" label="错误" min-width="240" show-overflow-tooltip />
        <el-table-column prop="attempts" label="重试次数" width="90" />
        <el-table-column label="时间" width="170"><template #default="{ row }">{{ fmtTime(row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link size="small" type="primary" @click="retryFailure(row)">重试</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!failures.length" description="没有失败任务" />
    </el-dialog>

    <el-dialog v-model="previewVisible" :title="preview.title" width="860" top="6vh" destroy-on-close>
      <div class="preview-body">
        <iframe v-if="preview.kind === 'pdf'" :src="preview.url" class="pdf-frame" />
        <div v-else-if="preview.kind === 'md'" class="md-preview" v-html="preview.html" />
        <div v-else-if="preview.kind === 'text'" class="text-preview"><pre>{{ preview.content }}</pre></div>
        <div v-else-if="preview.kind === 'office'" class="text-preview">
          <p class="muted small">该格式浏览器无法直接渲染，以下是从文档中抽取的正文：</p>
          <pre>{{ preview.content }}</pre>
        </div>
        <el-empty v-else description="暂不支持预览" />
      </div>
      <template #footer>
        <el-button @click="downloadDoc">下载原件</el-button>
        <el-button type="primary" @click="previewVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { fmtTime } from '../../utils/format'
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { kbApi } from '../../api'

const docs = ref([])
const PAGE_SIZE = 10
const kbPage = ref(1)
const kbTotal = ref(0)
const loading = ref(false)
const stats = ref({})
const busy = ref(false)
const uploading = ref(0)

const failVisible = ref(false)
const failures = ref([])

const previewVisible = ref(false)
const preview = ref({ title: '', kind: 'text', content: '', url: '', html: '' })
let previewRow = null
let previewObjectUrl = ''

async function loadKb(reset) {
  if (reset === true) kbPage.value = 1
  loading.value = true
  try {
    const [res, st] = await Promise.all([
      kbApi.documents({ page: kbPage.value, size: PAGE_SIZE }),
      kbApi.stats()
    ])
    docs.value = res.records
    kbTotal.value = res.total
    stats.value = st
  } finally {
    loading.value = false
  }
}

async function openFailures() {
  const res = await kbApi.failures({ resolved: 0, page: 1, size: 50 })
  failures.value = res.records
  failVisible.value = true
}

async function retryFailure(row) {
  await kbApi.retryFailure(row.id)
  ElMessage.success('已重新提交解析')
  failures.value = failures.value.filter((f) => f.id !== row.id)
  await loadKb()
}

async function reindex() {
  busy.value = true
  try {
    await kbApi.reindex()
    ElMessage.success('已提交重建，稍后刷新查看')
  } finally {
    busy.value = false
  }
}

// 站内预览：文本类直接显示、PDF 内嵌渲染、Office 显示抽取正文
async function viewDoc(row) {
  previewRow = row
  const ext = (row.fileName || '').split('.').pop().toLowerCase()
  try {
    if (ext === 'pdf') {
      const blob = await kbApi.file(row.id)
      if (previewObjectUrl) URL.revokeObjectURL(previewObjectUrl)
      previewObjectUrl = URL.createObjectURL(blob)
      preview.value = { title: row.title, kind: 'pdf', content: '', url: previewObjectUrl, html: '' }
    } else {
      const text = await kbApi.text(row.id)
      if (ext === 'md' || ext === 'markdown') {
        preview.value = { title: row.title, kind: 'md', content: text, url: '', html: DOMPurify.sanitize(marked.parse(text || '')) }
      } else {
        const office = ['doc', 'docx', 'ppt', 'pptx', 'xls', 'xlsx', 'rtf'].includes(ext)
        preview.value = { title: row.title, kind: office ? 'office' : 'text', content: text, url: '', html: '' }
      }
    }
    previewVisible.value = true
  } catch (e) {
    ElMessage.error('预览失败')
  }
}

async function downloadDoc() {
  if (!previewRow) return
  const blob = await kbApi.file(previewRow.id)
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = previewRow.fileName || previewRow.title
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 10000)
}

async function removeDoc(row) {
  await ElMessageBox.confirm(
    '删除《' + row.title + '》后，它的知识块、向量索引与关键词索引会一并清除，且不可恢复。',
    '删除文档',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
  )
  await kbApi.remove(row.id)
  ElMessage.success('已删除')
  await loadKb()
}

async function reingest(row) {
  await kbApi.reingest(row.id)
  ElMessage.success('已提交解析')
}

// 多选时 Element Plus 会为每个文件各调用一次 upload，这里做并发计数与汇总提示
let uploadOk = 0
let uploadFail = 0
let uploadReplaced = 0
let refreshTimer = null

async function upload({ file }) {
  const fd = new FormData()
  fd.append('file', file)
  fd.append('title', file.name.replace(/\.[^.]+$/, ''))
  uploading.value += 1
  try {
    const r = await kbApi.upload(fd)
    uploadOk += 1
    if (r && r.replaced) uploadReplaced += 1
  } catch (e) {
    uploadFail += 1
  } finally {
    uploading.value -= 1
    if (uploading.value === 0) {
      const ok = uploadOk
      const fail = uploadFail
      const replaced = uploadReplaced
      uploadOk = 0
      uploadFail = 0
      uploadReplaced = 0
      const tail = replaced > 0 ? '（其中 ' + replaced + ' 个与已有文档内容相同，已覆盖）' : ''
      if (fail === 0) ElMessage.success('已提交 ' + ok + ' 个文件' + tail + '，后台解析中')
      else if (ok === 0) ElMessage.error(fail + ' 个文件上传失败')
      else ElMessage.warning('成功 ' + ok + ' 个，失败 ' + fail + ' 个' + tail)
      scheduleRefresh()
    }
  }
}

// 解析是异步的，分几次刷新让状态自己变过来
function scheduleRefresh() {
  if (refreshTimer) clearTimeout(refreshTimer)
  ;[1500, 5000, 12000].forEach((ms) => {
    refreshTimer = setTimeout(() => loadKb(), ms)
  })
}

const docStatusLabel = (s) => ({ PENDING: '等待解析', PARSING: '解析中', EMBEDDING: '向量化中', READY: '已就绪', FAILED: '解析失败' }[s] || s)
const docStatusTag = (s) => ({ READY: 'success', FAILED: 'danger', PENDING: 'info', PARSING: 'warning', EMBEDDING: 'warning' }[s] || 'info')
const sourceLabel = (s) => ({ UPLOAD: '上传', GENERATED: '生成' }[s] || s || '—')

onMounted(() => loadKb())
</script>