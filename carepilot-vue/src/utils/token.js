/** 解析 JWT 的 exp，判断令牌是否已过期（不校验签名，签名由后端负责） */
export function tokenExpired(token) {
  if (!token) return true
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
    if (!payload.exp) return true
    return Date.now() >= payload.exp * 1000 - 5000
  } catch (e) {
    return true
  }
}

export const ACCESS_KEY = 'cp_token'
export const REFRESH_KEY = 'cp_refresh'

export function getAccessToken() {
  return localStorage.getItem(ACCESS_KEY) || ''
}

export function getRefreshToken() {
  return localStorage.getItem(REFRESH_KEY) || ''
}

export function saveSession({ token, refreshToken, user }) {
  if (token) localStorage.setItem(ACCESS_KEY, token)
  if (refreshToken) localStorage.setItem(REFRESH_KEY, refreshToken)
  if (user) localStorage.setItem('cp_user', JSON.stringify(user))
}

export function clearSession() {
  localStorage.removeItem(ACCESS_KEY)
  localStorage.removeItem(REFRESH_KEY)
  localStorage.removeItem('cp_user')
}
