import { defineConfig } from 'vite';                // Vite 配置函数
import vue from '@vitejs/plugin-vue';                 // Vue 3 单文件组件编译插件
import cesium from 'vite-plugin-cesium';               // Cesium 3D 地图 Vite 插件

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    vue(),                                              // 启用 Vue SFC 编译
    cesium()                                            // 启用 Cesium 3D 地球支持
  ],
  server: {
    port: 5173,                                         // 前端开发服务器端口
    proxy: {
      '/api': 'http://localhost:8080',                  // 将 /api 开头的请求代理到 Spring Boot 后端
      '/ws': {
        target: 'ws://localhost:8080',                  // 将 /ws 开头的 WebSocket 连接代理到后端
        ws: true                                        // 启用 WebSocket 代理
      }
    }
  }
});
