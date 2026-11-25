import { defineStore } from 'pinia'
import { ref } from 'vue'

const TENANT_KEY = 'kb_tenant_id'
const KB_KEY = 'kb_space_id'

export const useWorkspaceStore = defineStore('workspace', () => {
  const tenantId = ref(Number(localStorage.getItem(TENANT_KEY) || 0) || null)
  const kbId = ref(Number(localStorage.getItem(KB_KEY) || 0) || null)
  const spaces = ref([])

  function setTenantId(id) {
    tenantId.value = id
    if (id != null) localStorage.setItem(TENANT_KEY, String(id))
    else localStorage.removeItem(TENANT_KEY)
  }

  function setKbId(id) {
    kbId.value = id
    if (id != null) localStorage.setItem(KB_KEY, String(id))
    else localStorage.removeItem(KB_KEY)
  }

  function setSpaces(list) {
    spaces.value = list || []
    if (!kbId.value && spaces.value.length) {
      setKbId(spaces.value[0].id)
    }
    if (kbId.value && !spaces.value.some((s) => s.id === kbId.value)) {
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
