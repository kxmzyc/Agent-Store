import axios from 'axios'
import { router } from '../router'
import { store } from '../store'

export const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

export const agentApi = axios.create({
  baseURL: '/agent',
  timeout: 120000
})

api.interceptors.request.use((config) => {
  if (store.token) config.headers.Authorization = `Bearer ${store.token}`
  return config
})

agentApi.interceptors.request.use((config) => {
  if (store.token) config.headers.Authorization = `Bearer ${store.token}`
  return config
})

// 单飞锁：并发 401 只触发一次刷新，其余请求复用同一个刷新 Promise
let refreshing = null

function loginRedirect() {
  const current = router.currentRoute.value
  const redirect = current?.fullPath && current.path !== '/login' ? current.fullPath : '/'
  router.push({ path: '/login', query: { redirect } })
}

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const status = error.response?.status
    const original = error.config
    const refreshToken = store.refreshToken()
    if (status === 401 && refreshToken && original && !original._retry) {
      original._retry = true
      try {
        if (!refreshing) {
          refreshing = axios
            .post('/api/auth/refresh', { refreshToken })
            .then((res) => {
              store.setAccessToken(res.data.accessToken)
              return res.data.accessToken
            })
            .finally(() => { refreshing = null })
        }
        const token = await refreshing
        original.headers.Authorization = `Bearer ${token}`
        return api.request(original)
      } catch {
        store.clearAuth()
        loginRedirect()
        return Promise.reject(error)
      }
    }
    if (status === 401) {
      store.clearAuth()
      loginRedirect()
    }
    return Promise.reject(error)
  }
)

export function errorMessage(error) {
  return error.response?.data?.msg || '操作失败，请稍后再试'
}

export async function refreshCartCount() {
  if (!store.token) {
    store.setCartCount(0)
    return 0
  }
  try {
    const { data } = await api.get('/cart')
    const count = data.reduce((sum, item) => sum + Number(item.quantity || 0), 0)
    store.setCartCount(count)
    return count
  } catch {
    store.setCartCount(0)
    return 0
  }
}
