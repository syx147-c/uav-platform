/**
 * 认证状态管理 Composable
 *
 * 提供全局响应式 auth 状态，任何组件都可以引用。
 * 状态通过 window.__AUTH_STORE__ 共享单例，避免多实例不同步。
 *
 * 使用方式：
 *   const { isLoggedIn, username, role, login, register, logout } = useAuth();
 */
import { ref } from 'vue';
import { post } from '../api/http.js';
import { saveAuth, getToken, getUser, clearAuth, isTokenExpired } from '../utils/auth.js';

// ===== 全局单例（确保 App.vue 和 AppLayout 共享同一份状态） =====
const shared = window.__AUTH_STORE__ || (() => {
  const token = getToken();
  const user = getUser();

  const state = {
    isLoggedIn: ref(!!token && !isTokenExpired()),
    username:   ref(user?.username || ''),
    role:       ref(user?.role || '')
  };

  // 如果 token 已过期，清除残留数据
  if (token && isTokenExpired()) {
    clearAuth();
    state.isLoggedIn.value = false;
  }

  window.__AUTH_STORE__ = state;
  return state;
})();

export function useAuth() {
  /** 登录 — 调用后端 /api/auth/login，成功后保存凭证 */
  async function login(username, password) {
    const data = await post('/api/auth/login', { username, password });
    saveAuth(data.token, data.username, data.role);
    shared.isLoggedIn.value = true;
    shared.username.value = data.username;
    shared.role.value = data.role;
    return data;
  }

  /** 注册 — 调用后端 /api/auth/register，成功后自动登录 */
  async function register(username, password) {
    const data = await post('/api/auth/register', { username, password });
    saveAuth(data.token, data.username, data.role);
    shared.isLoggedIn.value = true;
    shared.username.value = data.username;
    shared.role.value = data.role;
    return data;
  }

  /**
   * 登出 — 通知后端将 Token 加入 Redis 黑名单，然后清除本地凭证
   *
   * 注意：不能直接用 post() 因为 /api/auth/* 不在自动附加 Token 的白名单里。
   * 这里手动构造请求携带 Authorization 头。
   * 即使后端不可达也清除本地状态（降级处理）。
   */
  async function logout() {
    const token = getToken();
    try {
      if (token) {
        await api('/api/auth/logout', {
          method: 'POST',
          headers: { 'Authorization': `Bearer ${token}` }
        });
      }
    } catch {
      // 后端不可达时降级，本地清除即可
    }
    clearAuth();
    shared.isLoggedIn.value = false;
    shared.username.value = '';
    shared.role.value = '';
  }

  return {
    isLoggedIn: shared.isLoggedIn,
    username:   shared.username,
    role:       shared.role,
    login,
    register,
    logout
  };
}
