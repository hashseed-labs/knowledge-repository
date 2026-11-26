<template>
  <div class="page" v-loading="loading">
    <div class="panel toolbar" v-if="attempt">
      <div>
        <div class="title">{{ attempt.paperTitle }}</div>
        <div class="desc">
          状态 {{ attempt.status }}
          <span v-if="attempt.status === 'IN_PROGRESS'"> · 剩余 {{ formatRemain(remainSeconds) }}</span>
          <span v-if="attempt.status !== 'IN_PROGRESS'">
            · 得分 {{ attempt.score ?? 0 }} / {{ attempt.maxScore ?? 0 }}
            <span v-if="attempt.pendingShortCount">（简答待评 {{ attempt.pendingShortCount }}）</span>
          </span>
        </div>
      </div>
      <div class="actions">
        <el-button @click="router.push('/exam')">返回试卷列表</el-button>
        <el-button
          v-if="attempt.status === 'IN_PROGRESS'"
          type="primary"
          :loading="submitting"
          @click="onSubmit"
        >
          交卷
        </el-button>
      </div>
    </div>

    <div class="panel" style="margin-top: 16px" v-if="attempt">
      <div v-for="(q, idx) in attempt.questions || []" :key="q.questionId" class="q-card">
        <div class="q-head">
          <strong>{{ idx + 1 }}. [{{ typeLabel(q.type) }}] （{{ q.score }} 分）</strong>
          <el-tag
            v-if="attempt.status !== 'IN_PROGRESS' && q.pendingReview"
            type="warning"
            size="small"
          >待人工评阅</el-tag>
          <el-tag
            v-else-if="attempt.status !== 'IN_PROGRESS' && q.correct === true"
            type="success"
            size="small"
          >正确 +{{ q.earnedScore }}</el-tag>
          <el-tag
            v-else-if="attempt.status !== 'IN_PROGRESS' && q.correct === false"
            type="danger"
            size="small"
          >错误</el-tag>
        </div>
        <div class="stem">{{ q.stem }}</div>

        <div v-if="q.type === 'SINGLE'" class="mt8">
          <el-radio-group v-model="answers[q.questionId]" :disabled="!editable" @change="scheduleSave">
            <el-radio v-for="op in q.options || []" :key="op.key" :value="op.key" class="opt">
              {{ op.key }}. {{ op.text }}
            </el-radio>
          </el-radio-group>
        </div>

        <div v-else-if="q.type === 'MULTI'" class="mt8">
          <el-checkbox-group v-model="answers[q.questionId]" :disabled="!editable" @change="scheduleSave">
            <el-checkbox v-for="op in q.options || []" :key="op.key" :value="op.key" class="opt">
              {{ op.key }}. {{ op.text }}
            </el-checkbox>
          </el-checkbox-group>
        </div>

        <div v-else-if="q.type === 'JUDGE'" class="mt8">
          <el-radio-group v-model="answers[q.questionId]" :disabled="!editable" @change="scheduleSave">
            <el-radio :value="true">正确</el-radio>
            <el-radio :value="false">错误</el-radio>
          </el-radio-group>
        </div>

        <div v-else class="mt8">
          <el-input
            v-model="answers[q.questionId]"
            type="textarea"
            :rows="3"
            :disabled="!editable"
            placeholder="请输入简答"
            @change="scheduleSave"
          />
        </div>

        <div v-if="attempt.status !== 'IN_PROGRESS'" class="review">
          <div>正确答案：{{ formatAnswer(q.correctAnswer) }}</div>
          <div v-if="q.analysis">解析：{{ q.analysis }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAttempt, saveAnswers, submitAttempt } from '../api/exam'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const submitting = ref(false)
const attempt = ref(null)
const answers = reactive({})
const remainSeconds = ref(0)
let tickTimer = null
let saveTimer = null

const editable = computed(() => attempt.value?.status === 'IN_PROGRESS')

function typeLabel(type) {
  return { SINGLE: '单选', MULTI: '多选', JUDGE: '判断', SHORT: '简答' }[type] || type
}

function formatAnswer(answer) {
  if (Array.isArray(answer)) return answer.join(', ')
  if (typeof answer === 'boolean') return answer ? '正确' : '错误'
  if (answer == null) return '-'
  return String(answer)
}

function formatRemain(sec) {
  const s = Math.max(0, Number(sec) || 0)
  const m = Math.floor(s / 60)
  const r = s % 60
  return `${m}:${String(r).padStart(2, '0')}`
}

function hydrateAnswers(data) {
  Object.keys(answers).forEach((k) => delete answers[k])
  for (const q of data.questions || []) {
    if (q.type === 'MULTI') {
      answers[q.questionId] = Array.isArray(q.userAnswer) ? [...q.userAnswer] : []
    } else if (q.type === 'JUDGE') {
      answers[q.questionId] = typeof q.userAnswer === 'boolean' ? q.userAnswer : null
    } else {
      answers[q.questionId] = q.userAnswer ?? (q.type === 'SHORT' ? '' : null)
    }
  }
}

function buildAnswerPayload() {
  return (attempt.value?.questions || []).map((q) => ({
    questionId: q.questionId,
    answer: answers[q.questionId],
  }))
}

function scheduleSave() {
  if (!editable.value) return
  clearTimeout(saveTimer)
  saveTimer = setTimeout(async () => {
    try {
      await saveAnswers(attempt.value.id, buildAnswerPayload())
    } catch {
      // http interceptor already tips
    }
  }, 800)
}

function startTick() {
  stopTick()
  tickTimer = setInterval(async () => {
    if (!attempt.value || attempt.value.status !== 'IN_PROGRESS') return
    remainSeconds.value = Math.max(0, remainSeconds.value - 1)
    if (remainSeconds.value <= 0) {
      stopTick()
      ElMessage.warning('考试时间到，正在自动交卷')
      await doSubmit(true)
    }
  }, 1000)
}

function stopTick() {
  if (tickTimer) {
    clearInterval(tickTimer)
    tickTimer = null
  }
}

async function load() {
  loading.value = true
  try {
    const data = await getAttempt(route.params.id)
    attempt.value = data
    remainSeconds.value = data.remainSeconds || 0
    hydrateAnswers(data)
    if (data.status === 'IN_PROGRESS') startTick()
  } catch (e) {
    ElMessage.error(e.message || '加载考试失败')
    router.push('/exam')
  } finally {
    loading.value = false
  }
}

async function onSubmit() {
  await ElMessageBox.confirm('确认交卷？客观题将自动批改。', '交卷', { type: 'warning' })
  await doSubmit(false)
}

async function doSubmit(auto) {
  if (!attempt.value || submitting.value) return
  submitting.value = true
  try {
    const data = await submitAttempt(attempt.value.id, buildAnswerPayload())
    attempt.value = data
    remainSeconds.value = 0
    hydrateAnswers(data)
    stopTick()
    ElMessage.success(auto ? '已自动交卷' : '交卷成功')
  } catch (e) {
    if (!auto) ElMessage.error(e.message || '交卷失败')
  } finally {
    submitting.value = false
  }
}

watch(
  () => route.params.id,
  () => load(),
)

onMounted(load)
onUnmounted(() => {
  stopTick()
  clearTimeout(saveTimer)
})
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
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
.actions {
  display: flex;
  gap: 8px;
}
.q-card {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 14px;
  margin-bottom: 12px;
}
.q-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.stem {
  margin-top: 8px;
  white-space: pre-wrap;
  line-height: 1.6;
}
.mt8 {
  margin-top: 10px;
}
.opt {
  display: block;
  margin: 6px 0;
}
.review {
  margin-top: 10px;
  padding: 8px 10px;
  background: #f9fafb;
  border-radius: 6px;
  color: #374151;
  font-size: 13px;
  line-height: 1.6;
}
</style>
