<script setup>
/**
 * 登录页面 — 天蓝色主题
 *
 * 功能：
 * - 登录 / 注册双模式（Tab 切换）
 * - JWT Token 认证流程
 * - 表单校验 + 错误提示
 * - 登录成功后跳转到驾驶舱主页
 */
import { ref, reactive, computed } from 'vue';
import { useRouter } from 'vue-router';
import { useAuth } from '../composables/useAuth.js';

const router = useRouter();
const { login, register } = useAuth();

// ===== 表单模式：login | register =====
const mode = ref('login'); // 'login' | 'register'

// ===== 表单数据 =====
const form = reactive({
  username: '',
  password: '',
  confirmPassword: '' // 注册时需要二次确认密码
});

// ===== 状态 =====
const loading = ref(false);
const errorMsg = ref('');

// ===== 校验 =====
/** 检查表单是否可提交 */
const canSubmit = computed(() => {
  const u = form.username.trim();
  const p = form.password.trim();
  if (mode.value === 'register') {
    return u.length >= 3 && p.length >= 6 && p === form.confirmPassword.trim();
  }
  return u.length >= 3 && p.length >= 6;
});

// ===== 提交 =====
async function handleSubmit() {
  if (!canSubmit.value || loading.value) return;

  errorMsg.value = '';
  loading.value = true;

  try {
    if (mode.value === 'login') {
      await login(form.username.trim(), form.password.trim());
    } else {
      await register(form.username.trim(), form.password.trim());
    }
    // 登录/注册成功 → 跳转到驾驶舱
    router.replace('/dashboard');
  } catch (e) {
    // 提取后端返回的错误信息
    errorMsg.value = e.message || '操作失败，请检查网络连接';
  } finally {
    loading.value = false;
  }
}

/** 切换登录/注册模式时清空错误 */
function switchMode(m) {
  mode.value = m;
  errorMsg.value = '';
  form.confirmPassword = '';
}
</script>

<template>
  <div class="login-page">
    <!-- ===== 背景装饰 ===== -->
    <div class="bg-decor">
      <!-- 柔光圆形 -->
      <div class="bg-orb orb-1"></div>
      <div class="bg-orb orb-2"></div>
      <div class="bg-orb orb-3"></div>
      <!-- 漂浮动感线条 -->
      <div class="bg-line line-1"></div>
      <div class="bg-line line-2"></div>
    </div>

    <!-- ===== 登录卡片 ===== -->
    <div class="login-card">
      <!-- Logo + 标题 -->
      <div class="card-header">
        <div class="logo-hex">
          <span class="logo-inner">◆</span>
        </div>
        <h1 class="title">UAV<span class="accent">.CTRL</span></h1>
        <p class="subtitle">Flight Control System — 身份认证</p>
      </div>

      <!-- Tab 切换 -->
      <div class="tab-row">
        <button
          class="tab-btn"
          :class="{ active: mode === 'login' }"
          @click="switchMode('login')"
        >登 录</button>
        <button
          class="tab-btn"
          :class="{ active: mode === 'register' }"
          @click="switchMode('register')"
        >注 册</button>
      </div>

      <!-- 表单 -->
      <form class="login-form" @submit.prevent="handleSubmit">
        <!-- 用户名 -->
        <div class="field">
          <label class="field-label">用户名</label>
          <input
            v-model="form.username"
            class="field-input"
            type="text"
            placeholder="请输入用户名（至少3位）"
            autocomplete="username"
            :disabled="loading"
          />
        </div>

        <!-- 密码 -->
        <div class="field">
          <label class="field-label">密码</label>
          <input
            v-model="form.password"
            class="field-input"
            type="password"
            placeholder="请输入密码（至少6位）"
            autocomplete="current-password"
            :disabled="loading"
          />
        </div>

        <!-- 确认密码（仅注册模式） -->
        <div v-if="mode === 'register'" class="field">
          <label class="field-label">确认密码</label>
          <input
            v-model="form.confirmPassword"
            class="field-input"
            type="password"
            placeholder="请再次输入密码"
            autocomplete="new-password"
            :disabled="loading"
            :class="{
              'input-match': form.confirmPassword && form.password === form.confirmPassword,
              'input-mismatch': form.confirmPassword && form.password !== form.confirmPassword
            }"
          />
          <span
            v-if="form.confirmPassword && form.password !== form.confirmPassword"
            class="field-hint error"
          >两次密码不一致</span>
        </div>

        <!-- 错误提示 -->
        <div v-if="errorMsg" class="error-banner">
          <span class="error-icon">!</span>
          {{ errorMsg }}
        </div>

        <!-- 提交按钮 -->
        <button
          type="submit"
          class="submit-btn"
          :class="{ loading }"
          :disabled="!canSubmit || loading"
        >
          <span v-if="loading" class="spinner"></span>
          <span v-else>{{ mode === 'login' ? '登 录' : '注 册' }}</span>
        </button>
      </form>

      <!-- 底部提示 -->
      <p class="card-footer">
        默认管理员账号：admin / admin123
      </p>
    </div>
  </div>
</template>

<style scoped>
/* ============================================
   登录页 — 天蓝色主题
   配色：#e8f4fd 天空蓝底 / #3b82f6 主强调色
   ============================================ */
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(160deg, #e8f4fd 0%, #d0e8fb 30%, #f0f7fd 60%, #f8fafc 100%);
  position: relative;
  overflow: hidden;
  font-family: 'Inter', 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif;
}

/* ===== 背景装饰动画 ===== */
.bg-decor {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}

/* 柔光球体 */
.bg-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.30;
  animation: orb-float 12s ease-in-out infinite;
}
.orb-1 {
  width: 420px; height: 420px;
  background: #93c5fd; /* blue-300 */
  top: -120px; right: -100px;
  animation-delay: 0s;
}
.orb-2 {
  width: 320px; height: 320px;
  background: #a5b4fc; /* indigo-300 */
  bottom: -100px; left: -80px;
  animation-delay: -4s;
}
.orb-3 {
  width: 260px; height: 260px;
  background: #7dd3fc; /* sky-300 */
  top: 50%; left: 50%;
  transform: translate(-50%, -50%);
  animation-delay: -8s;
}

@keyframes orb-float {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33%  { transform: translate(30px, -20px) scale(1.05); }
  66%  { transform: translate(-20px, 15px) scale(0.95); }
}

/* 动感线条 */
.bg-line {
  position: absolute;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(59,130,246,0.15), transparent);
  animation: line-drift 20s linear infinite;
}
.line-1 {
  width: 60%;
  top: 25%;
  left: -10%;
  animation-duration: 24s;
}
.line-2 {
  width: 40%;
  top: 70%;
  right: -10%;
  animation-duration: 18s;
  animation-delay: -6s;
}

@keyframes line-drift {
  0%   { transform: translateX(0) rotate(-3deg); }
  100% { transform: translateX(110vw) rotate(-3deg); }
}

/* ===== 卡片主体 ===== */
.login-card {
  position: relative;
  z-index: 10;
  width: 420px;
  max-width: 94vw;
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border-radius: 20px;
  box-shadow:
    0 4px 24px rgba(59, 130, 246, 0.08),
    0 20px 60px rgba(59, 130, 246, 0.06),
    0 0 0 1px rgba(59, 130, 246, 0.08);
  padding: 44px 40px 36px;
  animation: card-enter 0.6s ease-out;
}

@keyframes card-enter {
  from {
    opacity: 0;
    transform: translateY(24px) scale(0.97);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

/* ===== Logo 区域 ===== */
.card-header {
  text-align: center;
  margin-bottom: 28px;
}

.logo-hex {
  width: 52px;
  height: 52px;
  margin: 0 auto 14px;
  border: 2px solid #3b82f6;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  transform: rotate(45deg);
  background: linear-gradient(135deg, rgba(59,130,246,0.06), rgba(99,102,241,0.04));
  box-shadow: 0 0 20px rgba(59, 130, 246, 0.12);
  animation: pulse-glow 3s ease-in-out infinite;
}

.logo-inner {
  transform: rotate(-45deg);
  font-size: 20px;
  color: #3b82f6;
}

@keyframes pulse-glow {
  0%, 100% { box-shadow: 0 0 20px rgba(59, 130, 246, 0.12); }
  50%      { box-shadow: 0 0 32px rgba(59, 130, 246, 0.24); }
}

.title {
  font-size: 28px;
  font-weight: 800;
  color: #1e293b;
  letter-spacing: 2px;
  font-family: 'JetBrains Mono', 'Cascadia Code', monospace;
}

.accent {
  color: #3b82f6;
}

.subtitle {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 6px;
  letter-spacing: 1px;
  text-transform: uppercase;
}

/* ===== Tab 切换 ===== */
.tab-row {
  display: flex;
  gap: 0;
  margin-bottom: 24px;
  background: rgba(59, 130, 246, 0.04);
  border-radius: 10px;
  padding: 4px;
}

.tab-btn {
  flex: 1;
  padding: 10px 0;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #64748b;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.25s;
  letter-spacing: 2px;
}

.tab-btn.active {
  background: #ffffff;
  color: #3b82f6;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.12);
}

.tab-btn:hover:not(.active) {
  color: #3b82f6;
  background: rgba(59, 130, 246, 0.04);
}

/* ===== 表单字段 ===== */
.login-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-label {
  font-size: 12px;
  font-weight: 600;
  color: #475569;
  letter-spacing: 0.5px;
}

.field-input {
  width: 100%;
  padding: 12px 16px;
  border: 1.5px solid rgba(59, 130, 246, 0.15);
  border-radius: 10px;
  background: rgba(59, 130, 246, 0.02);
  color: #1e293b;
  font-size: 14px;
  outline: none;
  transition: all 0.25s;
  font-family: inherit;
}

.field-input::placeholder {
  color: #c0c8d4;
  font-size: 13px;
}

.field-input:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.08);
  background: #ffffff;
}

.field-input:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

/* 注册时密码确认匹配/不匹配提示 */
.input-match {
  border-color: #10b981 !important;
  background: rgba(16, 185, 129, 0.02) !important;
}

.input-mismatch {
  border-color: #ef4444 !important;
  background: rgba(239, 68, 68, 0.02) !important;
}

.field-hint {
  font-size: 11px;
  margin-top: 2px;
}

.field-hint.error {
  color: #ef4444;
}

/* ===== 错误提示 ===== */
.error-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: rgba(239, 68, 68, 0.06);
  border: 1px solid rgba(239, 68, 68, 0.2);
  border-radius: 8px;
  color: #dc2626;
  font-size: 13px;
  line-height: 1.5;
}

.error-icon {
  width: 18px; height: 18px;
  border-radius: 50%;
  background: #ef4444;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
}

/* ===== 提交按钮 ===== */
.submit-btn {
  width: 100%;
  padding: 13px 0;
  margin-top: 4px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #3b82f6 0%, #6366f1 100%);
  color: #ffffff;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 3px;
  cursor: pointer;
  transition: all 0.3s;
  box-shadow: 0 4px 16px rgba(59, 130, 246, 0.25);
  font-family: inherit;
  position: relative;
  overflow: hidden;
}

.submit-btn::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #2563eb 0%, #4f46e5 100%);
  opacity: 0;
  transition: opacity 0.3s;
}

.submit-btn:hover:not(:disabled)::before {
  opacity: 1;
}

.submit-btn:hover:not(:disabled) {
  box-shadow: 0 6px 24px rgba(59, 130, 246, 0.35);
  transform: translateY(-1px);
}

.submit-btn:active:not(:disabled) {
  transform: translateY(0);
}

.submit-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.submit-btn span {
  position: relative;
  z-index: 1;
}

/* 加载旋转器 */
.spinner {
  display: inline-block;
  width: 18px; height: 18px;
  border: 2.5px solid rgba(255,255,255,0.3);
  border-top-color: #ffffff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ===== 底部提示 ===== */
.card-footer {
  text-align: center;
  margin-top: 24px;
  font-size: 11px;
  color: #a0aec0;
  letter-spacing: 0.5px;
}
</style>
