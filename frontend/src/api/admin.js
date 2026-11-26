import http from './http'

export function listTenants(page = 1, size = 10, keyword = '') {
  return http.get('/admin/tenants', { params: { page, size, keyword } })
}

export function createTenant(data) {
  return http.post('/admin/tenants', data)
}

export function updateTenant(id, data) {
  return http.put(`/admin/tenants/${id}`, data)
}

export function deleteTenant(id) {
  return http.delete(`/admin/tenants/${id}`)
}

export function listTenantMembers(tenantId) {
  return http.get(`/admin/tenants/${tenantId}/members`)
}

export function listTenantMemberCandidates(tenantId, keyword = '') {
  return http.get(`/admin/tenants/${tenantId}/member-candidates`, { params: { keyword } })
}

export function addTenantMember(tenantId, data) {
  return http.post(`/admin/tenants/${tenantId}/members`, data)
}

export function updateTenantMember(tenantId, userId, data) {
  return http.put(`/admin/tenants/${tenantId}/members/${userId}`, data)
}

export function removeTenantMember(tenantId, userId) {
  return http.delete(`/admin/tenants/${tenantId}/members/${userId}`)
}

export function listUsers(page = 1, size = 10, keyword = '') {
  return http.get('/admin/users', { params: { page, size, keyword } })
}

export function createUser(data) {
  return http.post('/admin/users', data)
}

export function updateUser(id, data) {
  return http.put(`/admin/users/${id}`, data)
}

export function listRoles() {
  return http.get('/admin/roles')
}

export function listPermissions() {
  return http.get('/admin/permissions')
}

export function createRole(data) {
  return http.post('/admin/roles', data)
}

export function updateRole(id, data) {
  return http.put(`/admin/roles/${id}`, data)
}

export function listAudits(page = 1, size = 20, action = '') {
  return http.get('/admin/audits', { params: { page, size, action } })
}

export function listSpaces() {
  return http.get('/kb/spaces')
}

export function createSpace(data) {
  return http.post('/kb/spaces', data)
}

export function updateSpace(id, data) {
  return http.put(`/kb/spaces/${id}`, data)
}

export function listMembers(kbId) {
  return http.get(`/kb/spaces/${kbId}/members`)
}

export function addMember(kbId, data) {
  return http.post(`/kb/spaces/${kbId}/members`, data)
}

export function removeMember(kbId, userId) {
  return http.delete(`/kb/spaces/${kbId}/members/${userId}`)
}
