<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="brand">Knowledge KB</div>
      <el-menu :default-active="route.path" router>
        <el-menu-item index="/health">
          <el-icon><Monitor /></el-icon>
          <span>依赖状态</span>
        </el-menu-item>
        <el-menu-item index="/docs">
          <el-icon><Document /></el-icon>
          <span>文档管理</span>
        </el-menu-item>
        <el-menu-item index="/chat">
          <el-icon><ChatDotRound /></el-icon>
          <span>知识问答</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div>{{ title }}</div>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const titleMap = {
  '/docs': '文档管理',
  '/chat': '知识库问答',
  '/health': '依赖状态',
}

const title = computed(() => titleMap[route.path] || 'Knowledge KB')
</script>

<style scoped>
.layout {
  min-height: 100vh;
}
.aside {
  background: #111827;
  color: #fff;
}
.brand {
  height: 60px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  font-weight: 700;
  letter-spacing: 0.3px;
}
.aside :deep(.el-menu) {
  border-right: none;
  background: transparent;
}
.aside :deep(.el-menu-item) {
  color: #d1d5db;
}
.aside :deep(.el-menu-item.is-active) {
  background: #1f2937;
  color: #fff;
}
.header {
  display: flex;
  align-items: center;
  background: #fff;
  border-bottom: 1px solid var(--line);
  font-size: 18px;
  font-weight: 600;
}
</style>
