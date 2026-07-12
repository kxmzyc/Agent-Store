<template>
  <section class="panel auth-panel">
    <div class="page-head">
      <div>
        <h1>注册</h1>
        <p>用户名唯一，密码至少 6 位。</p>
      </div>
    </div>
    <form class="form" @submit.prevent="submit">
      <label>用户名
        <input v-model="form.username" required />
      </label>
      <label>手机号
        <input v-model="form.phone" />
      </label>
      <label>密码
        <input v-model="form.password" required type="password" minlength="6" />
      </label>
      <p v-if="error" class="error">{{ error }}</p>
      <button class="primary" :disabled="loading">{{ loading ? '提交中' : '创建账号' }}</button>
      <router-link to="/login">已有账号？去登录</router-link>
    </form>
  </section>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { api, errorMessage } from '../api/http'
import { router } from '../router'

const form = reactive({ username: '', phone: '', password: '' })
const error = ref('')
const loading = ref(false)

async function submit() {
  loading.value = true
  error.value = ''
  try {
    await api.post('/auth/register', form)
    router.push('/login')
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}
</script>
