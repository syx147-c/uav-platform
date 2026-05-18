import { createRouter, createWebHistory } from 'vue-router';      // Vue Router 4
import { getToken, isTokenExpired, clearAuth } from '../utils/auth.js';

/**
 * 路由配置
 * - 登录页公开访问
 * - 其他页面需要 JWT 认证（通过导航守卫拦截）
 * - 采用懒加载优化首屏性能
 */
const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/LoginView.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('../views/Dashboard.vue'),
    meta: { title: '驾驶舱', icon: 'Odometer', requiresAuth: true }
  },
  {
    path: '/charts',
    name: 'Charts',
    component: () => import('../views/Charts.vue'),
    meta: { title: '数据图表', icon: 'DataLine', requiresAuth: true }
  },
  {
    path: '/missions',
    name: 'Missions',
    component: () => import('../views/MissionManager.vue'),
    meta: { title: '任务管理', icon: 'List', requiresAuth: true }
  },
  {
    path: '/logs',
    name: 'Logs',
    component: () => import('../views/FlightLogs.vue'),
    meta: { title: '飞行日志', icon: 'Document', requiresAuth: true }
  },
  {
    path: '/chat',
    name: 'Chat',
    component: () => import('../views/ChatConsole.vue'),
    meta: { title: 'AI 控制台', icon: 'ChatDotSquare', requiresAuth: true }
  },
  {
    // 404 兜底 — 重定向到驾驶舱
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard'
  }
];

const router = createRouter({
  history: createWebHistory(), // HTML5 History 模式（无 # 号）
  routes
});

/**
 * 全局前置守卫 — JWT 认证检查
 *
 * 逻辑：
 * 1. 目标路由标记为 public → 直接放行（已登录用户访问 login 页则重定向到 dashboard）
 * 2. 目标路由需要认证但没有有效 Token → 跳转 /login
 * 3. Token 存在且有效 → 放行
 */
router.beforeEach((to, from, next) => {
  const token = getToken();
  const hasValidToken = token && !isTokenExpired();

  // 公开页面（登录页）
  if (to.meta.public) {
    if (hasValidToken && to.path === '/login') {
      // 已登录用户不要重复登录
      return next('/dashboard');
    }
    return next();
  }

  // 需要认证的页面
  if (!hasValidToken) {
    // 清除可能残留的过期数据
    clearAuth();
    // 将目标路径作为 redirect 参数传给登录页
    return next({ path: '/login', query: { redirect: to.fullPath } });
  }

  next();
});

export default router;
