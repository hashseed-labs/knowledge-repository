<template>
  <div class="page">
    <div class="panel toolbar">
      <div>
        <div class="title">知识库管理</div>
        <div class="desc">创建知识库并管理成员</div>
      </div>
      <el-button v-if="auth.hasPermission('kb:space:manage')" type="primary" @click="openCreate">新建知识库</el-button>
    </div>
    <div class="panel" style="margin-top:16px">
      <el-table :data="spaces" v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="description" label="描述" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button link type="primary" @click="useSpace(row)">切换使用</el-button>
            <el-button v-if="auth.hasPermission('kb:member:manage')" link type="primary" @click="openMembers(row)">成员</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="createVisible" title="新建知识库" width="420px">
      <el-form label-width="70px">
        <el-form-item label="名称"><el-input v-model="createForm.name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="createForm.description" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible=false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="create">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="memberVisible" :title="`成员 - ${current?.name || ''}`" width="640px">
      <div class="member-toolbar">
        <el-select v-model="memberForm.userId" filterable placeholder="用户" style="width:180px">
          <el-option v-for="u in users" :key="u.id" :label="`${u.displayName}(${u.username})`" :value="u.id" />
        </el-select>
        <el-select v-model="memberForm.roleId" placeholder="角色" style="width:180px">
          <el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.id" />
        </el-select>
        <el-button type="primary" @click="add">添加</el-button>
      </div>
      <el-table :data="members" style="margin-top:12px">
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="displayName" label="显示名" />
        <el-table-column prop="roleName" label="角色" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="danger" @click="remove(row.userId)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { addMember, createSpace, listMembers, listRoles, listSpaces, listUsers, removeMember } from '../api/admin'
import { useAuthStore } from '../stores/auth'
import { useWorkspaceStore } from '../stores/workspace'

const auth = useAuthStore()
const ws = useWorkspaceStore()
const spaces = ref([])
const loading = ref(false)
const createVisible = ref(false)
const memberVisible = ref(false)
const saving = ref(false)
const current = ref(null)
const members = ref([])
const users = ref([])
const roles = ref([])
const createForm = reactive({ name: '', description: '' })
const memberForm = reactive({ userId: null, roleId: null })

async function load() {
  loading.value = true
  try {
    spaces.value = await listSpaces()
    ws.setSpaces(spaces.value)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  createForm.name = ''
  createForm.description = ''
  createVisible.value = true
}

async function create() {
  saving.value = true
  try {
    await createSpace(createForm)
    ElMessage.success('已创建')
    createVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

function useSpace(row) {
  // 确保顶栏下拉的 options 与当前列表一致，并用统一数字 ID 触发联动
  ws.setSpaces(spaces.value)
  ws.setKbId(row.id)
  ElMessage.success(`已切换到知识库：${row.name}`)
}

async function openMembers(row) {
  current.value = row
  members.value = await listMembers(row.id)
  const userPage = await listUsers(1, 100)
  users.value = userPage.records || []
  roles.value = await listRoles()
  memberForm.userId = null
  memberForm.roleId = null
  memberVisible.value = true
}

async function add() {
  await addMember(current.value.id, { ...memberForm })
  members.value = await listMembers(current.value.id)
  ElMessage.success('已添加')
}

async function remove(userId) {
  await removeMember(current.value.id, userId)
  members.value = await listMembers(current.value.id)
}

onMounted(load)
</script>

<style scoped>
.toolbar { display:flex; justify-content:space-between; align-items:center; }
.title { font-weight:600; }
.desc { color:var(--muted); font-size:13px; margin-top:4px; }
.member-toolbar { display:flex; gap:8px; flex-wrap:wrap; }
</style>
