import axios from 'axios'
import { ElMessage } from 'element-plus'
import { ERROR_CODES } from '@/utils/constants'
import { getToken, removeToken } from '@/utils/auth'
import { getApiBase } from '@/utils/serverConfig'
import router from '@/router'
import { createMockAdapter } from '@/mock'

const MOCK_MODE = import.meta.env.VITE_USE_MOCK || 'false'
const USE_MOCK = MOCK_MODE === 'true'

const service = axios.create({
  // 初始值；每次请求会在拦截器里用「运行时配置」覆盖 ——
  // 这样用户在 App 内改服务器地址后立刻生效，不必重新打包。
  baseURL: getApiBase(),
  timeout: 20000,
  headers: { 'Content-Type': 'application/json' }
})

/**
 * 三种模式（.env 里的 VITE_USE_MOCK）：
 *   true   —— 全部走 Mock
 *   false  —— 全部走真实后端
 *   hybrid —— 优先真实后端；后端返回 404（接口还没写）时自动回退 Mock
 *
 * 混合模式是给「后端接口一个一个补」的过渡期用的：
 * 对接好的接口用真实数据，没写的继续用 Mock 顶着，页面始终可用。
 *
 * ── 性能相关的三件事（换到公网/隧道访问后尤其明显）────────────────
 * 1. GET 响应短缓存（30s）：物种列表、用户信息这类不常变的数据不再重复打网络
 * 2. 404 负缓存：后端未实现的接口只探测一次，之后直接走 Mock，
 *    否则每次请求都要白等一个完整的往返（隧道下就是 0.8 秒）
 * 3. 相同请求合并：同一时刻的重复 GET 只发一次
 * 写操作（POST/PUT/DELETE）会清空 GET 缓存，避免读到旧数据。
 */
const mockAdapter = MOCK_MODE === 'true' || MOCK_MODE === 'hybrid' ? createMockAdapter() : null
const realAdapter =
  typeof axios.getAdapter === 'function' ? axios.getAdapter(axios.defaults.adapter) : axios.defaults.adapter

/** 后端尚未实现的接口路径（按 服务器地址 + 路径 记录），本次会话内不再重复探测 */
const MISSING_PATHS = new Set()
/** GET 响应缓存：key -> { expires, response } */
const GET_CACHE = new Map()
/** 进行中的 GET 请求：key -> Promise */
const INFLIGHT = new Map()
const CACHE_TTL_MS = 30 * 1000

function normalizePath(url) {
  return String(url || '')
    .split('?')[0]
    .replace(/\/\d+(?=\/|$)/g, '/:id')
}

function isGet(config) {
  return String(config.method || 'get').toLowerCase() === 'get'
}

function cacheKeyOf(config) {
  const params = config.params ? JSON.stringify(config.params) : ''
  return `${getApiBase()}|${config.url}|${params}`
}

function missingKeyOf(config) {
  return `${getApiBase()}|${normalizePath(config.url)}`
}

async function dispatch(config) {
  if (MOCK_MODE === 'true') return mockAdapter(config)

  if (MOCK_MODE === 'hybrid') {
    // 已确认后端没有这个接口 → 直接 Mock，不再白跑一次 404
    if (MISSING_PATHS.has(missingKeyOf(config))) return mockAdapter(config)
    try {
      return await realAdapter(config)
    } catch (err) {
      // 只有「后端没这个接口」才回退；401/403/500/网络错误照常抛出，
      // 否则真实故障会被 Mock 掩盖，联调时反而更难查。
      if (err && err.response && err.response.status === 404) {
        MISSING_PATHS.add(missingKeyOf(config))
        // eslint-disable-next-line no-console
        console.info(
          '[植语助手] 后端未实现（已记住，后续直接走 Mock）：',
          String(config.method || 'get').toUpperCase(),
          config.url
        )
        return mockAdapter(config)
      }
      throw err
    }
  }

  return realAdapter(config)
}

service.defaults.adapter = async (config) => {
  const get = isGet(config)

  // 写操作后清空读缓存，保证「改完立刻看到新数据」
  if (!get) {
    GET_CACHE.clear()
    return dispatch(config)
  }

  // 允许单次请求禁用缓存：request.get(url, { cache: false })
  if (config.cache === false) return dispatch(config)

  const key = cacheKeyOf(config)
  const hit = GET_CACHE.get(key)
  if (hit && hit.expires > Date.now()) return hit.response

  // 同一请求已经在路上 → 复用，避免页面里多处同时拉同一份数据
  if (INFLIGHT.has(key)) return INFLIGHT.get(key)

  const task = (async () => {
    const res = await dispatch(config)
    // 只缓存业务成功的结果，错误响应不缓存
    if (res && res.data && res.data.code === 0) {
      GET_CACHE.set(key, { expires: Date.now() + CACHE_TTL_MS, response: res })
    }
    return res
  })()

  INFLIGHT.set(key, task)
  const cleanup = () => INFLIGHT.delete(key)
  task.then(cleanup, cleanup)
  return task
}

/**
 * 清空请求缓存。
 * 登录/退出、切换服务器地址、手动刷新时调用，避免读到上一个用户的数据。
 */
export function clearRequestCache() {
  GET_CACHE.clear()
  MISSING_PATHS.clear()
}

if (MOCK_MODE === 'true') {
  // eslint-disable-next-line no-console
  console.info('[植语助手] Mock 模式（VITE_USE_MOCK=true），所有接口返回示例数据')
} else if (MOCK_MODE === 'hybrid') {
  // eslint-disable-next-line no-console
  console.info('[植语助手] 混合模式（VITE_USE_MOCK=hybrid）：优先真实后端，未实现的接口回退 Mock')
}

/* ---------------- 请求拦截 ---------------- */
service.interceptors.request.use(
  (config) => {
    // 运行时读取服务器地址：App 内改完立刻生效，旧请求不受影响
    config.baseURL = getApiBase()
    const token = getToken()
    if (token) config.headers.Authorization = `Bearer ${token}`
    return config
  },
  (error) => Promise.reject(error)
)

/* ---------------- 响应拦截 ---------------- */
service.interceptors.response.use(
  (response) => {
    const body = response.data

    // 非标准响应（例如直接返回文件流）原样透传
    if (!body || typeof body !== 'object' || !('code' in body)) return body

    // 统一响应体：{ code, message, data, traceId }
    if (body.code === 0) return body.data

    // 未登录 / 登录过期
    if (body.code === 1002) {
      removeToken()
      ElMessage.error(ERROR_CODES[1002])
      router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
      return Promise.reject(body)
    }

    const msg = body.message || ERROR_CODES[body.code] || `请求失败（${body.code}）`
    ElMessage.error(msg)
    return Promise.reject(body)
  },
  (error) => {
    let msg = '网络异常，请稍后重试'
    if (error.code === 'ECONNABORTED') msg = '请求超时'
    else if (error.response) {
      const { status } = error.response
      if (status === 401) {
        removeToken()
        msg = '登录已过期，请重新登录'
        router.push('/login')
      } else if (status === 403) msg = '没有权限执行该操作'
      else if (status === 404) msg = '接口不存在'
      else if (status >= 500) msg = '服务器开小差了'
    }
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default service
export { USE_MOCK }
