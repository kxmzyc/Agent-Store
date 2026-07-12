<template>
  <section class="panel auth-panel">
    <div class="page-head">
      <div>
        <h1>登录</h1>
        <p>登录后可以下单、查看订单和使用个人中心。</p>
      </div>
    </div>
    <form class="form" @submit.prevent="submit">
      <label>用户名
        <input v-model="form.username" required autocomplete="username" />
      </label>
      <label>密码
        <input v-model="form.password" required type="password" autocomplete="current-password" />
      </label>
      <label class="check-control auth-remember">
        <input v-model="rememberMe" type="checkbox" />
        <span>记住我</span>
      </label>
      <p v-if="error" class="error">{{ error }}</p>
      <button class="primary" :disabled="loading">{{ loading ? '登录中' : '登录' }}</button>
      <router-link to="/register">没有账号？去注册</router-link>
    </form>
  </section>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api, errorMessage } from '../api/http'
import { router } from '../router'
import { store } from '../store'

const route = useRoute()
const form = reactive({ username: localStorage.getItem('lastUsername') || 'alice', password: '123456' })
const error = ref('')
const loading = ref(false)
const rememberMe = ref(true)

async function submit() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await api.post('/auth/login', form)
    store.setAuth(data, rememberMe.value)
    const redirect = Array.isArray(route.query.redirect) ? route.query.redirect[0] : route.query.redirect
    router.push(redirect || '/')
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}
</script>
