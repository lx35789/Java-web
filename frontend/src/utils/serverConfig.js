/**
 * 服务器地址配置（运行时可变）
 *
 * 为什么要做成运行时可变：
 *   1. 内网穿透（ngrok / cpolar / frp）的免费域名可能变化，
 *      若把它编译进 APK，域名一变就要重新打包发给所有人。
 *   2. 同一份 APK 要同时支持「局域网 HTTP」与「公网 HTTPS」两种后端地址。
 *   3. 换测试机、换 Wi-Fi、换演示场地时，用户自己就能改，不用等发新包。
 *
 * 存储优先级：localStorage(用户设置) > VITE_API_BASE(构建时默认值) > 相对路径
 *
 * 注意：这里用 localStorage 而不是 Capacitor Preferences，
 * 是因为 PWA/浏览器端也有同一套代码；Capacitor 的 WebView 里 localStorage 同样可用。
 */

const STORAGE_KEY = 'gt_api_base'

/** 构建时注入的默认值（.env.development / .env.android / .env.production） */
export const DEFAULT_API_BASE = import.meta.env.VITE_API_BASE || '/api/v1'

/** 把用户输入规范化为可用的 baseURL */
export function normalizeApiBase(input) {
  let s = String(input || '').trim()
  if (!s) return ''
  // 允许只填 192.168.1.10:8080 这种，自动补协议
  if (!/^https?:\/\//i.test(s)) s = `http://${s}`
  // 去掉末尾斜杠，避免拼成 //api/v1
  s = s.replace(/\/+$/, '')
  // 用户可能只填到主机，自动补 /api/v1
  if (!/\/api\/v1$/i.test(s)) s = `${s}/api/v1`
  return s
}

/** 当前生效的 API 基址 */
export function getApiBase() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved) return saved
  } catch {
    /* 隐私模式下可能不可用，忽略 */
  }
  return DEFAULT_API_BASE
}

/** 设置 API 基址；传空则恢复默认 */
export function setApiBase(value) {
  try {
    const normalized = normalizeApiBase(value)
    if (normalized) localStorage.setItem(STORAGE_KEY, normalized)
    else localStorage.removeItem(STORAGE_KEY)
    return normalized || DEFAULT_API_BASE
  } catch {
    return DEFAULT_API_BASE
  }
}

/** 是否是一个能被探测的绝对地址（用于判断能否直接访问，以及日志诊断） */
export function isAbsoluteApiBase(base = getApiBase()) {
  return /^https?:\/\//i.test(base)
}

/**
 * 探测某个服务器地址是否可用。
 *
 * 用 /ping（后端公开接口，不查库、不走鉴权）而不是带鉴权的接口：
 *   - 未登录时也能探测，返回 200 即说明「地址对 + 网络通」
 *   - 代价极小，不会因为库出问题而误判地址错误
 *
 * @param base 候选地址（会规范化）
 * @param timeoutMs 超时，默认 2500ms —— 地址写错时走的是 TCP 超时，
 *                  局域网正确地址通常 <200ms，2.5s 足够区分
 * @returns {Promise<boolean>}
 */
export async function probeApiBase(base, timeoutMs = 2500) {
  const raw = String(base ?? '').trim() || DEFAULT_API_BASE
  // 先判断是否已是绝对地址再规范化：
  // normalizeApiBase 会给无协议的值补 http://，但开发模式的默认值是相对路径
  // （/api/v1，由 vite 代理转发），补成 http:///api/v1 永远探测失败。
  let abs
  if (/^https?:\/\//i.test(raw)) {
    abs = normalizeApiBase(raw)
  } else if (raw.startsWith('/')) {
    try {
      abs = new URL(raw, window.location.origin).href
    } catch {
      abs = raw
    }
  } else {
    abs = normalizeApiBase(raw)
  }
  const url = `${String(abs).replace(/\/+$/, '')}/ping`
  const controller = typeof AbortController === 'function' ? new AbortController() : null
  const timer = setTimeout(() => controller?.abort(), timeoutMs)
  try {
    const res = await fetch(url, {
      method: 'GET',
      signal: controller?.signal,
      cache: 'no-store',
      headers: { Accept: 'application/json' }
    })
    // 只要 HTTP 通了就算可达；即使返回非 2xx 也说明地址是对的
    return res.ok || res.status === 401 || res.status === 403
  } catch {
    return false
  } finally {
    clearTimeout(timer)
  }
}

/**
 * 启动时的地址自检。
 *
 * 要解决的问题：内网穿透域名 / 局域网 DHCP 地址会变，而 localStorage 里
 * 存的是**上一次能用**的地址。地址一变，App 的每个请求都超时，
 * 界面上只显示「网络异常」，用户根本想不到是地址过期了。
 *
 * 策略：
 *   1) 当前地址能通 → 什么都不做（正常情况下零开销，一次轻量 GET）
 *   2) 当前地址不通，且与构建默认值不同 → 探测默认值
 *        能通 → 自动切回默认，并告知用户
 *        不通 → 不动，交由界面提示（可能是后端没开）
 *
 * @returns {Promise<{changed:boolean, from?:string, to?:string}>}
 */
export async function ensureApiBaseUsable() {
  const current = getApiBase()
  if (await probeApiBase(current)) return { changed: false }

  const fallback = DEFAULT_API_BASE
  // 比较前统一形态：相对路径不能被 normalizeApiBase 改成 http:///… 后拿来比
  const same = (a, b) => {
    const f = (v) => {
      const s = String(v ?? '').trim()
      if (s.startsWith('/')) return s.replace(/\/+$/, '')
      return normalizeApiBase(s)
    }
    return f(a) === f(b)
  }
  if (!fallback || same(fallback, current)) {
    return { changed: false }
  }
  if (await probeApiBase(fallback)) {
    // 删掉过期设置，回到构建默认值
    try {
      localStorage.removeItem(STORAGE_KEY)
    } catch {
      /* 隐私模式忽略 */
    }
    return { changed: true, from: current, to: fallback }
  }
  return { changed: false }
}

/** 后端文件（/files/xxx.jpg）转绝对地址；相对路径在 App 内会拼到 WebView 自身，必须补全 */
export function fileUrl(path) {
  if (!path) return ''
  if (/^https?:\/\//i.test(path)) return path
  const origin = getApiBase().replace(/\/api\/v1\/?$/i, '')
  return `${origin}${path.startsWith('/') ? '' : '/'}${path}`
}
