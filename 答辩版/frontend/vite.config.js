import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const backendTarget = process.env.VITE_BACKEND_PROXY_TARGET || 'http://localhost:8081'
const agentTarget = process.env.VITE_AGENT_PROXY_TARGET || 'http://localhost:8000'
const cacheDir = process.env.VITE_CACHE_DIR || 'node_modules/.vite'

export default defineConfig({
  cacheDir,
  plugins: [vue()],
  server: {
    proxy: {
      '/api': backendTarget,
      '/agent': agentTarget
    }
  }
})
