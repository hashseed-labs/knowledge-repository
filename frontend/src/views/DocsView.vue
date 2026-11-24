<template>
  <div class="page">
    <div class="panel toolbar">
      <div>
        <div class="title">Markdown 文档</div>
        <div class="desc">上传后自动分块、向量化并写入 Milvus</div>
      </div>
      <el-upload
        :show-file-list="false"
        :http-request="handleUpload"
        accept=".md"
      >
        <el-button type="primary" :loading="uploading">上传 .md</el-button>
      </el-upload>
    </div>

    <div class="panel" style="margin-top: 16px">
      <el-table :data="records" v-loading="loading" empty-text="暂无文档">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="标题" min-width="220" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="分块数" width="100" />
        <el-table-column label="错误信息" min-width="220">
          <template #default="{ row }">
            <span class="error">{{ row.errorMsg || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="refreshOne(row.id)">刷新</el-button>
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
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
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteDocument, getDocument, listDocuments, uploadDocument } from '../api/document'

const records = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const uploading = ref(false)
let timer = null

function statusType(status) {
  if (status === 'READY') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PROCESSING') return 'warning'
  return 'info'
}

async function load() {
  loading.value = true
  try {
    const data = await listDocuments(page.value, size.value)
    records.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

async function handleUpload({ file }) {
  uploading.value = true
  try {
    await uploadDocument(file)
    ElMessage.success('上传成功，正在处理')
    page.value = 1
    await load()
  } finally {
    uploading.value = false
  }
}

async function refreshOne(id) {
  const doc = await getDocument(id)
  const idx = records.value.findIndex((r) => r.id === id)
  if (idx >= 0) records.value[idx] = doc
}

async function onDelete(row) {
  await ElMessageBox.confirm(`确认删除文档「${row.title}」？`, '提示', { type: 'warning' })
  await deleteDocument(row.id)
  ElMessage.success('已删除')
  await load()
}

function onPage(p) {
  page.value = p
  load()
}

onMounted(() => {
  load()
  timer = setInterval(() => {
    const busy = records.value.some((r) => r.status === 'PENDING' || r.status === 'PROCESSING')
    if (busy) load()
  }, 3000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}
.title {
  font-size: 16px;
  font-weight: 600;
}
.desc {
  color: var(--muted);
  margin-top: 4px;
  font-size: 13px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.error {
  color: #b91c1c;
  font-size: 12px;
}
</style>
