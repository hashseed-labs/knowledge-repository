<template>
  <div class="page">
    <div class="panel toolbar">
      <div>
        <div class="title">角色权限</div>
        <div class="desc">自定义角色并勾选权限点</div>
      </div>
      <el-button type="primary" @click="openCreate">新建角色</el-button>
    </div>
    <div class="panel" style="margin-top:16px">
      <el-table :data="roles" v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="code" label="编码" />
        <el-table-column prop="name" label="名称" />
        <el-table-column label="内置" width="80">
          <template #default="{ row }">{{ row.builtin ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="权限数" width="100">
          <template #default="{ row }">{{ row.permissionIds?.length || 0 }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="visible" :title="editing ? '编辑角色' : '新建角色'" width="560px">
      <el-form label-width="80px">
        <el-form-item label="编码"><el-input v-model="form.code" :disabled="!!editing" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="权限">
          <el-checkbox-group v-model="form.permissionIds">
            <el-checkbox v-for="p in permissions" :key="p.id" :label="p.id" :value="p.id">
              {{ p.name }} ({{ p.code }})
            </el-checkbox>
          </el-checkbox-group>
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
import { createRole, listPermissions, listRoles, updateRole } from '../api/admin'

const roles = ref([])
const permissions = ref([])
const loading = ref(false)
const visible = ref(false)
const saving = ref(false)
const editing = ref(null)
const form = reactive({ code: '', name: '', permissionIds: [] })

async function load() {
  loading.value = true
  try {
    roles.value = await listRoles()
    permissions.value = await listPermissions()
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  Object.assign(form, { code: '', name: '', permissionIds: [] })
  visible.value = true
}

function openEdit(row) {
  editing.value = row
  Object.assign(form, { code: row.code, name: row.name, permissionIds: [...(row.permissionIds || [])] })
  visible.value = true
}

async function save() {
  saving.value = true
  try {
    if (editing.value) await updateRole(editing.value.id, { name: form.name, permissionIds: form.permissionIds })
    else await createRole({ ...form })
    ElMessage.success('已保存')
    visible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar { display:flex; justify-content:space-between; align-items:center; }
.title { font-weight:600; }
.desc { color:var(--muted); font-size:13px; margin-top:4px; }
.el-checkbox { display:block; margin: 6px 0; }
</style>
