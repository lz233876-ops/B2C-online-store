import { createRouter, createWebHashHistory } from 'vue-router'
import { getToken } from '../api/http'

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/Login.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('../layout/Layout.vue'),
    redirect: '/product',
    children: [
      { path: 'product', name: 'product', component: () => import('../views/ProductList.vue'), meta: { title: '商品管理' } },
      { path: 'order', name: 'order', component: () => import('../views/OrderList.vue'), meta: { title: '订单管理' } },
      { path: 'aftersale', name: 'aftersale', component: () => import('../views/AftersaleList.vue'), meta: { title: '售后管理' } }
    ]
  }
]

const router = createRouter({
  // 用 hash 模式：部署到任何静态服务器都不用配 history fallback
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to) => {
  if (!to.meta.public && !getToken()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path === '/login' && getToken()) {
    return { path: '/' }
  }
  return true
})

export default router
