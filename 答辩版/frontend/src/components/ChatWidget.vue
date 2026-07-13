<template>
  <template v-if="!isGuidePage">
    <button
      class="agent-fab"
      :class="[`is-${fabStatus.key}`, { 'is-dragging': dragging }]"
      :style="fabStyle"
      :title="fabStatus.label"
      type="button"
      @click="toggleOpen"
      @pointerdown="startDrag"
    >
      <Bot size="21" />
      <span class="agent-fab-label">{{ fabStatus.label }}</span>
      <i v-if="fabStatus.key !== 'idle'" aria-hidden="true"></i>
    </button>

    <section v-if="open && !workbenchOpen" class="chat-panel agent-compact-panel" :class="{ 'is-left': fabPosition.side === 'left' }">
      <header>
        <div>
          <strong>AI 导购助手</strong>
          <small>真实商品 · 任务推进 · 安全加购</small>
        </div>
        <div class="chat-header-actions">
          <button class="ghost" type="button" title="恢复默认位置" @click="resetFabPosition">复位</button>
          <button class="ghost" type="button" title="新会话" @click="newSession">新会话</button>
          <button class="ghost" type="button" title="展开导购工作台" @click="workbenchOpen = true">展开</button>
          <button class="ghost" type="button" title="关闭" @click="open = false">×</button>
        </div>
      </header>
      <AgentConversation mode="compact" @expand="workbenchOpen = true" @open-full="openFullGuide" />
    </section>

    <AgentWorkbench v-if="workbenchOpen" @close="workbenchOpen = false" @open-full="openFullGuide" />
  </template>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import AgentConversation from './AgentConversation.vue'
import AgentWorkbench from './AgentWorkbench.vue'
import { router } from '../router'
import { useAgentConversation } from '../composables/useAgentConversation'

const { ensureLoaded, fabStatus, newSession } = useAgentConversation()
const open = ref(false)
const workbenchOpen = ref(false)
const dragging = ref(false)
const desktop = ref(window.innerWidth > 720)
const justDragged = ref(false)
const dragOffsetX = ref(0)
const dragOffsetY = ref(0)
const dragMoved = ref(false)
const storedPosition = readPosition()
const fabPosition = ref(storedPosition || defaultPosition())
const isGuidePage = computed(() => router.currentRoute.value.path === '/ai-guide')
const fabStyle = computed(() => {
  if (!desktop.value) return undefined
  const draggingLeft = dragging.value && Number.isFinite(fabPosition.value.left)
  return {
    top: `${fabPosition.value.top}px`,
    bottom: 'auto',
    left: draggingLeft ? `${fabPosition.value.left}px` : (fabPosition.value.side === 'left' ? '16px' : 'auto'),
    right: draggingLeft ? 'auto' : (fabPosition.value.side === 'right' ? '16px' : 'auto')
  }
})

function defaultPosition() {
  return { side: 'right', top: Math.max(92, window.innerHeight - 86) }
}

function readPosition() {
  try {
    const value = JSON.parse(localStorage.getItem('agentFabPosition') || 'null')
    if (!value || !['left', 'right'].includes(value.side) || !Number.isFinite(value.top)) return null
    return { side: value.side, top: clampTop(value.top) }
  } catch {
    return null
  }
}

function clampTop(value) {
  return Math.min(Math.max(84, value), Math.max(84, window.innerHeight - 78))
}

function clampLeft(value, width) {
  return Math.min(Math.max(16, value), Math.max(16, window.innerWidth - width - 16))
}

function persistPosition() {
  localStorage.setItem('agentFabPosition', JSON.stringify(fabPosition.value))
}

async function toggleOpen() {
  if (justDragged.value) return
  open.value = !open.value
  if (open.value) await ensureLoaded()
}

function startDrag(event) {
  if (!desktop.value || event.button !== 0) return
  const rect = event.currentTarget.getBoundingClientRect()
  dragging.value = true
  dragMoved.value = false
  dragOffsetX.value = event.clientX - rect.left
  dragOffsetY.value = event.clientY - rect.top
  fabPosition.value = { ...fabPosition.value, left: rect.left }
  event.currentTarget.setPointerCapture?.(event.pointerId)
  window.addEventListener('pointermove', moveDrag)
  window.addEventListener('pointerup', endDrag, { once: true })
}

function moveDrag(event) {
  if (!dragging.value) return
  dragMoved.value = true
  const width = document.querySelector('.agent-fab')?.getBoundingClientRect().width || 112
  fabPosition.value = {
    ...fabPosition.value,
    left: clampLeft(event.clientX - dragOffsetX.value, width),
    top: clampTop(event.clientY - dragOffsetY.value)
  }
}

function endDrag(event) {
  if (!dragging.value) return
  dragging.value = false
  window.removeEventListener('pointermove', moveDrag)
  if (!dragMoved.value) return
  fabPosition.value = {
    side: event.clientX < window.innerWidth / 2 ? 'left' : 'right',
    top: clampTop(fabPosition.value.top)
  }
  persistPosition()
  justDragged.value = true
  window.setTimeout(() => { justDragged.value = false }, 0)
}

function resetFabPosition() {
  fabPosition.value = defaultPosition()
  localStorage.removeItem('agentFabPosition')
}

function onResize() {
  desktop.value = window.innerWidth > 720
  fabPosition.value = { ...fabPosition.value, top: clampTop(fabPosition.value.top) }
}

function openFullGuide() {
  open.value = false
  workbenchOpen.value = false
  router.push('/ai-guide')
}

onMounted(() => window.addEventListener('resize', onResize))
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  window.removeEventListener('pointermove', moveDrag)
})
</script>
