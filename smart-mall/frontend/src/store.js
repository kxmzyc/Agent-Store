import { reactive } from 'vue'

function readUserInfo() {
  const raw = localStorage.getItem('userInfo')
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    localStorage.removeItem('userInfo')
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    return null
  }
}

const initialUser = readUserInfo()

export const store = reactive({
  user: initialUser,
  token: initialUser ? localStorage.getItem('accessToken') || '' : '',
  router: null,
  setAuth(data) {
    this.user = data.userInfo
    this.token = data.accessToken
    localStorage.setItem('accessToken', data.accessToken)
    localStorage.setItem('refreshToken', data.refreshToken)
    localStorage.setItem('userInfo', JSON.stringify(data.userInfo))
  },
  logout() {
    this.user = null
    this.token = ''
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('userInfo')
    this.router?.push('/login')
  }
})
