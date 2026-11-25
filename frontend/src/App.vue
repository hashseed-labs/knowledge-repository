<template>
  <router-view v-if="isLoginPage" />
  <el-container v-else class="layout">
    <el-aside width="220px" class="aside">
      <div class="brand">Knowledge KB</div>
      <el-menu :default-active="route.path" router>
        <el-menu-item index="/health">
          <el-icon><Monitor /></el-icon>
          <span>依赖状态</span>
        </el-menu-item>
        <el-menu-item v-if="auth.hasPermission('kb:doc:read')" index="/docs">
          <el-icon><Document /></el-icon>
          <span>文档管理</span>
        </el-menu-item>
        <el-menu-item v-if="auth.hasPermission('kb:chat:ask')" index="/chat">
          <el-icon><ChatDotRound /></el-icon>
          <span>知识问答</span>
        </el-menu-item>
        <el-menu-item index="/spaces">
          <el-icon><FolderOpened /></el-icon>
          <span>知识库</span>
        </el-menu-item>
        <el-menu-item v-if="auth.hasPermission('tenant:user:manage')" index="/users">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item v-if="auth.hasPermission('tenant:role:manage')" index="/roles">
          <el-icon><Key /></el-icon>
          <span>角色权限</span>
        </el-menu-item>
        <el-menu-item v-if="auth.hasPermission('tenant:audit:view')" index="/audits">
          <el-icon><Notebook /></el-icon>
          <span>审计日志</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div>{{ title }}</div>
        <div class="header-right">
          <el-select
            v-if="tenants.length"
            :model-value="ws.tenantId"
            placeholder="组织"
            style="width: 150px"
            @change="onTenantChange"
          >
            <el-option v-for="t in tenants" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
          <el-select
            v-if="ws.spaces.length"
            :model-value="ws.kbId"
            placeholder="知识库"
            style="width: 160px"
            @change="onKbChange"
          >
            <el-option v-for="s in ws.spaces" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
          <span class="user">{{ auth.user?.displayName || auth.user?.username }}</span>
          <el-button link type="primary" @click="onLogout">退出</el-button>
        </div>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { logout } from './api/auth'
import { listSpaces } from './api/admin'
import { useAuthStore } from './stores/auth'
import { useWorkspaceStore } from './stores/workspace'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const ws = useWorkspaceStore()

const isLoginPage = computed(() => route.path === '/login')
const tenants = computed(() => auth.user?.tenants || [])

const titleMap = {
  '/docs': '文档管理',
  '/chat': '知识库问答',
  '/health': '依赖状态',
  '/users': '用户管理',
  '/roles': '角色权限',
  '/spaces': '知识库管理',
  '/audits': '审计日志',
}
const title = computed(() => titleMap[route.path] || 'Knowledge KB')

async function refreshSpaces() {
  if (!auth.isLogin || !ws.tenantId) return
  try {
    const spaces = await listSpaces()
    ws.setSpaces(spaces)
  } catch {
    // ignore
  }
}

async function onTenantChange(id) {
  ws.setTenantId(id)
  ws.setKbId(null)
  await refreshSpaces()
}

function onKbChange(id) {
  ws.setKbId(id)
}

async function onLogout() {
  try {
    await logout(auth.refreshToken)
  } catch {
    // ignore
  }
  auth.clear()
  ws.clear()
  router.push('/login')
}

onMounted(() => {
  if (auth.isLogin) refreshSpaces()
})

watch(
  () => auth.isLogin,
  (v) => {
    if (v) refreshSpaces()
  },
)
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
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid var(--line);
  font-size: 18px;
  font-weight: 600;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.user {
  font-size: 14px;
  font-weight: 500;
  color: #475569;
}
</style>
