import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

const TOKEN_KEY = 'kb_access_token'
const REFRESH_KEY = 'kb_refresh_token'
const USER_KEY = 'kb_user'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref(localStorage.getItem(TOKEN_KEY) || '')
  const refreshToken = ref(localStorage.getItem(REFRESH_KEY) || '')
  const user = ref(JSON.parse(localStorage.getItem(USER_KEY) || 'null'))

  const isLogin = computed(() => !!accessToken.value)
  const permissions = computed(() => new Set(user.value?.permissions || []))
  const roles = computed(() => new Set(user.value?.roles || []))

  function hasPermission(code) {
    return permissions.value.has(code)
  }

  function setSession(payload) {
    accessToken.value = payload.accessToken || ''
    refreshToken.value = payload.refreshToken || ''
    user.value = payload.user || null
    localStorage.setItem(TOKEN_KEY, accessToken.value)
    localStorage.setItem(REFRESH_KEY, refreshToken.value)
    localStorage.setItem(USER_KEY, JSON.stringify(user.value))
  }

  function updateUser(partial) {
    user.value = { ...(user.value || {}), ...partial }
    localStorage.setItem(USER_KEY, JSON.stringify(user.value))
  }

  function clear() {
    accessToken.value = ''
    refreshToken.value = ''
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(REFRESH_KEY)
    localStorage.removeItem(USER_KEY)
  }

  return {
    accessToken,
    refreshToken,
    user,
    isLogin,
    permissions,
    roles,
    hasPermission,
    setSession,
    updateUser,
    clear,
  }
})
