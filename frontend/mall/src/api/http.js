const TOKEN_KEY = 'shopping_mall_token'
const USER_KEY = 'shopping_mall_user'

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

export function saveSession(loginVO) {
  localStorage.setItem(TOKEN_KEY, loginVO.accessToken)
}

export function saveUser(user) {
  localStorage.setItem(USER_KEY, JSON.stringify(user || {}))
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
  } catch {
    throw new Error('网络异常，请确认后端已启动（localhost:8080）')
  }

  if (resp.status === 401) {
    clearSession()
    if (!location.hash.startsWith('#/login')) {
      location.hash = `#/login?redirect=${encodeURIComponent(location.hash.replace('#', ''))}`
    }
    throw new Error('请先登录')
  }
  if (resp.status === 403) throw new Error('没有权限')

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
