<template>
  <button class="chat-fab" title="AI导购助手" @click="open = !open">
    <Bot size="22" />
  </button>
  <section v-if="open" class="chat-panel">
    <header>
      <div>
        <strong>AI 导购助手</strong>
        <small>工具调用 · 短期记忆 · 偏好记忆</small>
      </div>
      <button class="ghost" @click="open = false">×</button>
    </header>
    <div class="messages">
      <div v-for="(m, i) in messages" :key="i" class="bubble" :class="m.role">
        {{ m.content }}
        <small v-if="m.tools?.length">tools: {{ m.tools.join(', ') }}</small>
      </div>
    </div>
    <form @submit.prevent="send">
      <input v-model="draft" placeholder="问我：有没有适合敲代码的键盘，预算500" />
      <button class="icon-btn primary" title="发送" :disabled="loading || !draft.trim()">
        <Send size="18" />
      </button>
    </form>
  </section>
</template>

<script setup>
import { ref } from 'vue'
import { agentApi } from '../api/http'
import { store } from '../store'

const open = ref(false)
const loading = ref(false)
const draft = ref('')
const sessionId = localStorage.getItem('agentSessionId') || crypto.randomUUID()
localStorage.setItem('agentSessionId', sessionId)
const messages = ref([
  { role: 'assistant', content: '可以帮你查商品、推荐商品，也能查询最近订单。' }
])

async function send() {
  if (!draft.value.trim()) return
  const content = draft.value.trim()
  draft.value = ''
  messages.value.push({ role: 'user', content })
  loading.value = true
  try {
    const { data } = await agentApi.post('/chat', {
      userId: store.user?.id || 2,
      sessionId,
      message: content
    })
    messages.value.push({ role: 'assistant', content: data.reply, tools: data.toolsUsed })
  } catch (error) {
    messages.value.push({ role: 'assistant', content: 'AI服务暂时不可用，请稍后再试。' })
  } finally {
    loading.value = false
  }
}
</script>
