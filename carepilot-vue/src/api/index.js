import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { clearSession, getRefreshToken, saveSession, tokenExpired } from '../utils/token'

const http = axios.create({ baseURL: '/api', timeout: 120000 })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('cp_token')
  if (token) config.headers.Authorization = 'Bearer ' + token
  return config
})

// ---- 双令牌：访问令牌过期时用刷新令牌换新令牌，并把原请求重放一次 ----
let refreshing = null

export async function refreshAccessToken() {
  const rt = getRefreshToken()
  if (!rt) return null
  if (!refreshing) {
    refreshing = axios.post('/api/auth/refresh', { refreshToken: rt })
      .then((r) => {
        const body = r.data
        if (body && body.code === 0 && body.data && body.data.token) {
          saveSession({ token: body.data.token, refreshToken: body.data.refreshToken })
          // 同步到 store：聊天/工作台的 WebSocket 用的是 store 里的 token
          import('../stores/auth')
            .then((m) => { try { m.useAuthStore().token = body.data.token } catch (e) { /* 未初始化时忽略 */ } })
            .catch(() => {})
          return body.data.token
        }
        return null
      })
      .catch(() => null)
      .finally(() => { refreshing = null })
  }
  return refreshing
}

/** 拿到一个可用的 access token：有效就直接返回，过期就静默续期 */
export async function ensureFreshToken() {
  const cur = localStorage.getItem('cp_token')
  if (cur && !tokenExpired(cur)) return cur
  return (await refreshAccessToken()) || ''
}

function toLogin() {
  clearSession()
  if (router.currentRoute.value.path !== '/login') {
    ElMessage.warning('登录已过期，请重新登录')
    router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
  }
}

async function handleUnauthorized(config) {
  if (config && !config._retried) {
    const token = await refreshAccessToken()
    if (token) {
      config._retried = true
      config.headers = config.headers || {}
      config.headers.Authorization = 'Bearer ' + token
      return http(config)
    }
  }
  toLogin()
  return null
}

http.interceptors.response.use(
  async (resp) => {
    const body = resp.data
    if (body && typeof body.code === 'number') {
      if (body.code === 0) return body.data
      if (body.code === 40100) {
        const replayed = await handleUnauthorized(resp.config)
        if (replayed) return replayed
        return Promise.reject(body)
      }
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(body)
    }
    return body
  },
  async (err) => {
    const status = err?.response?.status
    if (status === 401) {
      const replayed = await handleUnauthorized(err.config)
      if (replayed) return replayed
      return Promise.reject(err)
    }
    if (status === 403) {
      ElMessage.error(err?.response?.data?.message || '当前账号无权访问该资源')
    } else {
      ElMessage.error(err?.response?.data?.message || err.message || '网络异常')
    }
    return Promise.reject(err)
  }
)

export default http

export const authApi = {
  login: (data) => http.post('/auth/login', data),
  register: (data) => http.post('/auth/register', data),
  me: () => http.get('/auth/me'),
}

export const walletApi = {
  mine: () => http.get('/wallet'),
  recharge: (amount) => http.post('/wallet/recharge', { amount }),
  ledger: (params) => http.get('/wallet/ledger', { params }),
}

export const chatApi = {
  createSession: () => http.post('/chat/sessions'),
  sessions: () => http.get('/chat/sessions'),
  messages: (id, limit = 100) => http.get('/chat/sessions/' + id + '/messages', { params: { limit } }),
  context: (id) => http.get('/chat/sessions/' + id + '/context'),
  close: (id) => http.post('/chat/sessions/' + id + '/close'),
  remove: (id) => http.delete('/chat/sessions/' + id),
  transfer: (id, reasonCode = 'OTHER') => http.post('/chat/sessions/' + id + '/transfer', null, { params: { reasonCode } }),
  pendingApprovals: (id) => http.get('/chat/approvals/sessions/' + id + '/pending'),
  reopen: (id) => http.post('/chat/sessions/' + id + '/reopen'),
  endAgent: (id) => http.post('/chat/sessions/' + id + '/end-agent'),
  cancelTransfer: (id) => http.post('/chat/sessions/' + id + '/transfer/cancel'),
  approve: (no) => http.post('/chat/approvals/' + no + '/approve'),
  reject: (no) => http.post('/chat/approvals/' + no + '/reject'),
}

export const workbenchApi = {
  heartbeat: () => http.post('/workbench/heartbeat'),
  waiting: () => http.get('/workbench/sessions/waiting'),
  mine: () => http.get('/workbench/sessions/mine'),
  accept: (id) => http.post('/workbench/sessions/' + id + '/accept'),
  summary: (id) => http.get('/workbench/sessions/' + id + '/summary'),
  close: (id) => http.post('/workbench/sessions/' + id + '/close'),
  messages: (id) => http.get('/workbench/sessions/' + id + '/messages'),
  reply: (id, content) => http.post('/workbench/sessions/' + id + '/messages', { content }),
  tickets: (params) => http.get('/workbench/tickets', { params }),
  transit: (id, action, remark) => http.post('/workbench/tickets/' + id + '/' + action, null, { params: remark ? { remark } : {} }),
  logs: (id) => http.get('/workbench/tickets/' + id + '/logs'),
}

export const mallApi = {
  products: (params) => http.get('/mall/products', { params }),
  product: (id) => http.get('/mall/products/' + id),
  categories: () => http.get('/mall/categories'),
  cart: () => http.get('/mall/cart'),
  addCart: (productId, quantity = 1) => http.post('/mall/cart', { productId, quantity }),
  updateCart: (id, quantity) => http.put('/mall/cart/' + id, { quantity }),
  removeCart: (id) => http.delete('/mall/cart/' + id),
  addresses: () => http.get('/mall/addresses'),
  addAddress: (data) => http.post('/mall/addresses', data),
  updateAddress: (id, data) => http.put('/mall/addresses/' + id, data),
  removeAddress: (id) => http.delete('/mall/addresses/' + id),
  setDefaultAddress: (id) => http.post('/mall/addresses/' + id + '/default'),
}

export const orderApi = {
  create: (data, idempotencyKey) => http.post('/orders', data, idempotencyKey ? { headers: { 'Idempotency-Key': idempotencyKey } } : {}),
  list: (params) => http.get('/orders', { params }),
  detail: (orderNo) => http.get('/orders/' + orderNo),
  pay: (orderNo, channel) => http.post('/orders/' + orderNo + '/pay', { channel }),
  payments: (orderNo) => http.get('/orders/' + orderNo + '/payments'),
  cancel: (orderNo) => http.post('/orders/' + orderNo + '/cancel'),
  receive: (orderNo) => http.post('/orders/' + orderNo + '/receive'),
  adminList: (params) => http.get('/admin/orders', { params }),
  ship: (orderNo, data) => http.post('/admin/orders/' + orderNo + '/ship', data),
}

export const adminUserApi = {
  list: (params) => http.get('/admin/users', { params }),
  create: (data) => http.post('/admin/users', data),
  update: (id, data) => http.put('/admin/users/' + id, data),
  toggle: (id) => http.post('/admin/users/' + id + '/status'),
  resetPassword: (id, password) => http.post('/admin/users/' + id + '/password', { password }),
}

export const adminProductApi = {
  list: (keyword) => http.get('/admin/products', { params: keyword ? { keyword } : {} }),
  create: (data) => http.post('/admin/products', data),
  update: (id, data) => http.put('/admin/products/' + id, data),
  toggle: (id) => http.post('/admin/products/' + id + '/status'),
  remove: (id) => http.delete('/admin/products/' + id),
  uploadImage: (file) => {
    const fd = new FormData()
    fd.append('file', file)
    return http.post('/admin/products/image', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
  },
  uploadCover: (id, file) => {
    const fd = new FormData()
    fd.append('file', file)
    return http.post('/admin/products/' + id + '/cover', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
  },
  uploadGallery: (id, file) => {
    const fd = new FormData()
    fd.append('file', file)
    return http.post('/admin/products/' + id + '/gallery', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
  },
  removeGallery: (id, url) => http.delete('/admin/products/' + id + '/gallery', { params: { url } }),
}

export const kbApi = {
  documents: (params) => http.get('/admin/kb/documents', { params }),
  stats: () => http.get('/admin/kb/stats'),
  failures: (params) => http.get('/admin/kb/failures', { params }),
  retryFailure: (id) => http.post('/admin/kb/failures/' + id + '/retry'),
  reingest: (id) => http.post('/admin/kb/documents/' + id + '/reingest'),
  remove: (id) => http.delete('/admin/kb/documents/' + id),
  file: (id) => http.get('/admin/kb/documents/' + id + '/file', { responseType: 'blob' }),
  text: (id) => http.get('/admin/kb/documents/' + id + '/text'),
  reindex: () => http.post('/admin/kb/reindex'),
  upload: (formData) => http.post('/admin/kb/documents', formData, { headers: { 'Content-Type': 'multipart/form-data' } }),
}