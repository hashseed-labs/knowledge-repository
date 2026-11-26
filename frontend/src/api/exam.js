import http from './http'

export function generatePaper(payload) {
  return http.post('/exam/papers/generate', payload, { timeout: 300000 })
}

export function listPapers(page = 1, size = 10) {
  return http.get('/exam/papers', { params: { page, size } })
}

export function getPaper(id) {
  return http.get(`/exam/papers/${id}`)
}

export function updatePaper(id, payload) {
  return http.put(`/exam/papers/${id}`, payload)
}

export function publishPaper(id) {
  return http.post(`/exam/papers/${id}/publish`)
}

export function deletePaper(id) {
  return http.delete(`/exam/papers/${id}`)
}

export function startAttempt(paperId) {
  return http.post(`/exam/papers/${paperId}/attempts`)
}

export function getAttempt(id) {
  return http.get(`/exam/attempts/${id}`)
}

export function saveAnswers(id, answers) {
  return http.put(`/exam/attempts/${id}/answers`, { answers })
}

export function submitAttempt(id, answers) {
  return http.post(`/exam/attempts/${id}/submit`, answers ? { answers } : {})
}

export function listMyAttempts(page = 1, size = 10) {
  return http.get('/exam/attempts/mine', { params: { page, size } })
}
