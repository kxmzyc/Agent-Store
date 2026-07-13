import { computed, reactive, ref } from 'vue'
import { agentApi, refreshCartCount } from '../api/http'
import { router } from '../router'
import { store } from '../store'

const welcomeMessage = { role: 'assistant', content: '可以帮你查商品、推荐商品，也能查询最近订单。' }
const messages = ref([welcomeMessage])
const draft = ref('')
const loading = ref(false)
const historyLoading = ref(false)
const preferenceTags = ref([])
const ignorePreferences = ref(false)
const useLlm = ref(false)
const loadedSessions = ref([])
const sessionId = ref(localStorage.getItem('agentSessionId') || crypto.randomUUID())

localStorage.setItem('agentSessionId', sessionId.value)

const defaultQuickActions = [{
  key: 'preference-recommend',
  label: '偏好推荐',
  prompt: '按我的偏好推荐几款商品'
}]

const latestAssistantMessage = computed(() => [...messages.value].reverse().find((message) => message.role === 'assistant'))
const quickActions = computed(() => latestAssistantMessage.value?.ui?.suggestedActions?.length
  ? latestAssistantMessage.value.ui.suggestedActions
  : defaultQuickActions)
const agentTask = computed(() => latestAssistantMessage.value?.ui?.agentTask || null)
const isComplexTask = computed(() => ['purchase_plan', 'comparison', 'cart_confirmation'].includes(agentTask.value?.type))
const fabStatus = computed(() => {
  if (loading.value) return { key: 'working', label: '正在查询' }
  if (agentTask.value?.stage === 'awaiting_confirmation') return { key: 'confirm', label: '待确认' }
  if (agentTask.value?.type === 'purchase_plan' && agentTask.value?.stage !== 'completed') return { key: 'planning', label: '方案中' }
  return { key: 'idle', label: 'AI 导购' }
})

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

async function loadPreferences() {
  if (!store.user?.id) {
    preferenceTags.value = []
    return
  }
  try {
    const { data } = await agentApi.get('/preferences')
    preferenceTags.value = data.map((item) => item.tag).filter(Boolean).slice(0, 3)
  } catch {
    preferenceTags.value = []
  }
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

async function ensureLoaded() {
  await Promise.all([restoreHistory(), loadPreferences()])
}

function newSession() {
  sessionId.value = crypto.randomUUID()
  localStorage.setItem('agentSessionId', sessionId.value)
  // This is already the live source of truth. Do not replace it with the
  // text-only history endpoint when the compact window changes into a workbench.
  loadedSessions.value.push(sessionId.value)
  messages.value = [{ role: 'assistant', content: '已开启新会话。我会继续读取你的长期偏好来推荐商品。' }]
  ignorePreferences.value = false
  loadPreferences()
}

async function send() {
  if (!draft.value.trim() || loading.value) return
  if (!loadedSessions.value.includes(sessionId.value)) loadedSessions.value.push(sessionId.value)
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
    status: '正在连接导购助手',
    retryPrompt: content
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
        message: content,
        ignorePreferences: ignorePreferences.value,
        useLlm: useLlm.value
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
          assistantMessage.ui = event.ui || null
          if (!assistantMessage.content && event.reply) assistantMessage.content = event.reply
          if (assistantMessage.tools.includes('add_to_cart')) refreshCartCount()
          loadPreferences()
        } else if (event.type === 'error') {
          assistantMessage.loading = false
          assistantMessage.content = event.content || 'AI 服务暂时不可用，请稍后再试。'
          assistantMessage.error = true
        }
      }
    }
  } catch {
    assistantMessage.loading = false
    assistantMessage.content = 'AI 服务暂时不可用，请稍后再试。'
    assistantMessage.error = true
  } finally {
    loading.value = false
  }
}

function usePrompt(text) {
  draft.value = text
  if (!loading.value) send()
}

function retryMessage(prompt) {
  if (!loading.value) usePrompt(prompt)
}

function runSuggestedAction(action) {
  if (action.action === 'navigate_orders') return router.push('/orders')
  if (action.action === 'navigate_products') return router.push('/')
  if (action.action === 'navigate_cart') return router.push('/cart')
  if (action.action === 'view_product' && action.productId) return router.push(`/products/${action.productId}`)
  if (action.action === 'edit_plan_budget') {
    draft.value = '把这套方案的预算调整为 '
    return
  }
  if (action.prompt) usePrompt(action.prompt)
}

function toolStatusText(name) {
  if (name === 'search_products') return '正在检索真实商品和库存'
  if (name === 'get_product_detail') return '正在读取商品详情'
  if (name === 'compare_products') return '正在对比商品信息'
  if (name === 'recommend_by_preference') return '正在读取你的偏好并推荐'
  if (name === 'build_purchase_plan') return '正在生成真实购买方案'
  if (name === 'add_to_cart') return '正在加入购物车'
  if (name === 'query_order_status') return '正在查询你的订单状态'
  return '正在调用导购工具'
}

export function useAgentConversation() {
  return {
    agentTask,
    draft,
    ensureLoaded,
    fabStatus,
    historyLoading,
    ignorePreferences,
    isComplexTask,
    latestAssistantMessage,
    loading,
    messages,
    newSession,
    preferenceTags,
    quickActions,
    retryMessage,
    runSuggestedAction,
    send,
    sessionId,
    useLlm,
    usePrompt
  }
}
