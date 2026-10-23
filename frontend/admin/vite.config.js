import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * 管理后台的 Vite 配置。
 *
 * ⚠️ 用 proxy 而不是让前端直连 http://localhost:8080：
 *   1. 开发时前端在 5173、后端在 8080，直连就是跨域，需要后端配 CORS（本项目只允许了 5173，
 *      一旦换端口就得改后端配置，很别扭）；
 *   2. 走 proxy 之后前端代码里所有请求都写相对路径 /api/...，
 *      部署到 Nginx 后面时也只需要一条反向代理，**前端代码不用改**。
 */
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/doc.html': { target: 'http://localhost:8080', changeOrigin: true },
      '/v3/api-docs': { target: 'http://localhost:8080', changeOrigin: true }
    }
  }
})
