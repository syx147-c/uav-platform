<script setup>
/**
 * 应用根组件
 * 直接渲染 Dashboard 驾驶舱页面
 */
import Dashboard from './views/Dashboard.vue';            // 导入驾驶舱主页
</script>

<template>
  <Dashboard />                                           <!-- 渲染驾驶舱 -->
</template>

<style>
/* 全局样式重置 */
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
  overflow: hidden;
}
</style>
