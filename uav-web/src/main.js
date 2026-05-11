import { createApp } from 'vue';                  // Vue 3 应用入口
import App from './App.vue';                       // 根组件
import router from './router/index.js';            // Vue Router 路由配置
import ElementPlus from 'element-plus';            // Element Plus UI 组件库
import 'element-plus/dist/index.css';              // Element Plus 样式
import * as ElementPlusIconsVue from '@element-plus/icons-vue'; // Element Plus 图标集

const app = createApp(App);                        // 创建 Vue 应用实例

// 注册所有 Element Plus 图标组件（在模板中可直接使用 <Refresh /> 等）
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component);
}

app.use(router);                                   // 激活路由系统
app.use(ElementPlus);                              // 激活 Element Plus
app.mount('#app');                                 // 挂载到 DOM
