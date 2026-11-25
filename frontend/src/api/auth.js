import http from './http'

export function login(data) {
  return http.post('/auth/login', data)
}

export function logout(refreshToken) {
  return http.post('/auth/logout', { refreshToken })
}

export function me() {
  return http.get('/auth/me')
}

export function refresh(refreshToken) {
  return http.post('/auth/refresh', { refreshToken })
}
