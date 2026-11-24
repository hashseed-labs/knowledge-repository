import http from './http'

export function listDocuments(page = 1, size = 10) {
  return http.get('/documents', { params: { page, size } })
}

export function getDocument(id) {
  return http.get(`/documents/${id}`)
}

export function deleteDocument(id) {
  return http.delete(`/documents/${id}`)
}

export function uploadDocument(file, onProgress) {
  const form = new FormData()
  form.append('file', file)
  return http.post('/documents/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: onProgress,
  })
}
