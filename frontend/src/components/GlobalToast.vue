<template>
  <Transition name="toast">
    <div v-if="state.visible" class="toast" role="status" aria-live="polite">
      <span class="toast-dot" aria-hidden="true" />
      <span>{{ state.message }}</span>
    </div>
  </Transition>
</template>

<script setup>
import { useToast } from '../composables/useToast'

const { state } = useToast()
</script>
