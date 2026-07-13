<template>
  <section
    class="agent-conversation"
    :class="[`agent-conversation--${mode}`, { 'has-task': agentTask }]"
  >
    <div v-if="showMemory" class="agent-memory-bar">
      <div v-if="store.user?.id" class="agent-memory" aria-label="已记住的购物偏好">
        <template v-if="preferenceTags.length">
          <span class="agent-memory-label">已记住</span>
          <span v-for="tag in preferenceTags" :key="tag" class="agent-memory-tag">{{ tag }}</span>
        </template>
        <span v-else class="agent-memory-empty">告诉我你的偏好，我会记住。</span>
      </div>
      <span v-else class="agent-memory-empty">登录后可记住你的偏好</span>
      <label class="agent-llm-toggle" title="关闭时使用规则引擎；打开后可调用已配置的大模型接口">
        <input v-model="useLlm" type="checkbox" />
        <span>大模型 API</span>
      </label>
      <button v-if="store.user?.id" class="agent-memory-manage" type="button" @click="router.push('/profile')">管理偏好</button>
      <span v-if="historyLoading" class="agent-history-status">恢复历史中...</span>
    </div>

    <section v-if="agentTask" class="agent-task-card" :class="[`is-${agentTask.stage}`, { 'is-collapsed': taskDetailsCollapsed }]" aria-label="当前导购任务">
      <div v-if="taskDetailsCollapsed" class="agent-task-collapsed">
        <div>
          <span class="agent-task-kicker">{{ taskTypeLabel(agentTask.type) }}</span>
          <strong>本次已忽略长期偏好</strong>
        </div>
        <button type="button" @click="taskDetailsCollapsed = false">显示确认</button>
      </div>
      <template v-else>
      <div class="agent-task-heading">
        <div>
          <span class="agent-task-kicker">{{ taskTypeLabel(agentTask.type) }}</span>
          <strong>{{ agentTask.title }}</strong>
        </div>
        <span class="agent-task-stage">{{ taskStageLabel(agentTask.stage) }}</span>
      </div>
      <ol v-if="agentTask.steps?.length" class="agent-task-steps">
        <li v-for="step in agentTask.steps" :key="step.key" :class="`is-${step.status}`">
          <i aria-hidden="true"></i><span>{{ step.label }}</span>
        </li>
      </ol>
      <p>{{ agentTask.summary }}</p>
      <div class="agent-task-controls">
        <label class="agent-preference-switch">
          <input v-model="ignorePreferences" type="checkbox" @change="collapseTaskIfIgnoringPreferences" />
          <span>本次不使用长期偏好</span>
        </label>
        <button v-if="agentTask.canExpand && mode === 'compact'" type="button" @click="$emit('expand')">展开工作台</button>
        <button v-if="agentTask.canExpand && mode !== 'page'" type="button" @click="$emit('open-full')">进入完整方案页</button>
      </div>
      </template>
    </section>

    <div ref="messageList" class="agent-message-list">
      <div v-for="(message, index) in messages" :key="index" class="bubble" :class="message.role">
        <div v-if="message.loading && !message.content" class="typing-state" aria-live="polite">
          <span class="typing-ring" aria-hidden="true"></span>
          <span>{{ message.status || '正在组织回复' }}</span>
          <span class="typing-dots" aria-hidden="true"><i></i><i></i><i></i></span>
        </div>
        <div v-else class="message-content" v-html="renderMarkdown(message.content)"></div>
        <div v-if="message.role === 'assistant' && message.ui?.source?.productsQueried" class="chat-source-note">
          已查询真实商品和库存
        </div>
        <div v-if="message.role === 'assistant' && message.ui?.modelUsed" class="chat-source-note is-model">
          已接入大模型 API
        </div>
        <div v-if="message.role === 'assistant' && message.ui?.productCards?.length" class="chat-product-rail" aria-label="真实商品推荐">
          <article v-for="product in message.ui.productCards" :key="product.id" class="chat-product-card">
            <router-link class="chat-product-thumb" :to="`/products/${product.id}`" :aria-label="`查看${product.name}详情`">
              <img v-if="product.imageUrl && !failedImageIds.has(product.id)" :src="product.imageUrl" :alt="product.name" @error="markImageFailed(product.id)" />
              <span v-else class="chat-product-placeholder" aria-hidden="true">暂无图片</span>
            </router-link>
            <div class="chat-product-body">
              <p class="chat-product-name">{{ product.name }}</p>
              <p v-if="product.reason" class="chat-product-reason">{{ product.reason }}</p>
              <div class="chat-product-meta">
                <strong>¥{{ formatPrice(product.price) }}</strong>
                <span :class="{ 'is-sold-out': isSoldOut(product) }">{{ isSoldOut(product) ? '已售罄' : `库存 ${product.stock}` }}</span>
              </div>
              <div class="chat-product-actions">
                <router-link :to="`/products/${product.id}`">查看详情</router-link>
                <button type="button" :disabled="isSoldOut(product)" @click="requestCardAdd(message, product)">
                  {{ isSoldOut(product) ? '暂不可加购' : needsCartConfirmation(message, product) ? '确认加入' : '加入购物车' }}
                </button>
              </div>
            </div>
          </article>
        </div>
        <div v-if="displayedTools(message.tools).length" class="tool-badges" aria-label="本次已调用的功能">
          <span v-for="tool in displayedTools(message.tools)" :key="tool">已调用：{{ toolLabel(tool) }}</span>
        </div>
        <button v-if="message.error && message.retryPrompt" type="button" class="chat-retry" @click="retryMessage(message.retryPrompt)">重试</button>
      </div>
    </div>

    <div class="chat-quick-actions agent-quick-actions">
      <button v-for="action in quickActions" :key="action.key" type="button" @click="runSuggestedAction(action)">{{ action.label }}</button>
    </div>
    <form class="agent-composer" @submit.prevent="send">
      <input v-model="draft" placeholder="问我：有没有适合敲代码的键盘，预算500" aria-label="发送给 AI 导购助手" />
      <button class="icon-btn primary" title="发送" :disabled="loading || !draft.trim()"><Send size="18" /></button>
    </form>
  </section>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import { router } from '../router'
import { store } from '../store'
import { useAgentConversation } from '../composables/useAgentConversation'

defineEmits(['expand', 'open-full'])

defineProps({
  mode: { type: String, default: 'compact' },
  showMemory: { type: Boolean, default: true }
})

const {
  agentTask,
  draft,
  historyLoading,
  ignorePreferences,
  loading,
  messages,
  preferenceTags,
  quickActions,
  retryMessage,
  runSuggestedAction,
  send,
  useLlm,
  usePrompt
} = useAgentConversation()

const messageList = ref(null)
const failedImageIds = ref(new Set())
const taskDetailsCollapsed = ref(false)
const md = new MarkdownIt({ breaks: true, linkify: true })

watch(messages, scrollToLatest, { deep: true, flush: 'post' })
const canCollapseTask = computed(() => Boolean(agentTask.value))

watch([ignorePreferences, agentTask], ([ignored, task]) => {
  if (ignored && task) taskDetailsCollapsed.value = true
  if (!task) taskDetailsCollapsed.value = false
}, { immediate: true })

function collapseTaskIfIgnoringPreferences() {
  if (ignorePreferences.value && canCollapseTask.value) taskDetailsCollapsed.value = true
}

function renderMarkdown(text) {
  const template = document.createElement('template')
  template.innerHTML = DOMPurify.sanitize(md.render(text || ''))
  decorateMarkdownTables(template.content)
  return template.innerHTML
}

function decorateMarkdownTables(content) {
  content.querySelectorAll('table').forEach((table) => {
    const headers = Array.from(table.querySelectorAll('thead th')).map((cell) => cell.textContent.trim())
    const indexColumn = headers.findIndex((value) => /^(序号|编号|#)$/.test(value))
    const productColumn = headers.findIndex((value) => /商品|名称/.test(value))
    const valueColumns = headers.map((value, index) => /^(价格|售价|预算|库存|销量|数量|合计|小计)$/.test(value) ? index : -1).filter((index) => index >= 0)
    table.querySelectorAll('tr').forEach((row) => {
      Array.from(row.children).forEach((cell, index) => {
        const value = cell.textContent.trim()
        if (index === indexColumn) cell.classList.add('table-index')
        if (index === productColumn) cell.classList.add('table-product')
        if (valueColumns.includes(index) || /^(?:[¥￥]\s?[\d,]+(?:\.\d{1,2})?|(?:库存|销量|数量)\s*[:：]?\s*\d+|\d+)$/.test(value)) cell.classList.add('table-value')
      })
    })
    const wrapper = document.createElement('div')
    wrapper.className = 'markdown-table-scroll'
    table.replaceWith(wrapper)
    wrapper.append(table)
  })
}

function scrollToLatest() {
  nextTick(() => {
    if (messageList.value) messageList.value.scrollTop = messageList.value.scrollHeight
  })
}

function formatPrice(value) {
  return Number(value || 0).toFixed(2)
}

function isSoldOut(product) {
  return product.status !== 'IN_STOCK' || Number(product.stock) <= 0
}

function markImageFailed(productId) {
  failedImageIds.value = new Set([...failedImageIds.value, productId])
}

function needsCartConfirmation(message, product) {
  return Number(message.ui?.pendingCart?.productId) === Number(product.id)
}

function requestCardAdd(message, product) {
  if (isSoldOut(product)) return
  usePrompt(needsCartConfirmation(message, product) ? '确认' : `把商品 ID ${product.id} 加入购物车`)
}

function displayedTools(tools) {
  return [...new Set(tools || [])]
}

function toolLabel(name) {
  if (name === 'search_products') return '商品搜索'
  if (name === 'get_product_detail') return '商品详情'
  if (name === 'compare_products') return '商品对比'
  if (name === 'recommend_by_preference') return '偏好推荐'
  if (name === 'build_purchase_plan') return '购买方案'
  if (name === 'search_knowledge_base') return '商品知识参考'
  if (name === 'add_to_cart') return '加入购物车'
  if (name === 'query_order_status') return '订单查询'
  return '导购服务'
}

function taskTypeLabel(type) {
  return ({ purchase_plan: '导购方案', product_search: '真实商品', comparison: '商品比较', order_query: '订单查询', cart_confirmation: '安全确认' })[type] || 'AI 导购'
}

function taskStageLabel(stage) {
  return ({ clarifying: '补充信息', searching: '查询中', ready: '方案就绪', awaiting_confirmation: '等待确认', completed: '已完成' })[stage] || '进行中'
}
</script>
