<script setup>
/**
 * 应用根组件
 *
 * 路由分发：
 * - /login（公开页面）→ 直接渲染 <router-view />，不包裹主布局
 * - 其他所有页面     → 渲染 <AppLayout />（侧边栏 + 顶栏 + 内容区）
 */
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import AppLayout from './layout/AppLayout.vue';

const route = useRoute();

/** 是否为公开页面（登录页） */
const isPublicPage = computed(() => route.meta.public === true);
</script>

<template>
  <!-- 登录页 → 纯净背景，无布局 -->
  <router-view v-if="isPublicPage" />
  <!-- 主应用 → 侧边栏 + 顶栏布局 -->
  <AppLayout v-else />
</template>

<style>
/* 全局样式重置 */
* { margin: 0; padding: 0; box-sizing: border-box; }
body { overflow: hidden; }
</style>
