import { createRouter, createWebHistory } from 'vue-router'
import DocsView from '../views/DocsView.vue'
import ChatView from '../views/ChatView.vue'
import HealthView from '../views/HealthView.vue'
import LoginView from '../views/LoginView.vue'
import UsersView from '../views/UsersView.vue'
import RolesView from '../views/RolesView.vue'
import SpacesView from '../views/SpacesView.vue'
import AuditsView from '../views/AuditsView.vue'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    { path: '/', redirect: '/health' },
    { path: '/health', name: 'health', component: HealthView },
    { path: '/docs', name: 'docs', component: DocsView, meta: { permission: 'kb:doc:read' } },
    { path: '/chat', name: 'chat', component: ChatView, meta: { permission: 'kb:chat:ask' } },
    { path: '/spaces', name: 'spaces', component: SpacesView },
    { path: '/users', name: 'users', component: UsersView, meta: { permission: 'tenant:user:manage' } },
    { path: '/roles', name: 'roles', component: RolesView, meta: { permission: 'tenant:role:manage' } },
    { path: '/audits', name: 'audits', component: AuditsView, meta: { permission: 'tenant:audit:view' } },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.public) {
    if (auth.isLogin && to.path === '/login') return '/health'
    return true
  }
  if (!auth.isLogin) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.permission && !auth.hasPermission(to.meta.permission)) {
    return '/health'
  }
  return true
})

export default router
