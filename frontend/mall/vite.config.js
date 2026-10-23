import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * 商城前台的 Vite 配置。
 * <p>端口与后台错开（前台 5174、后台 5173），两者可以同时开着演示。
 * <p>同样用 proxy 走相对路径 /api，前端代码里不出现后端地址。
 */
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5174,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
