<template>
  <section class="ai-guide-page">
    <header class="ai-guide-page-header">
      <div>
        <span>SMART MALL / AI GUIDE</span>
        <h1>完整导购方案</h1>
        <p>从需求澄清到真实库存方案，再到安全确认加购。</p>
      </div>
      <div class="ai-guide-page-actions">
        <button type="button" @click="newSession">新会话</button>
        <button type="button" @click="router.back()">返回商城</button>
      </div>
    </header>
    <AgentConversation mode="page" />
  </section>
</template>

<script setup>
import { onMounted } from 'vue'
import AgentConversation from '../components/AgentConversation.vue'
import { useAgentConversation } from '../composables/useAgentConversation'
import { router } from '../router'

const { ensureLoaded, newSession } = useAgentConversation()

onMounted(ensureLoaded)
</script>
