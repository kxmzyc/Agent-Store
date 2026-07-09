import { reactive } from 'vue'

function authStorage() {
  return sessionStorage.getItem('accessToken') ? sessionStorage : localStorage
}

function readUserInfo() {
  const storage = authStorage()
  const raw = storage.getItem('userInfo')
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    clearAuthStorage()
    return null
  }
}

function clearAuthStorage() {
  for (const storage of [localStorage, sessionStorage]) {
    storage.removeItem('userInfo')
    storage.removeItem('accessToken')
    storage.removeItem('refreshToken')
  }
}

const initialUser = readUserInfo()

export const store = reactive({
  user: initialUser,
  token: initialUser ? authStorage().getItem('accessToken') || '' : '',
  cartItemCount: 0,
  router: null,
  setAuth(data, rememberMe = true) {
    clearAuthStorage()
    const storage = rememberMe ? localStorage : sessionStorage
    this.user = data.userInfo
    this.token = data.accessToken
    storage.setItem('accessToken', data.accessToken)
    storage.setItem('refreshToken', data.refreshToken)
    storage.setItem('userInfo', JSON.stringify(data.userInfo))
    localStorage.setItem('lastUsername', data.userInfo.username)
  },
  setAccessToken(token) {
    this.token = token
    authStorage().setItem('accessToken', token)
  },
  refreshToken() {
    return authStorage().getItem('refreshToken') || ''
  },
  persistUser() {
    if (!this.user) return
    authStorage().setItem('userInfo', JSON.stringify(this.user))
  },
  setCartCount(count) {
    this.cartItemCount = Math.max(0, Number(count) || 0)
  },
  clearAuth() {
    this.user = null
    this.token = ''
    this.cartItemCount = 0
    clearAuthStorage()
  },
  logout() {
    this.clearAuth()
    this.router?.push('/login')
  }
})
