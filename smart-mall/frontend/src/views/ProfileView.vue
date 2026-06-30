<template>
  <section class="panel profile-card">
    <div class="page-head">
      <div>
        <h1>个人中心</h1>
        <p>维护手机号和头像地址，登录状态刷新后保持。</p>
      </div>
    </div>
    <form class="form" @submit.prevent="save">
      <label>用户名
        <input :value="profile.username" disabled />
      </label>
      <label>手机号
        <input v-model="profile.phone" />
      </label>
      <label>头像 URL
        <input v-model="profile.avatarUrl" />
      </label>
      <label>创建时间
        <input :value="profile.createdAt" disabled />
      </label>
      <p v-if="message" :class="messageType">{{ message }}</p>
      <div class="profile-action-swap">
        <span class="profile-edit-label">编辑资料</span>
        <button class="profile-save-btn">保存</button>
      </div>
    </form>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { api, errorMessage } from '../api/http'
import { store } from '../store'
import { useToast } from '../composables/useToast'

const profile = reactive({ username: '', phone: '', avatarUrl: '', createdAt: '' })
const message = ref('')
const messageType = ref('muted')
const toast = useToast()

onMounted(load)

async function load() {
  const { data } = await api.get('/user/profile')
  Object.assign(profile, data)
}

async function save() {
  try {
    const { data } = await api.put('/user/profile', { phone: profile.phone, avatarUrl: profile.avatarUrl })
    Object.assign(profile, data)
    store.user = data
    localStorage.setItem('userInfo', JSON.stringify(data))
    messageType.value = 'muted'
    message.value = '已保存'
    toast.show('个人资料已保存')
  } catch (e) {
    messageType.value = 'error'
    message.value = errorMessage(e)
    toast.show(message.value)
  }
}
</script>
