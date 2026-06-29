import { reactive } from 'vue'

export const store = reactive({
  user: JSON.parse(localStorage.getItem('userInfo') || 'null'),
  token: localStorage.getItem('accessToken') || '',
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
