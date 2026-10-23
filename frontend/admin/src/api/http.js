/**
 * 极简 HTTP 封装（用原生 fetch，不引 axios —— 少一个依赖，行为也更可控）。
 *
 * 后端约定（本项目已统一）：
 *   业务成功/失败 → HTTP 200 + { code, message, data }，code=200 才是成功
 *   未登录        → HTTP 401
 *   无权限        → HTTP 403
 * 所以这里必须【同时】处理 HTTP 状态码和业务 code，只判其中一个都会出错。
 */
const TOKEN_KEY = 'shopping_admin_token'
const USER_KEY = 'shopping_admin_user'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function getUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch {
    return null
  }
}

export function saveSession(loginVO, userInfo) {
  localStorage.setItem(TOKEN_KEY, loginVO.accessToken)
  localStorage.setItem(USER_KEY, JSON.stringify(userInfo || {}))
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export async function request(path, { method = 'GET', body, auth = true } = {}) {
  const headers = { 'Content-Type': 'application/json; charset=utf-8' }
  if (auth) {
    const token = getToken()
    if (token) headers.Authorization = `Bearer ${token}`
  }

  let resp
  try {
    resp = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body)
    })
  } catch (e) {
    throw new Error('网络异常，请检查后端是否已启动（localhost:8080）')
  }

  if (resp.status === 401) {
    clearSession()
    if (!location.hash.startsWith('#/login')) location.hash = '#/login'
    throw new Error('登录已过期，请重新登录')
  }
  if (resp.status === 403) {
    throw new Error('没有权限执行该操作（当前账号缺少对应权限点）')
  }

  const text = await resp.text()
  let json
  try {
    json = text ? JSON.parse(text) : {}
  } catch {
    throw new Error(`响应不是合法 JSON（HTTP ${resp.status}）`)
  }
  if (json.code !== 200) {
    const err = new Error(json.message || `请求失败（code=${json.code}）`)
    err.code = json.code
    throw err
  }
  return json.data
}
