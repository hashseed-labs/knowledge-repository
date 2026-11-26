import { createRouter, createWebHistory } from 'vue-router'
import DocsView from '../views/DocsView.vue'
import ChatView from '../views/ChatView.vue'
import HealthView from '../views/HealthView.vue'
import LoginView from '../views/LoginView.vue'
import UsersView from '../views/UsersView.vue'
import RolesView from '../views/RolesView.vue'
import SpacesView from '../views/SpacesView.vue'
import AuditsView from '../views/AuditsView.vue'
import TenantsView from '../views/TenantsView.vue'
import ExamView from '../views/ExamView.vue'
import ExamTakeView from '../views/ExamTakeView.vue'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    { path: '/', redirect: () => homePath(useAuthStore()) },
    { path: '/health', name: 'health', component: HealthView, meta: { permission: 'system:health:view' } },
    { path: '/docs', name: 'docs', component: DocsView, meta: { permission: 'kb:doc:read' } },
    { path: '/chat', name: 'chat', component: ChatView, meta: { permission: 'kb:chat:ask' } },
    { path: '/exam', name: 'exam', component: ExamView, meta: { permission: 'exam:paper:read' } },
    {
      path: '/exam/attempts/:id',
      name: 'exam-take',
      component: ExamTakeView,
      meta: { permission: 'exam:attempt:take' },
    },
    { path: '/spaces', name: 'spaces', component: SpacesView },
    { path: '/tenants', name: 'tenants', component: TenantsView, meta: { permission: 'tenant:org:manage' } },
    { path: '/users', name: 'users', component: UsersView, meta: { permission: 'tenant:user:manage' } },
    { path: '/roles', name: 'roles', component: RolesView, meta: { permission: 'tenant:role:manage' } },
    { path: '/audits', name: 'audits', component: AuditsView, meta: { permission: 'tenant:audit:view' } },
  ],
})

function homePath(auth) {
  if (auth.hasPermission('system:health:view')) return '/health'
  return '/spaces'
}

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.public) {
    if (auth.isLogin && to.path === '/login') return homePath(auth)
    return true
  }
  if (!auth.isLogin) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.permission && !auth.hasPermission(to.meta.permission)) {
    return homePath(auth)
  }
  return true
})

export default router
