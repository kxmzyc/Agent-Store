<template>
  <button class="chat-fab" title="AI 导购助手" @click="toggleOpen">
    <Bot size="22" />
  </button>
  <section v-if="open" class="chat-panel">
    <header>
      <div>
        <strong>AI 导购助手</strong>
        <small>真实商品 · 订单查询 · 偏好记忆</small>
      </div>
      <div class="chat-header-actions">
        <button class="ghost" title="新会话" @click="newSession">新会话</button>
        <button class="ghost" title="关闭" @click="open = false">×</button>
      </div>
    </header>
    <div class="chat-session-bar">
      <span>session {{ sessionLabel }}</span>
      <span v-if="historyLoading">恢复历史中...</span>
    </div>
    <div class="messages">
      <div v-for="(m, i) in messages" :key="i" class="bubble" :class="m.role">
        <div v-if="m.loading && !m.content" class="typing-state" aria-live="polite">
          <span class="typing-ring" aria-hidden="true"></span>
          <span>{{ m.status || '正在组织回复' }}</span>
          <span class="typing-dots" aria-hidden="true"><i></i><i></i><i></i></span>
        </div>
        <div v-else class="message-content" v-html="renderMarkdown(m.content)"></div>
        <div v-if="displayedTools(m.tools).length" class="tool-badges" aria-label="本次已调用的功能">
          <span v-for="tool in displayedTools(m.tools)" :key="tool">已调用：{{ toolLabel(tool) }}</span>
        </div>
      </div>
    </div>
    <div class="chat-quick-actions">
      <button type="button" @click="usePrompt('我喜欢机械键盘，预算不要太高')">记住偏好</button>
      <button type="button" @click="usePrompt('按我的偏好推荐几款商品')">偏好推荐</button>
      <button type="button" @click="usePrompt('我上次买的东西到哪了')">查订单</button>
      <button type="button" @click="usePrompt('对比商品 ID 1 和 ID 2 哪个更适合宿舍敲代码')">商品对比</button>
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
import { computed, reactive, ref } from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import { store } from '../store'
import { agentApi } from '../api/http'

const open = ref(false)
const loading = ref(false)
const historyLoading = ref(false)
const draft = ref('')
const sessionId = ref(localStorage.getItem('agentSessionId') || crypto.randomUUID())
localStorage.setItem('agentSessionId', sessionId.value)
const md = new MarkdownIt({
  breaks: true,
  linkify: true
})
const welcomeMessage = { role: 'assistant', content: '可以帮你查商品、推荐商品，也能查询最近订单。' }
const messages = ref([welcomeMessage])
const loadedSessions = ref([])

const sessionLabel = computed(() => sessionId.value.slice(0, 8))

function renderMarkdown(text) {
  return DOMPurify.sanitize(md.render(text || ''))
}

function agentStreamUrl() {
  return `${window.location.origin}/agent/chat/stream`
}

function usePrompt(text) {
  draft.value = text
  if (!loading.value) send()
}

async function toggleOpen() {
  open.value = !open.value
  if (open.value) await restoreHistory()
}

async function restoreHistory() {
  if (!store.user?.id || loadedSessions.value.includes(sessionId.value)) return
  historyLoading.value = true
  try {
    const { data } = await agentApi.get('/history', { params: { sessionId: sessionId.value } })
    if (data.length) messages.value = data
    loadedSessions.value.push(sessionId.value)
  } catch {
    messages.value.push({ role: 'assistant', content: '历史记录暂时无法恢复，但可以继续开始新的导购对话。' })
  } finally {
    historyLoading.value = false
  }
}

function newSession() {
  sessionId.value = crypto.randomUUID()
  localStorage.setItem('agentSessionId', sessionId.value)
  messages.value = [
    { role: 'assistant', content: '已开启新会话。我会继续读取你的长期偏好来推荐商品。' }
  ]
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
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${store.token}`
      },
      body: JSON.stringify({
        sessionId: sessionId.value,
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
  if (name === 'get_product_detail') return '正在读取商品详情'
  if (name === 'compare_products') return '正在对比商品信息'
  if (name === 'recommend_by_preference') return '正在读取你的偏好并推荐'
  if (name === 'add_to_cart') return '正在加入购物车'
  if (name === 'query_order_status') return '正在查询你的订单状态'
  return '正在调用导购工具'
}

function toolLabel(name) {
  if (name === 'search_products') return '商品搜索'
  if (name === 'get_product_detail') return '商品详情'
  if (name === 'compare_products') return '商品对比'
  if (name === 'recommend_by_preference') return '偏好推荐'
  if (name === 'add_to_cart') return '加入购物车'
  if (name === 'query_order_status') return '订单查询'
  return name
}

function displayedTools(tools) {
  return [...new Set(tools || [])]
}
</script>
