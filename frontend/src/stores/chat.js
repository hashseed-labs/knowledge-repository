import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useChatStore = defineStore('chat', () => {
  const sessionId = ref(null)
  const messages = ref([])
  const streaming = ref(false)

  function reset() {
    sessionId.value = null
    messages.value = []
    streaming.value = false
  }

  function setSessionId(id) {
    sessionId.value = id
  }

  function pushMessage(msg) {
    messages.value.push(msg)
  }

  function updateLastAssistant(updater) {
    for (let i = messages.value.length - 1; i >= 0; i -= 1) {
      if (messages.value[i].role === 'assistant') {
        updater(messages.value[i])
        break
      }
    }
  }

  return {
    sessionId,
    messages,
    streaming,
    reset,
    setSessionId,
    pushMessage,
    updateLastAssistant,
  }
})
