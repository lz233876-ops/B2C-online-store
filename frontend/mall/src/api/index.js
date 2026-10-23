import { request } from './http'

/* ---------------- 认证 ---------------- */
export const authApi = {
  login: (phone, password) =>
    request('/api/app/auth/login', { method: 'POST', body: { phone, password }, auth: false }),
  register: (phone, password, nickname) =>
    request('/api/app/auth/register', { method: 'POST', body: { phone, password, nickname }, auth: false }),
  me: () => request('/api/app/auth/me')
}

/* ---------------- 商品 ---------------- */
export const productApi = {
  list: (params) => request(`/api/app/product/list?${new URLSearchParams(params)}`),
  detail: (spuId) => request(`/api/app/product/${spuId}`)
}

/* ---------------- 地址 ---------------- */
export const addressApi = {
  list: () => request('/api/app/address/list'),
  create: (body) => request('/api/app/address', { method: 'POST', body })
}

/* ---------------- 购物车 ---------------- */
export const cartApi = {
  list: () => request('/api/app/cart/list'),
  add: (skuId, quantity) => request('/api/app/cart/add', { method: 'POST', body: { skuId, quantity } }),
  updateQuantity: (id, quantity) =>
    request('/api/app/cart/quantity', { method: 'PUT', body: { id, quantity } }),
  updateSelected: (ids, selected) =>
    request('/api/app/cart/selected', { method: 'PUT', body: { ids, selected } })
}

/* ---------------- 订单 ---------------- */
export const orderApi = {
  submit: (addressId, note) => request('/api/app/order/submit', { method: 'POST', body: { addressId, note } }),
  page: (params) => request(`/api/app/order/page?${new URLSearchParams(params)}`),
  detail: (id) => request(`/api/app/order/${id}`),
  cancel: (id, reason) =>
    request(`/api/app/order/${id}/cancel?reason=${encodeURIComponent(reason || '')}`, { method: 'PUT' }),
  receive: (id) => request(`/api/app/order/${id}/receive`, { method: 'PUT' })
}

/* ---------------- 支付（模拟渠道） ---------------- */
export const payApi = {
  create: (orderNo, channel = 1) =>
    request('/api/app/pay/create', { method: 'POST', body: { orderNo, channel } }),
  // 模拟收银台是【公开接口】（它扮演渠道侧），所以不带 token
  mockPay: (paymentNo) =>
    request(`/api/pay/mock-gateway/${paymentNo}/pay?success=true`, { method: 'POST', auth: false })
}

/* ---------------- 秒杀 ---------------- */
export const seckillApi = {
  list: () => request('/api/app/seckill/list', { auth: false }),
  detail: (id) => request(`/api/app/seckill/detail/${id}`, { auth: false }),
  grab: (seckillSkuId) => request(`/api/app/seckill/grab/${seckillSkuId}`, { method: 'POST' }),
  result: (activityId) => request(`/api/app/seckill/result?activityId=${activityId}`)
}
