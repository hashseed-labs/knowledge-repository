<template>
  <div class="page chat-page">
    <div class="panel chat-panel">
      <div class="chat-header">
        <div>
          <div class="title">知识库问答</div>
          <div class="desc">SSE 流式输出 · Markdown 渲染 · 引用来源</div>
        </div>
        <el-button @click="onReset" :disabled="store.streaming">新会话</el-button>
      </div>

      <div class="messages" ref="listRef">
        <div v-if="!store.messages.length" class="empty">上传文档并等待 READY 后，在此提问</div>
        <div
          v-for="(msg, idx) in store.messages"
          :key="idx"
          class="msg"
          :class="msg.role"
        >
          <div class="role">{{ msg.role === 'user' ? '我' : '助手' }}</div>
          <div class="bubble">
            <div
              v-if="msg.role === 'assistant' && store.streaming && idx === store.messages.length - 1 && !msg.content"
              class="thinking"
            >
              <span class="thinking-dot"></span>
              思考中，请稍等...
            </div>
            <MarkdownView
              v-else-if="msg.role === 'assistant'"
              :content="msg.content"
            />
            <div v-else class="user-text">{{ msg.content }}</div>
            <div
              v-if="msg.role === 'assistant' && store.streaming && idx === store.messages.length - 1 && msg.content"
              class="thinking thinking-inline"
            >
              生成中...
            </div>
            <div
              v-if="msg.role === 'assistant' && (msg.elapsedMs != null || (store.streaming && idx === store.messages.length - 1))"
              class="elapsed"
            >
              耗时 {{ formatElapsed(liveElapsed(msg, idx)) }}
            </div>
            <div v-if="msg.citations?.length" class="citations">
              <button class="cite-toggle" type="button" @click="toggleCitations(idx)">
                <span>引用来源（{{ msg.citations.length }}）</span>
                <span class="cite-arrow">{{ isCitationsOpen(idx) ? '收起' : '展开' }}</span>
              </button>
              <div v-show="isCitationsOpen(idx)" class="cite-list">
                <el-card
                  v-for="(c, cidx) in msg.citations"
                  :key="cidx"
                  shadow="never"
                  class="cite-card"
                >
                  <div class="cite-head">
                    <span>{{ c.docTitle }} #{{ c.chunkIndex }}</span>
                    <el-tag size="small" type="info">score {{ formatScore(c.score) }}</el-tag>
                  </div>
                  <div class="cite-body">{{ c.content }}</div>
                </el-card>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="composer">
        <el-input
          v-model="question"
          type="textarea"
          :rows="3"
          resize="none"
          placeholder="输入问题，Enter 发送，Shift+Enter 换行"
          @keydown="onKeydown"
        />
        <el-button type="primary" :loading="store.streaming" @click="onSend">发送</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import MarkdownView from '../components/MarkdownView.vue'
import { streamChat } from '../api/chat'
import { useChatStore } from '../stores/chat'

const store = useChatStore()
const question = ref('')
const listRef = ref(null)
const nowTick = ref(Date.now())
const openCitations = ref({})
let abortController = null
let tickTimer = null

function isCitationsOpen(idx) {
  return !!openCitations.value[idx]
}

function toggleCitations(idx) {
  openCitations.value = {
    ...openCitations.value,
    [idx]: !openCitations.value[idx],
  }
}

function formatScore(score) {
  if (score == null) return '-'
  return Number(score).toFixed(3)
}

function formatElapsed(ms) {
  if (ms == null || Number.isNaN(ms) || ms < 0) return '-'
  if (ms < 1000) return `${ms} ms`
  return `${(ms / 1000).toFixed(1)} s`
}

function liveElapsed(msg, idx) {
  if (msg.elapsedMs != null) return msg.elapsedMs
  if (store.streaming && idx === store.messages.length - 1 && msg.startedAt) {
    return Math.max(0, nowTick.value - msg.startedAt)
  }
  return null
}

function startTick() {
  stopTick()
  tickTimer = setInterval(() => {
    nowTick.value = Date.now()
  }, 200)
}

function stopTick() {
  if (tickTimer) {
    clearInterval(tickTimer)
    tickTimer = null
  }
}

onUnmounted(() => {
  stopTick()
  if (abortController) abortController.abort()
})

async function scrollBottom() {
  await nextTick()
  if (listRef.value) {
    listRef.value.scrollTop = listRef.value.scrollHeight
  }
}

function onReset() {
  if (abortController) abortController.abort()
  openCitations.value = {}
  store.reset()
}

function onKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    onSend()
  }
}

async function onSend() {
  const q = question.value.trim()
  if (!q || store.streaming) return

  const startedAt = Date.now()
  store.pushMessage({ role: 'user', content: q, citations: [] })
  store.pushMessage({ role: 'assistant', content: '', citations: [], startedAt, elapsedMs: null })
  question.value = ''
  store.streaming = true
  startTick()
  await scrollBottom()

  abortController = new AbortController()
  try {
    await streamChat({
      question: q,
      sessionId: store.sessionId,
      signal: abortController.signal,
      onEvent: async (event, data) => {
        if (event === 'session' && data?.sessionId) {
          store.setSessionId(data.sessionId)
        }
        if (event === 'meta') {
          if (data?.sessionId) store.setSessionId(data.sessionId)
          store.updateLastAssistant((msg) => {
            msg.citations = data?.citations || []
          })
        }
        if (event === 'token') {
          store.updateLastAssistant((msg) => {
            msg.content += data?.content || ''
          })
          await scrollBottom()
        }
        if (event === 'done') {
          if (data?.sessionId) store.setSessionId(data.sessionId)
          store.updateLastAssistant((msg) => {
            if (data?.content) msg.content = data.content
            if (data?.citations) msg.citations = data.citations
            msg.elapsedMs = Date.now() - startedAt
          })
        }
        if (event === 'error') {
          ElMessage.error(data?.message || '问答失败')
          store.updateLastAssistant((msg) => {
            if (!msg.content) msg.content = `错误: ${data?.message || '问答失败'}`
            msg.elapsedMs = Date.now() - startedAt
          })
        }
      },
    })
  } catch (e) {
    if (e.name !== 'AbortError') {
      ElMessage.error(e.message || '流式问答失败')
      store.updateLastAssistant((msg) => {
        if (!msg.content) msg.content = `错误: ${e.message || '流式问答失败'}`
        msg.elapsedMs = Date.now() - startedAt
      })
    }
  } finally {
    store.updateLastAssistant((msg) => {
      if (msg.elapsedMs == null) {
        msg.elapsedMs = Date.now() - startedAt
      }
    })
    stopTick()
    store.streaming = false
    await scrollBottom()
  }
}
</script>

<style scoped>
.chat-page {
  height: calc(100vh - 100px);
}
.chat-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.title {
  font-size: 16px;
  font-weight: 600;
}
.desc {
  color: var(--muted);
  font-size: 13px;
  margin-top: 4px;
}
.messages {
  flex: 1;
  overflow: auto;
  padding: 8px 4px;
  background: #f8fafc;
  border: 1px solid var(--line);
  border-radius: 10px;
}
.empty {
  color: var(--muted);
  text-align: center;
  margin-top: 80px;
}
.msg {
  display: flex;
  gap: 10px;
  margin: 12px;
}
.msg.user {
  flex-direction: row-reverse;
}
.role {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: #e5e7eb;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  flex-shrink: 0;
}
.msg.assistant .role {
  background: #dbeafe;
  color: #1d4ed8;
}
.bubble {
  max-width: min(760px, 78%);
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 12px 14px;
}
.msg.user .bubble {
  background: #2563eb;
  color: #fff;
  border-color: #2563eb;
}
.user-text {
  white-space: pre-wrap;
}
.thinking {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #64748b;
  font-size: 14px;
}
.thinking-inline {
  margin-top: 8px;
  font-size: 12px;
}
.thinking-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #2563eb;
  animation: thinking-pulse 1s ease-in-out infinite;
}
@keyframes thinking-pulse {
  0%,
  100% {
    opacity: 0.35;
    transform: scale(0.85);
  }
  50% {
    opacity: 1;
    transform: scale(1.1);
  }
}
.elapsed {
  margin-top: 8px;
  font-size: 12px;
  color: var(--muted);
}
.msg.user .elapsed {
  color: rgba(255, 255, 255, 0.85);
}
.citations {
  margin-top: 12px;
  border-top: 1px dashed var(--line);
  padding-top: 10px;
}
.cite-toggle {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  border: none;
  background: transparent;
  padding: 0;
  cursor: pointer;
  font-size: 13px;
  color: var(--muted);
}
.cite-toggle:hover {
  color: #2563eb;
}
.cite-arrow {
  flex-shrink: 0;
}
.cite-list {
  margin-top: 8px;
}
.cite-card {
  margin-bottom: 8px;
}
.cite-head {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
  font-weight: 600;
  font-size: 13px;
}
.cite-body {
  font-size: 12px;
  color: #4b5563;
  white-space: pre-wrap;
}
.composer {
  margin-top: 12px;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 12px;
  align-items: end;
}
</style>
