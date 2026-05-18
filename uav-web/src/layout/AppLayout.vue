<script setup>
/**
 * 科技感主布局 — 霓虹侧边栏 + 顶部数据条 + 内容区
 * 配色：深空蓝 + 霓虹青 + 电光蓝 + 星云紫
 */
import { ref, computed, onMounted, onUnmounted } from 'vue';        // Vue 3 API
import { useRouter, useRoute } from 'vue-router';                    // 路由
import { useWebSocket } from '../composables/useWebSocket';          // WebSocket 连接
import { useAuth } from '../composables/useAuth.js';                 // JWT 认证状态

const router = useRouter();
const route = useRoute();

// === JWT 认证状态 ===
const { username, role, logout } = useAuth();

// === 全局 WebSocket 连接 ===
const { telemetry, connected, connect } = useWebSocket();

// === 侧边栏菜单项 ===
const menuItems = [
  { path: '/dashboard', title: '驾驶舱',    desc: '3D Mission Control',   icon: '◉' },
  { path: '/charts',    title: '数据图表',   desc: 'Telemetry Analytics',  icon: '⊡' },
  { path: '/chat',      title: 'AI 控制台',  desc: 'Natural Lang CMD',     icon: '▣' },
  { path: '/missions',  title: '任务管理',   desc: 'Mission Planner',      icon: '⊞' },
  { path: '/logs',      title: '飞行日志',   desc: 'Flight Data Recorder', icon: '⊟' }
];

const activeMenu = computed(() => route.path);
function navigateTo(path) { router.push(path); }

// === 实时时钟 ===
const clock = ref('');
let timer = null;
onMounted(() => {
  connect();
  timer = setInterval(() => {
    clock.value = new Date().toLocaleTimeString('zh-CN', { hour12: false });
  }, 1000);
});
onUnmounted(() => clearInterval(timer));

/** 退出登录 — 清除凭证并跳转到登录页 */
function handleLogout() {
  logout();
  router.push('/login');
}
</script>

<template>
  <div class="app-layout">
    <!-- 背景粒子网格 -->
    <div class="bg-grid"></div>
    <div class="bg-scan"></div>

    <!-- ==================== 左侧导航栏 ==================== -->
    <aside class="sidebar">
      <!-- 标志区域 -->
      <div class="logo-area">
        <div class="logo-hex">
          <span class="logo-icon">◆</span>
        </div>
        <div class="logo-text">
          <span class="logo-title">UAV<span style="color:var(--cyan)">.CTRL</span></span>
          <span class="logo-subtitle">Flight Control System</span>
        </div>
      </div>

      <!-- 分割线 -->
      <div class="divider-line"></div>

      <!-- 菜单 -->
      <nav class="nav-menu">
        <div
          v-for="item in menuItems" :key="item.path"
          class="nav-item"
          :class="{ active: activeMenu === item.path }"
          @click="navigateTo(item.path)"
        >
          <span class="nav-icon">{{ item.icon }}</span>
          <div class="nav-info">
            <span class="nav-title">{{ item.title }}</span>
            <span class="nav-desc">{{ item.desc }}</span>
          </div>
          <span class="nav-arrow" v-if="activeMenu === item.path">▸</span>
        </div>
      </nav>

      <!-- 底部状态 -->
      <div class="sidebar-footer">
        <div class="status-row">
          <span class="status-dot" :class="{ online: connected }"></span>
          <span class="status-text">{{ connected ? 'SYS.ONLINE' : 'SYS.OFFLINE' }}</span>
        </div>
        <div class="status-row">
          <span class="status-dot small" style="background:var(--cyan)"></span>
          <span class="status-text small">{{ clock }}</span>
        </div>
        <div class="status-row version">
          <span>VER 1.0.3-BETA</span>
        </div>
      </div>
    </aside>

    <!-- ==================== 右侧主区域 ==================== -->
    <div class="main-area">
      <!-- 顶部状态栏 -->
      <header class="top-bar">
        <div class="top-left">
          <span class="route-icon">◈</span>
          <span class="route-title">{{ route.meta?.title || '' }}</span>
        </div>
        <div class="top-center">
          <div class="data-chip">
            <span class="chip-label">BATT</span>
            <span class="chip-value" :class="{ warn: telemetry.battery < 30 }">{{ telemetry.battery ?? '--' }}%</span>
          </div>
          <div class="data-chip">
            <span class="chip-label">ALT</span>
            <span class="chip-value">{{ telemetry.altitude?.toFixed(1) ?? '--' }}M</span>
          </div>
          <div class="data-chip">
            <span class="chip-label">LAT</span>
            <span class="chip-value">{{ telemetry.latitude?.toFixed(4) ?? '--' }}</span>
          </div>
          <div class="data-chip">
            <span class="chip-label">LON</span>
            <span class="chip-value">{{ telemetry.longitude?.toFixed(4) ?? '--' }}</span>
          </div>
          <div class="data-chip">
            <span class="chip-label">STATUS</span>
            <span class="chip-value" :style="{ color: telemetry.in_air ? 'var(--cyan)' : 'var(--gray)' }">
              {{ telemetry.in_air ? 'AIRBORNE' : 'GROUND' }}
            </span>
          </div>
        </div>
        <div class="top-right">
          <!-- 连接状态 -->
          <span class="conn-badge" :class="{ active: connected }">
            {{ connected ? '● LIVE' : '○ IDLE' }}
          </span>
          <!-- 用户信息 -->
          <span class="user-badge">
            <span class="user-icon">▣</span>
            {{ username }}
            <span class="user-role">({{ role }})</span>
          </span>
          <!-- 退出按钮 -->
          <button class="logout-btn" @click="handleLogout" title="退出登录">
            ⏻
          </button>
        </div>
      </header>

      <!-- 页面内容 -->
      <main class="content">
        <router-view :telemetry="telemetry" :connected="connected" />
      </main>
    </div>
  </div>
</template>

<style scoped>
.app-layout {
  display: flex;
  height: 100vh;
  background: var(--bg-deep);
  position: relative;
  overflow: hidden;
}

/* ===== 背景效果 ===== */
.bg-grid {
  position: fixed; inset: 0; z-index: 0; pointer-events: none;
  background-image:
    linear-gradient(rgba(59,130,246,0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(59,130,246,0.03) 1px, transparent 1px);
  background-size: 40px 40px;
}
.bg-scan {
  position: fixed; inset: 0; z-index: 0; pointer-events: none;
  background: linear-gradient(transparent 0%, rgba(100,160,220,0.015) 50%, transparent 100%);
  background-size: 100% 200px;
  animation: scan-line 8s linear infinite;
}

/* ===== 侧边栏 ===== */
.sidebar {
  width: 240px;
  background: linear-gradient(180deg, rgba(255,255,255,0.98) 0%, rgba(248,250,252,0.98) 100%);
  border-right: 1px solid rgba(59,130,246,0.12);
  display: flex;
  flex-direction: column;
  z-index: 10;
  position: relative;
}
.sidebar::after {
  content: '';
  position: absolute; right: -1px; top: 60px; bottom: 60px;
  width: 1px;
  background: linear-gradient(transparent, var(--cyan), transparent);
  opacity: 0.3;
}

/* Logo */
.logo-area {
  padding: 22px 20px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.logo-hex {
  width: 42px; height: 42px;
  border: 1.5px solid var(--cyan);
  display: flex; align-items: center; justify-content: center;
  transform: rotate(45deg);
  box-shadow: var(--glow-cyan);
  animation: pulse-glow 3s ease-in-out infinite;
}
.logo-icon {
  transform: rotate(-45deg);
  font-size: 18px;
  color: var(--cyan);
}
.logo-text { display: flex; flex-direction: column; }
.logo-title {
  font-size: 17px; font-weight: 800; letter-spacing: 2px;
  font-family: var(--font-mono);
}
.logo-subtitle {
  font-size: 9px; color: var(--gray); text-transform: uppercase; letter-spacing: 1.5px;
  font-family: var(--font-mono);
}

/* 分割线 */
.divider-line {
  height: 1px; margin: 0 20px 8px;
  background: linear-gradient(90deg, transparent, var(--border-glow), transparent);
}

/* 菜单项 */
.nav-menu { flex: 1; padding: 4px 12px; }
.nav-item {
  display: flex; align-items: center;
  padding: 12px 14px;
  margin-bottom: 4px;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.25s;
  position: relative;
  overflow: hidden;
}
.nav-item::before {
  content: '';
  position: absolute; inset: 0;
  background: linear-gradient(135deg, rgba(59,130,246,0.06), rgba(168,85,247,0.03));
  opacity: 0; transition: opacity 0.25s;
}
.nav-item:hover::before { opacity: 1; }
.nav-item:hover { color: var(--white); background: rgba(59,130,246,0.04); }
.nav-item.active {
  background: linear-gradient(135deg, rgba(59,130,246,0.12), rgba(168,85,247,0.06));
  border: 1px solid rgba(59,130,246,0.2);
  box-shadow: 0 0 16px rgba(59,130,246,0.06);
}
.nav-icon {
  font-size: 18px; width: 28px; text-align: center;
  color: var(--gray);
  transition: color 0.25s;
}
.nav-item:hover .nav-icon,
.nav-item.active .nav-icon { color: var(--cyan); }
.nav-info { flex: 1; display: flex; flex-direction: column; margin-left: 4px; }
.nav-title { font-size: 13px; font-weight: 600; color: var(--white); }
.nav-desc { font-size: 9px; color: var(--gray); font-family: var(--font-mono); letter-spacing: 0.5px; }
.nav-arrow { color: var(--cyan); font-size: 14px; }

/* 底部状态 */
.sidebar-footer {
  padding: 16px 20px;
  border-top: 1px solid rgba(59,130,246,0.08);
  display: flex; flex-direction: column; gap: 6px;
}
.status-row { display: flex; align-items: center; gap: 8px; }
.status-dot {
  width: 8px; height: 8px; border-radius: 50%;
  background: var(--rose);
  box-shadow: 0 0 6px var(--rose);
}
.status-dot.online {
  background: var(--emerald);
  box-shadow: 0 0 8px var(--emerald), 0 0 16px rgba(16,185,129,0.3);
}
.status-dot.small { width: 5px; height: 5px; }
.status-text {
  font-size: 11px; font-family: var(--font-mono); color: var(--gray);
  text-transform: uppercase; letter-spacing: 1px;
}
.status-text.small { font-size: 10px; }
.version { font-size: 9px; color: rgba(100,116,139,0.5); letter-spacing: 2px; margin-top: 4px; }

/* ===== 顶部状态栏 ===== */
.main-area { flex: 1; display: flex; flex-direction: column; overflow: hidden; z-index: 5; }
.top-bar {
  height: 56px;
  background: linear-gradient(180deg, rgba(255,255,255,0.95), rgba(248,250,252,0.85));
  backdrop-filter: blur(20px);
  border-bottom: 1px solid rgba(59,130,246,0.1);
  display: flex;
  align-items: center;
  padding: 0 24px;
  gap: 16px;
  flex-shrink: 0;
}

.top-left {
  display: flex; align-items: center; gap: 8px;
  font-size: 15px; font-weight: 600; color: var(--white);
}
.route-icon { color: var(--cyan); font-size: 18px; }

/* 数据芯片 */
.top-center {
  flex: 1;
  display: flex; align-items: center; gap: 4px;
  justify-content: center;
}
.data-chip {
  display: flex; align-items: center; gap: 6px;
  padding: 4px 12px;
  background: rgba(59,130,246,0.04);
  border: 1px solid rgba(59,130,246,0.1);
  border-radius: 6px;
  font-family: var(--font-mono); font-size: 11px;
}
.chip-label { color: var(--gray); font-size: 9px; letter-spacing: 1px; }
.chip-value { color: var(--cyan); font-weight: 700; }
.chip-value.warn { color: var(--rose); }

.top-right {
  display: flex; align-items: center; gap: 10px;
}
.conn-badge {
  font-family: var(--font-mono); font-size: 10px;
  padding: 3px 10px;
  border: 1px solid rgba(244,63,94,0.3);
  border-radius: 20px;
  color: var(--rose);
  letter-spacing: 1px;
}
.conn-badge.active {
  border-color: rgba(16,185,129,0.4);
  color: var(--emerald);
  box-shadow: 0 0 10px rgba(16,185,129,0.15);
}

/* 用户信息标签 */
.user-badge {
  display: flex; align-items: center; gap: 6px;
  padding: 4px 12px;
  background: rgba(59,130,246,0.04);
  border: 1px solid rgba(59,130,246,0.12);
  border-radius: 8px;
  font-family: var(--font-mono); font-size: 11px;
  color: var(--white);
  letter-spacing: 0.5px;
}
.user-icon { color: var(--cyan); font-size: 12px; }
.user-role { font-size: 9px; color: var(--gray); }

/* 退出登录按钮 */
.logout-btn {
  width: 32px; height: 32px;
  display: flex; align-items: center; justify-content: center;
  background: rgba(239,68,68,0.04);
  border: 1px solid rgba(239,68,68,0.15);
  border-radius: 8px;
  color: var(--rose);
  font-size: 16px;
  cursor: pointer;
  transition: all 0.25s;
}
.logout-btn:hover {
  background: rgba(239,68,68,0.1);
  border-color: rgba(239,68,68,0.35);
  box-shadow: var(--glow-rose);
}

/* 内容区 */
.content {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  position: relative;
  z-index: 1;
}
</style>
