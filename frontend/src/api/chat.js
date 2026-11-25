import { useAuthStore } from '../stores/auth'
import { useWorkspaceStore } from '../stores/workspace'
import http from './http'

export function listMessages(sessionId) {
  return http.get(`/chat/sessions/${sessionId}/messages`)
}

export async function streamChat({ question, sessionId, onEvent, signal }) {
  const auth = useAuthStore()
  const ws = useWorkspaceStore()
  const headers = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
  }
  if (auth.accessToken) headers.Authorization = `Bearer ${auth.accessToken}`
  if (ws.tenantId) headers['X-Tenant-Id'] = ws.tenantId
  if (ws.kbId) headers['X-Kb-Id'] = ws.kbId

  const resp = await fetch('/api/chat/stream', {
    method: 'POST',
    headers,
    body: JSON.stringify({ question, sessionId }),
    signal,
  })

  if (!resp.ok || !resp.body) {
    const text = await resp.text()
    throw new Error(text || `HTTP ${resp.status}`)
  }

  const reader = resp.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let eventName = 'message'
  let dataLines = []

  const flush = () => {
    if (!dataLines.length) return
    const raw = dataLines.join('\n')
    dataLines = []
    let parsed = raw
    try {
      parsed = JSON.parse(raw)
    } catch {
      // keep raw
    }
    onEvent?.(eventName, parsed)
    eventName = 'message'
  }

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const parts = buffer.split(/\r?\n/)
    buffer = parts.pop() || ''
    for (const line of parts) {
      if (line.startsWith('event:')) eventName = line.slice(6).trim()
      else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
      else if (line === '') flush()
    }
  }
  flush()
}
