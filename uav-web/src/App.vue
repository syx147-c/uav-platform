<script setup>
/**
 * 应用根组件 — 渲染主布局
 */
import AppLayout from './layout/AppLayout.vue';   // 主布局（含侧边栏 + 路由出口）
</script>

<template>
  <AppLayout />
</template>

<style>
/* 全局样式重置 */
* { margin: 0; padding: 0; box-sizing: border-box; }
body { overflow: hidden; }
</style>
