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

// 单飞锁：并发 401 只触发一次刷新，其余请求复用同一个刷新 Promise
let refreshing = null

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const status = error.response?.status
    const original = error.config
    if (status === 401 && localStorage.getItem('refreshToken') && original && !original._retry) {
      original._retry = true
      try {
        if (!refreshing) {
          refreshing = axios
            .post('/api/auth/refresh', { refreshToken: localStorage.getItem('refreshToken') })
            .then((res) => {
              store.token = res.data.accessToken
              localStorage.setItem('accessToken', res.data.accessToken)
              return res.data.accessToken
            })
            .finally(() => { refreshing = null })
        }
        const token = await refreshing
        original.headers.Authorization = `Bearer ${token}`
        return api.request(original)
      } catch {
        store.logout()
        router.push('/login')
        return Promise.reject(error)
      }
    }
    if (status === 401) router.push('/login')
    return Promise.reject(error)
  }
)

export function errorMessage(error) {
  return error.response?.data?.msg || '操作失败，请稍后再试'
}
