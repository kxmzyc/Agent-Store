<template>
  <aside class="agent-workbench" aria-label="AI 导购工作台">
    <header class="agent-workbench-header">
      <div>
        <span>SMART MALL AGENT</span>
        <strong>导购工作台</strong>
      </div>
      <div class="agent-workbench-actions">
        <button type="button" @click="newSession">新会话</button>
        <button type="button" @click="$emit('open-full')">完整方案页</button>
        <button type="button" aria-label="关闭导购工作台" @click="$emit('close')">×</button>
      </div>
    </header>
    <AgentConversation mode="workbench" @open-full="$emit('open-full')" />
  </aside>
</template>

<script setup>
import AgentConversation from './AgentConversation.vue'
import { useAgentConversation } from '../composables/useAgentConversation'

defineEmits(['close', 'open-full'])

const { newSession } = useAgentConversation()
</script>
