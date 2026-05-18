/**
 * JWT Token 存储与解析工具
 *
 * 职责：localStorage 读写 + JWT payload 解析
 * 不包含响应式状态 —— 响应式状态见 useAuth.js
 */

const TOKEN_KEY = 'uav_token';
const USER_KEY = 'uav_user';

/** 保存登录凭证到本地存储 */
export function saveAuth(token, username, role) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USER_KEY, JSON.stringify({ username, role }));
}

/** 读取 Token */
export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || '';
}

/** 读取用户信息 */
export function getUser() {
  try {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

/** 清除登录凭证 */
export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

/** 粗略检查 Token 是否过期（不验证签名，仅解析 payload 中的 exp 字段） */
export function isTokenExpired() {
  const token = getToken();
  if (!token) return true;
  try {
    // JWT 格式: header.payload.signature
    const payload = JSON.parse(atob(token.split('.')[1]));
    return payload.exp * 1000 < Date.now();
  } catch {
    return true;
  }
}
