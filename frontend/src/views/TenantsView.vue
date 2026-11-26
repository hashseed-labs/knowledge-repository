<template>
  <div class="page">
    <div class="panel toolbar">
      <div>
        <div class="title">组织管理</div>
        <div class="desc">维护租户组织与成员关系；新建组织会自动加入创建人并创建默认知识库</div>
      </div>
      <div class="actions">
        <el-input
          v-model="keyword"
          placeholder="搜索编码/名称"
          clearable
          style="width: 200px"
          @keyup.enter="load"
        />
        <el-button @click="load">查询</el-button>
        <el-button type="primary" @click="openCreate">新建组织</el-button>
      </div>
    </div>

    <div class="panel" style="margin-top: 16px">
      <el-table :data="records" v-loading="loading" empty-text="暂无组织">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="code" label="编码" min-width="120" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="memberCount" label="成员数" width="90" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="openMembers(row)">成员</el-button>
            <el-button link type="success" @click="switchTo(row)">切换使用</el-button>
            <el-button
              link
              type="danger"
              :disabled="row.code === 'default' || row.status === 'DISABLED'"
              @click="onDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :total="total"
          :page-size="size"
          :current-page="page"
          @current-change="onPage"
        />
      </div>
    </div>

    <el-dialog v-model="visible" :title="editing ? '编辑组织' : '新建组织'" width="460px" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="编码" v-if="!editing">
          <el-input v-model="form.code" placeholder="如 acme，创建后不可改" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="组织显示名称" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="ACTIVE" value="ACTIVE" />
            <el-option label="DISABLED" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="memberVisible" :title="`组织成员 - ${current?.name || ''}`" width="720px" destroy-on-close>
      <div class="member-toolbar">
        <el-select
          v-model="memberForm.userId"
          filterable
          remote
          clearable
          reserve-keyword
          placeholder="搜索并选择用户"
          :remote-method="searchCandidates"
          :loading="candidateLoading"
          style="width: 240px"
        >
          <el-option
            v-for="u in candidates"
            :key="u.id"
            :label="`${u.displayName || u.username} (${u.username})`"
            :value="u.id"
          />
        </el-select>
        <el-select v-model="memberForm.roleIds" multiple clearable placeholder="组织角色" style="width: 260px">
          <el-option v-for="r in builtinRoles" :key="r.id" :label="r.name" :value="r.id" />
        </el-select>
        <el-button type="primary" :loading="memberSaving" @click="addMember">添加</el-button>
      </div>

      <el-table :data="members" v-loading="memberLoading" style="margin-top: 12px" empty-text="暂无成员">
        <el-table-column prop="username" label="用户名" min-width="110" />
        <el-table-column prop="displayName" label="显示名" min-width="110" />
        <el-table-column prop="roleName" label="角色" min-width="160" />
        <el-table-column label="默认组织" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault" size="small" type="success">是</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditRoles(row)">改角色</el-button>
            <el-button link type="danger" @click="removeMember(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="roleVisible" title="调整组织角色" width="420px" destroy-on-close>
      <el-select v-model="roleForm.roleIds" multiple style="width: 100%" placeholder="选择角色">
        <el-option v-for="r in builtinRoles" :key="r.id" :label="r.name" :value="r.id" />
      </el-select>
      <template #footer>
        <el-button @click="roleVisible = false">取消</el-button>
        <el-button type="primary" :loading="roleSaving" @click="saveRoles">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { me } from '../api/auth'
import {
  addTenantMember,
  createTenant,
  deleteTenant,
  listRoles,
  listSpaces,
  listTenantMemberCandidates,
  listTenantMembers,
  listTenants,
  removeTenantMember,
  updateTenant,
  updateTenantMember,
} from '../api/admin'
import { useAuthStore } from '../stores/auth'
import { useWorkspaceStore } from '../stores/workspace'

const auth = useAuthStore()
const ws = useWorkspaceStore()

const records = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const loading = ref(false)
const visible = ref(false)
const saving = ref(false)
const editing = ref(null)
const form = reactive({ code: '', name: '', status: 'ACTIVE' })

const memberVisible = ref(false)
const memberLoading = ref(false)
const memberSaving = ref(false)
const current = ref(null)
const members = ref([])
const candidates = ref([])
const candidateLoading = ref(false)
const roles = ref([])
const builtinRoles = computed(() => (roles.value || []).filter((r) => !r.tenantId || r.tenantId === 0))
const memberForm = reactive({ userId: null, roleIds: [] })

const roleVisible = ref(false)
const roleSaving = ref(false)
const roleEditing = ref(null)
const roleForm = reactive({ roleIds: [] })

async function load() {
  loading.value = true
  try {
    const data = await listTenants(page.value, size.value, keyword.value)
    records.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function onPage(p) {
  page.value = p
  load()
}

function openCreate() {
  editing.value = null
  Object.assign(form, { code: '', name: '', status: 'ACTIVE' })
  visible.value = true
}

function openEdit(row) {
  editing.value = row
  Object.assign(form, { code: row.code, name: row.name, status: row.status || 'ACTIVE' })
  visible.value = true
}

async function refreshMyTenants() {
  try {
    const profile = await me()
    auth.updateUser(profile)
  } catch {
    // ignore
  }
}

async function save() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写组织名称')
    return
  }
  if (!editing.value && !form.code.trim()) {
    ElMessage.warning('请填写组织编码')
    return
  }
  saving.value = true
  try {
    if (editing.value) {
      await updateTenant(editing.value.id, { name: form.name.trim(), status: form.status })
      ElMessage.success('已更新')
    } else {
      await createTenant({
        code: form.code.trim(),
        name: form.name.trim(),
        status: form.status,
      })
      ElMessage.success('已创建，请在顶栏切换到新组织')
      await refreshMyTenants()
    }
    visible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function openMembers(row) {
  current.value = row
  memberVisible.value = true
  memberForm.userId = null
  memberForm.roleIds = []
  candidates.value = []
  memberLoading.value = true
  try {
    roles.value = await listRoles()
    members.value = await listTenantMembers(row.id)
    await searchCandidates('')
  } finally {
    memberLoading.value = false
  }
}

async function searchCandidates(q) {
  if (!current.value) return
  candidateLoading.value = true
  try {
    candidates.value = await listTenantMemberCandidates(current.value.id, q || '')
  } finally {
    candidateLoading.value = false
  }
}

async function addMember() {
  if (!memberForm.userId) {
    ElMessage.warning('请选择用户')
    return
  }
  memberSaving.value = true
  try {
    await addTenantMember(current.value.id, {
      userId: memberForm.userId,
      roleIds: memberForm.roleIds,
    })
    ElMessage.success('已添加成员')
    memberForm.userId = null
    memberForm.roleIds = []
    members.value = await listTenantMembers(current.value.id)
    await searchCandidates('')
    await load()
    await refreshMyTenants()
  } finally {
    memberSaving.value = false
  }
}

function openEditRoles(row) {
  roleEditing.value = row
  roleForm.roleIds = [...(row.roleIds || [])]
  roleVisible.value = true
}

async function saveRoles() {
  roleSaving.value = true
  try {
    await updateTenantMember(current.value.id, roleEditing.value.userId, {
      roleIds: roleForm.roleIds,
    })
    ElMessage.success('角色已更新')
    roleVisible.value = false
    members.value = await listTenantMembers(current.value.id)
  } finally {
    roleSaving.value = false
  }
}

async function removeMember(row) {
  await ElMessageBox.confirm(`确认将「${row.displayName || row.username}」移出该组织？`, '移除成员', {
    type: 'warning',
  })
  await removeTenantMember(current.value.id, row.userId)
  ElMessage.success('已移除')
  members.value = await listTenantMembers(current.value.id)
  await searchCandidates('')
  await load()
  await refreshMyTenants()
}

async function onDelete(row) {
  if (row.code === 'default') {
    ElMessage.warning('默认组织不可删除')
    return
  }
  await ElMessageBox.confirm(
    `确认删除组织「${row.name}」？删除后将禁用该组织，成员无法再切换进入。`,
    '删除组织',
    { type: 'warning' },
  )
  await deleteTenant(row.id)
  ElMessage.success('已删除')
  if (Number(ws.tenantId) === Number(row.id)) {
    ws.setTenantId(null)
    ws.setKbId(null)
    ws.setSpaces([])
  }
  await refreshMyTenants()
  await load()
}

async function switchTo(row) {
  if (row.status !== 'ACTIVE') {
    ElMessage.warning('组织已禁用，无法切换')
    return
  }
  await refreshMyTenants()
  const joined = (auth.user?.tenants || []).some((t) => Number(t.id) === Number(row.id))
  if (!joined) {
    ElMessage.error('当前账号不在该组织中，请先在「成员」中添加自己')
    return
  }
  ws.setTenantId(row.id)
  ws.setKbId(null)
  try {
    const spaces = await listSpaces()
    ws.setSpaces(spaces)
  } catch {
    ws.setSpaces([])
  }
  ElMessage.success(`已切换到组织：${row.name}`)
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.title {
  font-weight: 600;
}
.desc {
  color: var(--muted);
  font-size: 13px;
  margin-top: 4px;
}
.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.member-toolbar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}
</style>
