import { createRouter, createWebHistory } from 'vue-router'      // Vue Router 4

/**
 * 路由配置
 * 采用侧边栏导航的多页面结构
 */
const routes = [
  {
    path: '/',
    redirect: '/dashboard'                                       // 根路径自动跳转到驾驶舱
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('../views/Dashboard.vue'),           // 懒加载：驾驶舱主页
    meta: { title: '驾驶舱', icon: 'Odometer' }                   // 页面元信息（侧边栏用）
  },
  {
    path: '/charts',
    name: 'Charts',
    component: () => import('../views/Charts.vue'),             // 懒加载：数据图表页
    meta: { title: '数据图表', icon: 'DataLine' }
  },
  {
    path: '/missions',
    name: 'Missions',
    component: () => import('../views/MissionManager.vue'),      // 懒加载：任务管理页
    meta: { title: '任务管理', icon: 'List' }
  },
  {
    path: '/logs',
    name: 'Logs',
    component: () => import('../views/FlightLogs.vue'),         // 懒加载：飞行日志页
    meta: { title: '飞行日志', icon: 'Document' }
  }
];

const router = createRouter({
  history: createWebHistory(),                                   // HTML5 History 模式（无 # 号）
  routes
});

export default router;
