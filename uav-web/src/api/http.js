/**
 * HTTP 请求封装 — 自动携带 JWT Bearer Token
 *
 * 所有业务 API 调用统一使用此模块，确保 Authorization 头始终正确。
 * 如果后端返回 401，自动清除本地凭证并跳转登录页。
 */
import { getToken, clearAuth } from '../utils/auth.js';

const BASE = '';

/**
 * 发起带 JWT 认证的 HTTP 请求
 *
 * @param {string} url     - API 路径（如 /api/drone/telemetry）
 * @param {object} options - fetch 选项（method, body, headers 等）
 * @returns {Promise<object>} 解析后的 JSON 响应
 */
export async function api(url, options = {}) {
  const token = getToken();

  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {})
  };

  // 对非登录/注册接口自动附加 JWT Token
  if (token && !url.startsWith('/api/auth')) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const res = await fetch(BASE + url, {
    ...options,
    headers
  });

  // Token 过期或无效 → 清除凭证并跳转登录
  if (res.status === 401) {
    clearAuth();
    // 避免在登录页原地跳转
    if (window.location.pathname !== '/login') {
      window.location.href = '/login';
    }
    throw new Error('认证已过期，请重新登录');
  }

  // 解析 JSON（即使 res.ok 为 false 也尝试解析错误信息）
  const data = await res.json().catch(() => ({}));

  if (!res.ok) {
    const msg = data.error || data.message || `HTTP ${res.status}`;
    throw new Error(msg);
  }

  return data;
}

/** GET 请求快捷方法 */
export function get(url) {
  return api(url, { method: 'GET' });
}

/** POST 请求快捷方法 */
export function post(url, body) {
  return api(url, {
    method: 'POST',
    body: JSON.stringify(body)
  });
}
