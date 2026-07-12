import { reactive } from 'vue'

const state = reactive({
  visible: false,
  message: '',
  timer: null
})

export function useToast() {
  function show(message) {
    state.message = message
    state.visible = true
    if (state.timer) window.clearTimeout(state.timer)
    state.timer = window.setTimeout(() => {
      state.visible = false
      state.timer = null
    }, 2400)
  }

  return { state, show }
}
