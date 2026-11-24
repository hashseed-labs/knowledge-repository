import { createRouter, createWebHistory } from 'vue-router'
import DocsView from '../views/DocsView.vue'
import ChatView from '../views/ChatView.vue'
import HealthView from '../views/HealthView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/docs' },
    { path: '/docs', name: 'docs', component: DocsView },
    { path: '/chat', name: 'chat', component: ChatView },
    { path: '/health', name: 'health', component: HealthView },
  ],
})

export default router

