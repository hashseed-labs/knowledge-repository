<template>
  <div class="page">
    <div class="panel toolbar">
      <div>
        <div class="title">审计日志</div>
        <div class="desc">关键写操作留痕</div>
      </div>
      <div class="actions">
        <el-input v-model="action" placeholder="动作过滤，如 LOGIN" clearable style="width:200px" />
        <el-button @click="load">查询</el-button>
      </div>
    </div>
    <div class="panel" style="margin-top:16px">
      <el-table :data="records" v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户" width="120" />
        <el-table-column prop="action" label="动作" width="140" />
        <el-table-column prop="resourceType" label="资源类型" width="120" />
        <el-table-column prop="resourceId" label="资源ID" width="100" />
        <el-table-column label="结果" width="80">
          <template #default="{ row }">
            <el-tag :type="row.success ? 'success' : 'danger'" size="small">{{ row.success ? '成功' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" width="140" />
        <el-table-column prop="createdAt" label="时间" width="180" />
        <el-table-column prop="detail" label="详情" min-width="200" show-overflow-tooltip />
      </el-table>
      <div class="pager">
        <el-pagination background layout="total, prev, pager, next" :total="total" :page-size="size" :current-page="page" @current-change="(p)=>{page=p;load()}" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { listAudits } from '../api/admin'

const records = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const action = ref('')

async function load() {
  loading.value = true
  try {
    const data = await listAudits(page.value, size.value, action.value)
    records.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar { display:flex; justify-content:space-between; align-items:center; gap:12px; flex-wrap:wrap; }
.title { font-weight:600; }
.desc { color:var(--muted); font-size:13px; margin-top:4px; }
.actions { display:flex; gap:8px; }
.pager { display:flex; justify-content:flex-end; margin-top:16px; }
</style>
