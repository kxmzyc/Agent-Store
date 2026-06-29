import axios from 'axios'
import { router } from '../router'
import { store } from '../store'

export const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

export const agentApi = axios.create({
  baseURL: '/agent',
  timeout: 20000
})

api.interceptors.request.use((config) => {
  if (store.token) config.headers.Authorization = `Bearer ${store.token}`
  return config
})

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const status = error.response?.status
    if (status === 401 && localStorage.getItem('refreshToken')) {
      try {
        const refresh = await axios.post('/api/auth/refresh', {
          refreshToken: localStorage.getItem('refreshToken')
        })
        store.token = refresh.data.accessToken
        localStorage.setItem('accessToken', refresh.data.accessToken)
        error.config.headers.Authorization = `Bearer ${refresh.data.accessToken}`
        return api.request(error.config)
      } catch {
        store.logout()
      }
    }
    if (status === 401) router.push('/login')
    return Promise.reject(error)
  }
)

export function errorMessage(error) {
  return error.response?.data?.msg || '操作失败，请稍后再试'
}
