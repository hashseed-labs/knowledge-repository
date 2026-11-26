<template>
  <div class="page">
    <div class="panel toolbar">
      <div>
        <div class="title">依赖状态</div>
        <div class="desc">检测 MySQL / MinIO / Milvus / Embedding / LLM / Etcd 连通性</div>
      </div>
      <div class="actions">
        <el-tag :type="overallTag" size="large">整体: {{ overallText }}</el-tag>
        <el-button type="primary" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="panel" style="margin-top: 16px" v-loading="loading">
      <el-row :gutter="16">
        <el-col
          v-for="item in items"
          :key="item.key"
          :xs="24"
          :sm="12"
          :md="8"
          :lg="8"
        >
          <div class="dep-card" :class="item.ok ? 'ok' : 'bad'">
            <div class="dep-top">
              <div class="dep-name">{{ item.label }}</div>
              <el-tag :type="item.ok ? 'success' : 'danger'" size="small">
                {{ item.ok ? '正常' : '异常' }}
              </el-tag>
            </div>
            <div class="dep-key">{{ item.key }}</div>
            <div class="dep-desc">{{ item.desc }}</div>
            <div v-if="item.model" class="dep-model">
              <span class="dep-model-label">模型</span>
              <code>{{ item.model }}</code>
            </div>
            <div v-if="item.provider" class="dep-provider">提供方：{{ item.provider }}</div>
          </div>
        </el-col>
      </el-row>
      <div v-if="providerLine" class="checked">当前模型：{{ providerLine }}</div>
      <div v-if="checkedAt" class="checked">最近检测：{{ checkedAt }}</div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { getHealth } from '../api/health'

const META = {
  mysql: { label: 'MySQL', desc: '文档/会话等业务元数据' },
  minio: { label: 'MinIO', desc: 'Markdown 原文件对象存储' },
  milvus: { label: 'Milvus', desc: '向量检索与分块入库' },
  embedding: { label: 'Embedding', desc: '向量化（Ollama / 百炼 DashScope）' },
  llm: { label: 'LLM', desc: '问答推理（Ollama / 百炼 DashScope）' },
  etcd: { label: 'Etcd', desc: '动态配置（topK / chunkSize 等）' },
}

const loading = ref(false)
const health = ref(null)
const checkedAt = ref('')
let timer = null

const overallText = computed(() => {
  if (!health.value) return '检测中'
  return health.value.healthy ? '正常' : '部分异常'
})

const overallTag = computed(() => {
  if (!health.value) return 'info'
  return health.value.healthy ? 'success' : 'warning'
})

const providerLine = computed(() => {
  const meta = health.value?.meta
  if (!meta) return ''
  const embed = meta.embeddingModel
    ? `${meta.embeddingImpl || meta.embeddingProvider}/${meta.embeddingModel}`
    : (meta.embeddingImpl || meta.embeddingProvider)
  const llm = meta.llmModel
    ? `${meta.llmImpl || meta.llmProvider}/${meta.llmModel}`
    : (meta.llmImpl || meta.llmProvider)
  return `Embedding=${embed} · LLM=${llm}`
})

const items = computed(() => {
  const deps = health.value?.dependencies || {}
  const meta = health.value?.meta || {}
  const keys = Object.keys(META)
  const extra = Object.keys(deps).filter((k) => !keys.includes(k))
  return [...keys, ...extra].map((key) => {
    const item = {
      key,
      label: META[key]?.label || key,
      desc: META[key]?.desc || '外部依赖',
      ok: !!deps[key],
      model: '',
      provider: '',
    }
    if (key === 'embedding') {
      item.model = meta.embeddingModel || ''
      item.provider = meta.embeddingImpl || meta.embeddingProvider || ''
    }
    if (key === 'llm') {
      item.model = meta.llmModel || ''
      item.provider = meta.llmImpl || meta.llmProvider || ''
    }
    return item
  })
})

async function load() {
  loading.value = true
  try {
    health.value = await getHealth()
    checkedAt.value = new Date().toLocaleString()
  } catch {
    health.value = { healthy: false, dependencies: {} }
    checkedAt.value = new Date().toLocaleString()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  load()
  timer = setInterval(load, 15000)
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
  flex-wrap: wrap;
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
.actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.dep-card {
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 16px;
  background: #fafafa;
  min-height: 120px;
}
.dep-card.ok {
  border-color: #bbf7d0;
  background: #f0fdf4;
}
.dep-card.bad {
  border-color: #fecaca;
  background: #fef2f2;
}
.dep-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.dep-name {
  font-size: 16px;
  font-weight: 600;
}
.dep-key {
  margin-top: 8px;
  font-size: 12px;
  color: var(--muted);
  font-family: Consolas, Monaco, monospace;
}
.dep-desc {
  margin-top: 10px;
  font-size: 13px;
  color: #4b5563;
  line-height: 1.5;
}
.dep-model {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.dep-model-label {
  font-size: 12px;
  color: #64748b;
}
.dep-model code {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  color: #1e3a8a;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 6px;
  padding: 2px 8px;
  word-break: break-all;
}
.dep-provider {
  margin-top: 6px;
  font-size: 12px;
  color: #64748b;
}
.checked {
  margin-top: 4px;
  color: var(--muted);
  font-size: 12px;
}
</style>
