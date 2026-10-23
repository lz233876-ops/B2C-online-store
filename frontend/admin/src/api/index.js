import { request } from './http'

/* ---------------- 认证 ---------------- */
export const authApi = {
  login: (body) => request('/api/admin/auth/login', { method: 'POST', body, auth: false }),
  me: () => request('/api/admin/auth/me')
}

/* ---------------- 商品 ---------------- */
export const productApi = {
  spuPage: (params) => request(`/api/admin/product/spu/page?${new URLSearchParams(params)}`),
  updateStatus: (id, status) =>
    request(`/api/admin/product/spu/${id}/status?status=${status}`, { method: 'PUT' }),
  categoryTree: () => request('/api/admin/product/category/tree'),
  brandPage: (params) => request(`/api/admin/product/brand/page?${new URLSearchParams(params)}`)
}

/* ---------------- 订单 ---------------- */
export const orderApi = {
  page: (params) => request(`/api/admin/order/page?${new URLSearchParams(params)}`),
  detail: (id) => request(`/api/admin/order/${id}`),
  deliver: (id, body) => request(`/api/admin/order/${id}/deliver`, { method: 'PUT', body }),
  closeTimeout: () => request('/api/admin/order/close-timeout', { method: 'POST' })
}

/* ---------------- 售后 ---------------- */
export const aftersaleApi = {
  page: (params) => request(`/api/admin/aftersale/page?${new URLSearchParams(params)}`),
  detail: (id) => request(`/api/admin/aftersale/${id}`),
  audit: (id, body) => request(`/api/admin/aftersale/${id}/audit`, { method: 'PUT', body }),
  confirmReceive: (id) => request(`/api/admin/aftersale/${id}/receive`, { method: 'PUT' })
}

/* ---------------- 秒杀运维 ---------------- */
export const seckillApi = {
  warmUp: () => request('/api/admin/seckill/warm-up', { method: 'POST' }),
  cachedCount: () => request('/api/admin/seckill/cached-count')
}
