import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    // 必须显式绑 0.0.0.0：默认只监听 [::1]（IPv6），
    // 浏览器/内置预览走 IPv4 的 127.0.0.1 时会被拒绝连接（ERR_CONNECTION_REFUSED）
    host: true,
    open: true,
    proxy: {
      // 后端 Spring Boot
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      // 本地文件存储（后端直出图片时使用）
      '/files': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 1500,
    rollupOptions: {
      output: {
        manualChunks: {
          vue: ['vue', 'vue-router', 'pinia'],
          element: ['element-plus', '@element-plus/icons-vue'],
          echarts: ['echarts']
        }
      }
    }
  }
})
