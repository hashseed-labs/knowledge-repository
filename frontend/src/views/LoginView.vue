<template>
  <div class="login-page">
    <div class="login-card">
      <h1>Knowledge KB</h1>
      <p class="sub">企业知识库 · 请登录</p>
      <el-form :model="form" @submit.prevent="onSubmit">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" show-password prefix-icon="Lock" />
        </el-form-item>
        <el-button type="primary" class="btn" :loading="loading" native-type="submit">登录</el-button>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '../api/auth'
import { listSpaces } from '../api/admin'
import { useAuthStore } from '../stores/auth'
import { useWorkspaceStore } from '../stores/workspace'

const auth = useAuthStore()
const ws = useWorkspaceStore()
const router = useRouter()
const route = useRoute()
const loading = ref(false)
const form = reactive({ username: 'admin', password: 'admin123' })

async function onSubmit() {
  loading.value = true
  try {
    const data = await login(form)
    auth.setSession(data)
    const defaultTenant = data.user?.tenants?.find((t) => t.isDefault || t.default) || data.user?.tenants?.[0]
    if (defaultTenant) ws.setTenantId(defaultTenant.id)
    else if (data.user?.tenantId) ws.setTenantId(data.user.tenantId)
    const spaces = await listSpaces()
    ws.setSpaces(spaces)
    ElMessage.success('登录成功')
    const fallback = auth.hasPermission('system:health:view') ? '/health' : '/spaces'
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    router.replace(redirect || fallback)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(160deg, #0f172a, #1e3a5f 45%, #f8fafc);
}
.login-card {
  width: 380px;
  background: #fff;
  border-radius: 14px;
  padding: 32px 28px;
  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.25);
}
h1 {
  margin: 0;
  font-size: 24px;
}
.sub {
  color: #64748b;
  margin: 8px 0 24px;
}
.btn {
  width: 100%;
}
.tip {
  margin-top: 16px;
  font-size: 12px;
  color: #94a3b8;
  text-align: center;
}
</style>
