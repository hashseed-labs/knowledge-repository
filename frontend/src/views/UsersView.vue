<template>
  <div class="page">
    <div class="panel toolbar">
      <div>
        <div class="title">用户管理</div>
        <div class="desc">组织内用户与角色绑定</div>
      </div>
      <div class="actions">
        <el-input v-model="keyword" placeholder="搜索用户" clearable style="width: 200px" @keyup.enter="load" />
        <el-button @click="load">查询</el-button>
        <el-button type="primary" @click="openCreate">新建用户</el-button>
      </div>
    </div>
    <div class="panel" style="margin-top: 16px">
      <el-table :data="records" v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="displayName" label="显示名" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column label="角色" min-width="180">
          <template #default="{ row }">{{ roleNames(row.roleIds) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination background layout="total, prev, pager, next" :total="total" :page-size="size" :current-page="page" @current-change="(p)=>{page=p;load()}" />
      </div>
    </div>

    <el-dialog v-model="visible" :title="editing ? '编辑用户' : '新建用户'" width="480px">
      <el-form label-width="80px">
        <el-form-item label="用户名" v-if="!editing"><el-input v-model="form.username" /></el-form-item>
        <el-form-item label="显示名"><el-input v-model="form.displayName" /></el-form-item>
        <el-form-item :label="editing ? '新密码' : '密码'"><el-input v-model="form.password" type="password" show-password /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%"><el-option label="ACTIVE" value="ACTIVE" /><el-option label="DISABLED" value="DISABLED" /></el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleIds" multiple style="width: 100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible=false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createUser, listRoles, listUsers, updateUser } from '../api/admin'

const records = ref([])
const roles = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const loading = ref(false)
const visible = ref(false)
const saving = ref(false)
const editing = ref(null)
const form = reactive({ username: '', displayName: '', password: '', status: 'ACTIVE', roleIds: [] })

function roleNames(ids) {
  if (!ids?.length) return '-'
  return ids.map((id) => roles.value.find((r) => r.id === id)?.name || id).join(', ')
}

async function load() {
  loading.value = true
  try {
    const data = await listUsers(page.value, size.value, keyword.value)
    records.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  Object.assign(form, { username: '', displayName: '', password: '', status: 'ACTIVE', roleIds: [] })
  visible.value = true
}

function openEdit(row) {
  editing.value = row
  Object.assign(form, { username: row.username, displayName: row.displayName, password: '', status: row.status, roleIds: [...(row.roleIds || [])] })
  visible.value = true
}

async function save() {
  saving.value = true
  try {
    if (editing.value) {
      await updateUser(editing.value.id, { displayName: form.displayName, password: form.password || undefined, status: form.status, roleIds: form.roleIds })
    } else {
      await createUser({ ...form })
    }
    ElMessage.success('已保存')
    visible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  roles.value = await listRoles()
  await load()
})
</script>

<style scoped>
.toolbar { display:flex; justify-content:space-between; gap:12px; flex-wrap:wrap; align-items:center; }
.title { font-weight:600; }
.desc { color:var(--muted); font-size:13px; margin-top:4px; }
.actions { display:flex; gap:8px; }
.pager { display:flex; justify-content:flex-end; margin-top:16px; }
</style>
