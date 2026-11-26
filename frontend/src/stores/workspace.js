import { defineStore } from 'pinia'
import { ref } from 'vue'

const TENANT_KEY = 'kb_tenant_id'
const KB_KEY = 'kb_space_id'

function toId(value) {
  if (value == null || value === '') return null
  const n = Number(value)
  return Number.isFinite(n) && n > 0 ? n : null
}

export const useWorkspaceStore = defineStore('workspace', () => {
  const tenantId = ref(toId(localStorage.getItem(TENANT_KEY)))
  const kbId = ref(toId(localStorage.getItem(KB_KEY)))
  const spaces = ref([])

  function setTenantId(id) {
    tenantId.value = toId(id)
    if (tenantId.value != null) localStorage.setItem(TENANT_KEY, String(tenantId.value))
    else localStorage.removeItem(TENANT_KEY)
  }

  function setKbId(id) {
    kbId.value = toId(id)
    if (kbId.value != null) localStorage.setItem(KB_KEY, String(kbId.value))
    else localStorage.removeItem(KB_KEY)
  }

  function setSpaces(list) {
    spaces.value = (list || []).map((s) => ({
      ...s,
      id: toId(s.id),
    }))
    if (!kbId.value && spaces.value.length) {
      setKbId(spaces.value[0].id)
      return
    }
    if (kbId.value && !spaces.value.some((s) => toId(s.id) === kbId.value)) {
      setKbId(spaces.value[0]?.id || null)
    }
  }

  function clear() {
    tenantId.value = null
    kbId.value = null
    spaces.value = []
    localStorage.removeItem(TENANT_KEY)
    localStorage.removeItem(KB_KEY)
  }

  return { tenantId, kbId, spaces, setTenantId, setKbId, setSpaces, clear }
})
