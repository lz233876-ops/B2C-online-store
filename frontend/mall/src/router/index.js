import { createRouter, createWebHashHistory } from 'vue-router'
import { getToken } from '../api/http'

const routes = [
  { path: '/login', component: () => import('../views/Login.vue'), meta: { public: true } },
  { path: '/', redirect: '/home' },
  { path: '/home', component: () => import('../views/Home.vue'), meta: { public: true, tab: 'home' } },
  { path: '/detail/:spuId', component: () => import('../views/Detail.vue'), meta: { public: true } },
  { path: '/seckill', component: () => import('../views/Seckill.vue'), meta: { public: true, tab: 'seckill' } },
  { path: '/cart', component: () => import('../views/Cart.vue'), meta: { tab: 'cart' } },
  { path: '/orders', component: () => import('../views/Orders.vue'), meta: { tab: 'orders' } }
]

const router = createRouter({ history: createWebHashHistory(), routes })

router.beforeEach((to) => {
  if (!to.meta.public && !getToken()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
