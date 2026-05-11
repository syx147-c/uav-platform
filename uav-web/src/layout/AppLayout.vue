<script setup>
/**
 * 应用主布局 — 侧边导航栏 + 顶部状态栏 + 内容区
 */
import { ref, computed } from 'vue';                              // Vue 3 API
import { useRouter, useRoute } from 'vue-router';                 // Vue Router
import { useWebSocket } from '../composables/useWebSocket';       // WebSocket 连接 Hook

// === 路由 ===
const router = useRouter();                                       // 路由实例
const route = useRoute();                                         // 当前路由信息

// === WebSocket 连接（全局共享） ===
const { telemetry, connected } = useWebSocket();                  // 在 AppLayout 层建立连接，所有子页面通过 provide 获取

// === 侧边栏菜单项 ===
const menuItems = [
  { path: '/dashboard', title: '驾驶舱', icon: '⌂' },
  { path: '/charts',    title: '数据图表', icon: '⊡' },
  { path: '/missions',  title: '任务管理', icon: '≡' },
  { path: '/logs',      title: '飞行日志', icon: '⏏' }
];

// === 当前激活菜单项 ===
const activeMenu = computed(() => route.path);                    // 根据当前路由高亮对应菜单

// === 菜单点击：路由跳转 ===
function navigateTo(path) {
  router.push(path);
}
</script>

<template>
  <div class="app-layout">
    <!-- ===== 左侧导航栏 ===== -->
    <aside class="sidebar">
      <div class="logo">
        <span class="logo-icon">🛩</span>
        <span class="logo-text">UAV 飞控平台</span>
      </div>
      <nav class="nav-menu">
        <div
          v-for="item in menuItems"
          :key="item.path"
          class="nav-item"
          :class="{ active: activeMenu === item.path }"
          @click="navigateTo(item.path)"
        >
          <span class="nav-icon">{{ item.icon }}</span>
          <span class="nav-title">{{ item.title }}</span>
        </div>
      </nav>
      <div class="sidebar-footer">
        <span class="status-dot" :class="{ online: connected }"></span>
        {{ connected ? '已连接' : '未连接' }}
      </div>
    </aside>

    <!-- ===== 右侧主区域 ===== -->
    <div class="main-area">
      <!-- 顶部信息栏 -->
      <header class="top-bar">
        <div class="top-left">{{ route.meta.title || '' }}</div>
        <div class="top-right">
          <span class="telemetry-brief">
            电量 {{ telemetry.battery ?? '--' }}% &nbsp;|&nbsp;
            高度 {{ telemetry.altitude?.toFixed(2) ?? '--' }}m &nbsp;|&nbsp;
            {{ telemetry.in_air ? '🟢 飞行中' : '⚪ 地面' }}
          </span>
        </div>
      </header>
      <!-- 页面内容区 -->
      <main class="content">
        <router-view :telemetry="telemetry" :connected="connected" />
      </main>
    </div>
  </div>
</template>

<style scoped>
/* ===== 整体布局 ===== */
.app-layout {
  display: flex;
  height: 100vh;
  background: #f0f2f5;
  color: #333;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

/* ===== 侧边导航栏 ===== */
.sidebar {
  width: 220px;
  background: linear-gradient(180deg, #1a1a2e 0%, #16213e 100%);
  color: #ccc;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.logo {
  padding: 20px 16px;
  display: flex;
  align-items: center;
  gap: 10px;
  border-bottom: 1px solid rgba(255,255,255,0.1);
}
.logo-icon { font-size: 24px; }
.logo-text { font-size: 16px; font-weight: bold; color: #fff; }

.nav-menu { flex: 1; padding: 12px 8px; }

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  margin-bottom: 4px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  color: #999;
  font-size: 14px;
}
.nav-item:hover {
  background: rgba(255,255,255,0.08);
  color: #e0e0e0;
}
.nav-item.active {
  background: rgba(64, 158, 255, 0.2);
  color: #409EFF;
  font-weight: 600;
}
.nav-icon { font-size: 18px; }
.nav-title { font-size: 14px; }

.sidebar-footer {
  padding: 16px;
  font-size: 12px;
  border-top: 1px solid rgba(255,255,255,0.1);
  display: flex;
  align-items: center;
  gap: 8px;
  color: #888;
}
.status-dot {
  width: 8px; height: 8px;
  border-radius: 50%;
  background: #f44336;
  display: inline-block;
}
.status-dot.online { background: #4caf50; }

/* ===== 顶部状态栏 ===== */
.main-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.top-bar {
  height: 52px;
  background: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 24px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
  flex-shrink: 0;
}
.top-left { font-size: 16px; font-weight: 600; color: #333; }
.top-right { font-size: 13px; color: #666; }
.telemetry-brief { font-family: 'Menlo', 'Consolas', monospace; }

/* ===== 页面内容 ===== */
.content {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}
</style>
