<template>
  <div class="page">
    <div class="panel toolbar">
      <div>
        <div class="title">在线考试</div>
        <div class="desc">生成试卷后发布，即可开考；客观题交卷后自动批改</div>
      </div>
      <div class="toolbar-actions">
        <el-button @click="loadAttempts" v-if="auth.hasPermission('exam:attempt:read')">我的成绩</el-button>
        <el-button type="primary" @click="openGenerate" :disabled="!auth.hasPermission('exam:paper:generate')">
          生成试卷
        </el-button>
      </div>
    </div>

    <div class="panel" style="margin-top: 16px">
      <el-table :data="records" v-loading="loading" empty-text="暂无试卷">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="标题" min-width="200" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="questionCount" label="题量" width="80" />
        <el-table-column prop="totalScore" label="总分" width="90" />
        <el-table-column prop="updatedAt" label="更新时间" width="180" />
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row.id)">查看</el-button>
            <el-button
              v-if="auth.hasPermission('exam:attempt:take') && row.status === 'READY'"
              link
              type="warning"
              @click="onStart(row)"
            >
              开始考试
            </el-button>
            <el-button
              v-if="auth.hasPermission('exam:paper:manage') && row.status !== 'READY'"
              link
              type="success"
              @click="onPublish(row)"
            >
              发布
            </el-button>
            <el-button
              v-if="auth.hasPermission('exam:paper:manage')"
              link
              type="danger"
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

    <el-dialog v-model="genVisible" title="根据文档生成试卷" width="640px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="试卷标题">
          <el-input v-model="genForm.title" placeholder="例如：星舟客服系统运维测验" />
        </el-form-item>
        <el-form-item label="选择文档">
          <el-select
            v-model="genForm.docIds"
            multiple
            filterable
            placeholder="仅显示 READY 文档"
            style="width: 100%"
          >
            <el-option
              v-for="d in readyDocs"
              :key="d.id"
              :label="`${d.title} (#${d.id})`"
              :value="d.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-model="genForm.difficulty" style="width: 160px">
            <el-option label="简单" value="easy" />
            <el-option label="中等" value="medium" />
            <el-option label="困难" value="hard" />
          </el-select>
        </el-form-item>
        <el-form-item label="单选">
          <el-input-number v-model="genForm.singleCount" :min="0" :max="30" />
        </el-form-item>
        <el-form-item label="多选">
          <el-input-number v-model="genForm.multiCount" :min="0" :max="20" />
        </el-form-item>
        <el-form-item label="判断">
          <el-input-number v-model="genForm.judgeCount" :min="0" :max="30" />
        </el-form-item>
        <el-form-item label="简答">
          <el-input-number v-model="genForm.shortCount" :min="0" :max="10" />
        </el-form-item>
        <el-form-item label="考试时长">
          <el-input-number v-model="genForm.durationMinutes" :min="1" :max="600" />
          <span class="unit">分钟</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="genVisible = false">取消</el-button>
        <el-button type="primary" :loading="generating" @click="onGenerate">开始生成</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" size="60%" :title="detail?.title || '试卷详情'" destroy-on-close>
      <div v-loading="detailLoading">
        <div v-if="detail" class="detail-meta">
          <el-tag :type="statusType(detail.status)" size="small">{{ detail.status }}</el-tag>
          <span>题量 {{ detail.questionCount }} · 总分 {{ detail.totalScore }}</span>
          <el-button
            v-if="auth.hasPermission('exam:paper:manage') && detail.status !== 'ARCHIVED'"
            type="primary"
            size="small"
            :loading="saving"
            @click="onSave"
          >
            保存修改
          </el-button>
        </div>
        <div v-for="(q, idx) in detail?.questions || []" :key="q.id || idx" class="q-card">
          <div class="q-head">
            <strong>{{ idx + 1 }}. [{{ typeLabel(q.type) }}]</strong>
            <el-input-number v-model="q.score" :min="0" :step="0.5" size="small" />
          </div>
          <el-input v-model="q.stem" type="textarea" :rows="2" class="mt8" />
          <div v-if="q.options?.length" class="opts">
            <div v-for="(op, oi) in q.options" :key="oi" class="opt-row">
              <el-input v-model="op.key" style="width: 64px" />
              <el-input v-model="op.text" />
            </div>
          </div>
          <div class="mt8">
            <div class="label">答案</div>
            <el-input :model-value="formatAnswer(q.answer)" @update:model-value="(v) => (q.answer = parseAnswer(q.type, v))" />
          </div>
          <div class="mt8">
            <div class="label">解析</div>
            <el-input v-model="q.analysis" type="textarea" :rows="2" />
          </div>
          <div class="cite">来源 chunk: {{ (q.sourceChunkIds || []).join(', ') || '-' }}</div>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="attemptVisible" title="我的考试记录" width="720px">
      <el-table :data="attempts" v-loading="attemptLoading" empty-text="暂无记录">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="paperTitle" label="试卷" min-width="180" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column label="得分" width="120">
          <template #default="{ row }">{{ row.score ?? '-' }} / {{ row.maxScore ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" @click="goAttempt(row.id)">
              {{ row.status === 'IN_PROGRESS' ? '继续' : '查看' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listDocuments } from '../api/document'
import {
  deletePaper,
  generatePaper,
  getPaper,
  listMyAttempts,
  listPapers,
  publishPaper,
  startAttempt,
  updatePaper,
} from '../api/exam'
import { useAuthStore } from '../stores/auth'

const router = useRouter()

const auth = useAuthStore()
const records = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)

const genVisible = ref(false)
const generating = ref(false)
const readyDocs = ref([])
const genForm = reactive({
  title: '',
  docIds: [],
  difficulty: 'medium',
  singleCount: 3,
  multiCount: 1,
  judgeCount: 2,
  shortCount: 1,
  durationMinutes: 60,
})

const attemptVisible = ref(false)
const attemptLoading = ref(false)
const attempts = ref([])

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const saving = ref(false)

function statusType(status) {
  if (status === 'READY') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'DRAFT') return 'warning'
  return 'info'
}

function typeLabel(type) {
  return { SINGLE: '单选', MULTI: '多选', JUDGE: '判断', SHORT: '简答' }[type] || type
}

function formatAnswer(answer) {
  if (Array.isArray(answer)) return answer.join(',')
  if (typeof answer === 'boolean') return String(answer)
  if (answer == null) return ''
  return String(answer)
}

function parseAnswer(type, text) {
  const v = (text || '').trim()
  if (type === 'MULTI') return v ? v.split(/[,，\s]+/).filter(Boolean) : []
  if (type === 'JUDGE') return v.toLowerCase() === 'true' || v === '正确' || v === '是'
  return v
}

async function load() {
  loading.value = true
  try {
    const data = await listPapers(page.value, size.value)
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

async function openGenerate() {
  const data = await listDocuments(1, 100)
  readyDocs.value = (data.records || []).filter((d) => d.status === 'READY')
  if (!readyDocs.value.length) {
    ElMessage.warning('没有 READY 状态的文档，请先在文档管理上传并等待处理完成')
    return
  }
  genForm.title = ''
  genForm.docIds = []
  genVisible.value = true
}

async function onGenerate() {
  if (!genForm.title.trim()) {
    ElMessage.warning('请填写试卷标题')
    return
  }
  if (!genForm.docIds.length) {
    ElMessage.warning('请选择至少一篇文档')
    return
  }
  const totalQ = genForm.singleCount + genForm.multiCount + genForm.judgeCount + genForm.shortCount
  if (totalQ <= 0) {
    ElMessage.warning('题量合计须大于 0')
    return
  }
  generating.value = true
  try {
    const paper = await generatePaper({ ...genForm, title: genForm.title.trim() })
    ElMessage.success(`生成完成，共 ${paper.questionCount} 题`)
    genVisible.value = false
    await load()
    await openDetail(paper.id)
  } catch (e) {
    ElMessage.error(e.message || '生成失败')
  } finally {
    generating.value = false
  }
}

async function openDetail(id) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await getPaper(id)
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

async function onSave() {
  if (!detail.value) return
  saving.value = true
  try {
    detail.value = await updatePaper(detail.value.id, {
      title: detail.value.title,
      questions: (detail.value.questions || []).map((q, idx) => ({
        id: q.id,
        type: q.type,
        stem: q.stem,
        options: q.options || [],
        answer: q.answer,
        analysis: q.analysis,
        score: q.score,
        sourceDocIds: q.sourceDocIds || [],
        sourceChunkIds: q.sourceChunkIds || [],
        sortNo: q.sortNo ?? idx + 1,
      })),
    })
    ElMessage.success('已保存')
    await load()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function onPublish(row) {
  await ElMessageBox.confirm(`确认发布试卷「${row.title}」？发布后可开考。`, '发布', { type: 'warning' })
  await publishPaper(row.id)
  ElMessage.success('已发布')
  await load()
}

async function onStart(row) {
  await ElMessageBox.confirm(`确认开始考试「${row.title}」？开始后将计时。`, '开始考试', { type: 'warning' })
  const attempt = await startAttempt(row.id)
  router.push(`/exam/attempts/${attempt.id}`)
}

async function loadAttempts() {
  attemptVisible.value = true
  attemptLoading.value = true
  try {
    const data = await listMyAttempts(1, 50)
    attempts.value = data.records || []
  } finally {
    attemptLoading.value = false
  }
}

function goAttempt(id) {
  attemptVisible.value = false
  router.push(`/exam/attempts/${id}`)
}

async function onDelete(row) {
  await ElMessageBox.confirm(`确认删除试卷「${row.title}」？`, '删除', { type: 'warning' })
  await deletePaper(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.toolbar-actions {
  display: flex;
  gap: 8px;
}
.unit {
  margin-left: 8px;
  color: #6b7280;
}
.title {
  font-size: 16px;
  font-weight: 600;
}
.desc {
  color: #6b7280;
  font-size: 13px;
  margin-top: 4px;
}
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.detail-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.q-card {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 12px;
}
.q-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.opts {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.opt-row {
  display: flex;
  gap: 8px;
}
.mt8 {
  margin-top: 8px;
}
.label {
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 4px;
}
.cite {
  margin-top: 8px;
  font-size: 12px;
  color: #9ca3af;
}
</style>
