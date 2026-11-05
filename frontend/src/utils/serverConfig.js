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

/** 是否是绝对地址（用于判断能否直接访问，以及日志诊断） */
export function isAbsoluteApiBase(base = getApiBase()) {
  return /^https?:\/\//i.test(base)
}

/** 后端文件（/files/xxx.jpg）转绝对地址；相对路径在 App 内会拼到 WebView 自身，必须补全 */
export function fileUrl(path) {
  if (!path) return ''
  if (/^https?:\/\//i.test(path)) return path
  const origin = getApiBase().replace(/\/api\/v1\/?$/i, '')
  return `${origin}${path.startsWith('/') ? '' : '/'}${path}`
}
