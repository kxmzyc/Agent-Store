<template>
  <button class="chat-fab" title="AI 导购助手" @click="open = !open">
    <Bot size="22" />
  </button>
  <section v-if="open" class="chat-panel">
    <header>
      <div>
        <strong>AI 导购助手</strong>
        <small>工具调用 · 短期记忆 · 偏好记忆</small>
      </div>
      <button class="ghost" title="关闭" @click="open = false">×</button>
    </header>
    <div class="messages">
      <div v-for="(m, i) in messages" :key="i" class="bubble" :class="m.role">
        <div v-if="m.loading && !m.content" class="typing-state" aria-live="polite">
          <span class="typing-ring" aria-hidden="true"></span>
          <span>{{ m.status || '正在组织回复' }}</span>
          <span class="typing-dots" aria-hidden="true"><i></i><i></i><i></i></span>
        </div>
        <div v-else class="message-content" v-html="renderMarkdown(m.content)"></div>
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
import { reactive, ref } from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import { store } from '../store'

const open = ref(false)
const loading = ref(false)
const draft = ref('')
const sessionId = localStorage.getItem('agentSessionId') || crypto.randomUUID()
localStorage.setItem('agentSessionId', sessionId)
const md = new MarkdownIt({
  breaks: true,
  linkify: true
})
const messages = ref([
  { role: 'assistant', content: '可以帮你查商品、推荐商品，也能查询最近订单。' }
])

function renderMarkdown(text) {
  return DOMPurify.sanitize(md.render(text || ''))
}

function agentStreamUrl() {
  return `${window.location.origin}/agent/chat/stream`
}

function readSseEvents(buffer) {
  const events = []
  const parts = buffer.split('\n\n')
  const rest = parts.pop() || ''
  for (const part of parts) {
    const data = part
      .split('\n')
      .filter((line) => line.startsWith('data: '))
      .map((line) => line.slice(6))
      .join('\n')
    if (!data) continue
    events.push(JSON.parse(data))
  }
  return { events, rest }
}

async function send() {
  if (!draft.value.trim()) return
  const content = draft.value.trim()
  draft.value = ''
  if (!store.user?.id) {
    messages.value.push({ role: 'assistant', content: '请先登录后再使用 AI 导购助手，这样我才能读取你的偏好和订单。' })
    return
  }
  messages.value.push({ role: 'user', content })
  loading.value = true
  const assistantMessage = reactive({
    role: 'assistant',
    content: '',
    loading: true,
    status: '正在连接导购助手'
  })
  messages.value.push(assistantMessage)
  try {
    const response = await fetch(agentStreamUrl(), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        userId: store.user.id,
        sessionId,
        message: content
      })
    })
    if (!response.ok || !response.body) throw new Error(`HTTP ${response.status}`)

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const parsed = readSseEvents(buffer)
      buffer = parsed.rest
      for (const event of parsed.events) {
        if (event.type === 'token') {
          assistantMessage.loading = false
          assistantMessage.content += event.content || ''
        } else if (event.type === 'tool_start') {
          assistantMessage.status = toolStatusText(event.name)
        } else if (event.type === 'tool_end') {
          assistantMessage.status = '已查询真实数据，正在整理回复'
        } else if (event.type === 'done') {
          assistantMessage.loading = false
          assistantMessage.tools = event.toolsUsed || []
          if (!assistantMessage.content && event.reply) assistantMessage.content = event.reply
        } else if (event.type === 'error') {
          assistantMessage.loading = false
          assistantMessage.content = event.content || 'AI 服务暂时不可用，请稍后再试。'
        }
      }
    }
  } catch (error) {
    assistantMessage.loading = false
    assistantMessage.content = 'AI 服务暂时不可用，请稍后再试。'
  } finally {
    loading.value = false
  }
}

function toolStatusText(name) {
  if (name === 'search_products') return '正在检索真实商品和库存'
  if (name === 'query_order_status') return '正在查询你的订单状态'
  return '正在调用导购工具'
}
</script>
