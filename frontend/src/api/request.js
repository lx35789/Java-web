import axios from 'axios'
import { ElMessage } from 'element-plus'
import { ERROR_CODES } from '@/utils/constants'
import { getToken, removeToken } from '@/utils/auth'
import router from '@/router'
import { createMockAdapter } from '@/mock'

const MOCK_MODE = import.meta.env.VITE_USE_MOCK || 'false'
const USE_MOCK = MOCK_MODE === 'true'

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api/v1',
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
 */
if (MOCK_MODE === 'true') {
  service.defaults.adapter = createMockAdapter()
  // eslint-disable-next-line no-console
  console.info('[植语助手] Mock 模式（VITE_USE_MOCK=true），所有接口返回示例数据')
} else if (MOCK_MODE === 'hybrid') {
  const mockAdapter = createMockAdapter()
  const realAdapter =
    typeof axios.getAdapter === 'function' ? axios.getAdapter(axios.defaults.adapter) : axios.defaults.adapter

  service.defaults.adapter = async (config) => {
    try {
      return await realAdapter(config)
    } catch (err) {
      // 只有「后端没这个接口」才回退；401/403/500/网络错误照常抛出，
      // 否则真实故障会被 Mock 掩盖，联调时反而更难查。
      if (err && err.response && err.response.status === 404) {
        // eslint-disable-next-line no-console
        console.info(
          '[植语助手] 后端未实现，回退 Mock：',
          String(config.method || 'get').toUpperCase(),
          config.url
        )
        return mockAdapter(config)
      }
      throw err
    }
  }
  // eslint-disable-next-line no-console
  console.info('[植语助手] 混合模式（VITE_USE_MOCK=hybrid）：优先真实后端，未实现的接口回退 Mock')
}

/* ---------------- 请求拦截 ---------------- */
service.interceptors.request.use(
  (config) => {
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
