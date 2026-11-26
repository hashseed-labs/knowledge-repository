<template>
  <router-view v-if="isLoginPage" />
  <el-container v-else class="layout">
    <el-aside width="232px" class="aside">
      <div class="brand">
        <span class="brand-mark">KB</span>
        <div class="brand-text">
          <div class="brand-name">Knowledge KB</div>
          <div class="brand-sub">企业知识库</div>
        </div>
      </div>

      <el-menu
        class="side-menu"
        :default-active="activeMenu"
        router
        :collapse-transition="false"
      >
        <template v-if="auth.hasPermission('system:health:view')">
          <div class="menu-group">系统</div>
          <el-menu-item index="/health">
            <el-icon><Monitor /></el-icon>
            <span>依赖状态</span>
          </el-menu-item>
        </template>

        <div class="menu-group">知识</div>
        <el-menu-item index="/spaces">
          <el-icon><FolderOpened /></el-icon>
          <span>知识库</span>
        </el-menu-item>
        <el-menu-item v-if="auth.hasPermission('kb:doc:read')" index="/docs">
          <el-icon><Document /></el-icon>
          <span>文档管理</span>
        </el-menu-item>
        <el-menu-item v-if="auth.hasPermission('kb:chat:ask')" index="/chat">
          <el-icon><ChatDotRound /></el-icon>
          <span>知识问答</span>
        </el-menu-item>
        <el-menu-item v-if="auth.hasPermission('exam:paper:read')" index="/exam">
          <el-icon><EditPen /></el-icon>
          <span>在线考试</span>
        </el-menu-item>

        <template v-if="showAdminGroup">
          <div class="menu-group">管理</div>
          <el-menu-item v-if="auth.hasPermission('tenant:org:manage')" index="/tenants">
            <el-icon><OfficeBuilding /></el-icon>
            <span>组织管理</span>
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
        </template>
      </el-menu>
    </el-aside>

    <el-container class="main-wrap">
      <el-header class="header">
        <div class="header-title">{{ title }}</div>
        <div class="header-right">
          <el-select
            v-if="tenants.length"
            :model-value="ws.tenantId"
            placeholder="组织"
            class="header-select"
            @change="onTenantChange"
          >
            <el-option
              v-for="t in tenants"
              :key="t.id"
              :label="t.name"
              :value="Number(t.id)"
            />
          </el-select>
          <el-select
            v-if="ws.spaces.length"
            :key="`kb-${ws.kbId}`"
            :model-value="ws.kbId"
            placeholder="知识库"
            class="header-select"
            @change="onKbChange"
          >
            <el-option
              v-for="s in ws.spaces"
              :key="s.id"
              :label="s.name"
              :value="Number(s.id)"
            />
          </el-select>
          <span class="user">{{ auth.user?.displayName || auth.user?.username }}</span>
          <el-button link type="primary" @click="onLogout">退出</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { logout, me } from './api/auth'
import { listSpaces } from './api/admin'
import { useAuthStore } from './stores/auth'
import { useWorkspaceStore } from './stores/workspace'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const ws = useWorkspaceStore()

const isLoginPage = computed(() => route.path === '/login')
const tenants = computed(() => auth.user?.tenants || [])
const showAdminGroup = computed(
  () =>
    auth.hasPermission('tenant:org:manage') ||
    auth.hasPermission('tenant:user:manage') ||
    auth.hasPermission('tenant:role:manage') ||
    auth.hasPermission('tenant:audit:view'),
)

const activeMenu = computed(() => {
  const path = route.path
  if (path.startsWith('/exam')) return '/exam'
  return path
})

const titleMap = {
  '/docs': '文档管理',
  '/chat': '知识库问答',
  '/exam': '在线考试',
  '/health': '依赖状态',
  '/tenants': '组织管理',
  '/users': '用户管理',
  '/roles': '角色权限',
  '/spaces': '知识库管理',
  '/audits': '审计日志',
}
const title = computed(() => {
  if (route.path.startsWith('/exam/attempts/')) return '考试作答'
  return titleMap[route.path] || 'Knowledge KB'
})

async function refreshProfile() {
  if (!auth.isLogin) return
  try {
    const profile = await me()
    auth.updateUser(profile)
    if (profile?.tenantId && !ws.tenantId) {
      ws.setTenantId(profile.tenantId)
    }
  } catch {
    // ignore; keep cached session
  }
}

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

onMounted(async () => {
  if (!auth.isLogin) return
  await refreshProfile()
  await refreshSpaces()
})

watch(
  () => auth.isLogin,
  async (v) => {
    if (!v) return
    await refreshProfile()
    await refreshSpaces()
  },
)
</script>

<style scoped>
.layout {
  min-height: 100vh;
  background: var(--bg);
}

.aside {
  display: flex;
  flex-direction: column;
  background:
    linear-gradient(180deg, #0f172a 0%, #111827 48%, #0b1220 100%);
  color: #fff;
  border-right: 1px solid rgba(148, 163, 184, 0.12);
}

.brand {
  height: 72px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 18px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.12);
}

.brand-mark {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.4px;
  color: #eff6ff;
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  box-shadow: 0 6px 16px rgba(37, 99, 235, 0.28);
}

.brand-text {
  min-width: 0;
}

.brand-name {
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.2px;
  line-height: 1.2;
}

.brand-sub {
  margin-top: 2px;
  font-size: 12px;
  color: #94a3b8;
}

.side-menu {
  flex: 1;
  border-right: none !important;
  background: transparent !important;
  padding: 12px 10px 20px;
}

.menu-group {
  margin: 14px 12px 8px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #64748b;
}

.aside :deep(.el-menu-item) {
  height: 42px;
  line-height: 42px;
  margin: 2px 0;
  border-radius: 10px;
  color: #cbd5e1;
  transition: background 0.18s ease, color 0.18s ease;
}

.aside :deep(.el-menu-item .el-icon) {
  font-size: 16px;
  color: #94a3b8;
  transition: color 0.18s ease;
}

.aside :deep(.el-menu-item:hover) {
  background: rgba(148, 163, 184, 0.1);
  color: #f8fafc;
}

.aside :deep(.el-menu-item:hover .el-icon) {
  color: #e2e8f0;
}

.aside :deep(.el-menu-item.is-active) {
  background: rgba(37, 99, 235, 0.18);
  color: #eff6ff;
  font-weight: 600;
  box-shadow: inset 3px 0 0 #3b82f6;
}

.aside :deep(.el-menu-item.is-active .el-icon) {
  color: #93c5fd;
}

.main-wrap {
  min-width: 0;
}

.header {
  height: 64px !important;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--line);
}

.header-title {
  font-size: 18px;
  font-weight: 650;
  color: #0f172a;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-select {
  width: 150px;
}

.user {
  font-size: 14px;
  font-weight: 500;
  color: #475569;
  padding: 6px 10px;
  border-radius: 999px;
  background: #f1f5f9;
}

.main {
  padding: 0;
  background: var(--bg);
}
</style>
