import axios from 'axios'
import { ElMessage } from 'element-plus'
import { ERROR_CODES } from '@/utils/constants'
import { getToken, removeToken } from '@/utils/auth'
import router from '@/router'
import { createMockAdapter } from '@/mock'

const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api/v1',
  timeout: 20000,
  headers: { 'Content-Type': 'application/json' }
})

/**
 * Mock 模式：直接替换 axios adapter。
 * 好处是 api/ 下的模块代码与真实模式完全一致，后端就绪后只需把
 * .env 里的 VITE_USE_MOCK 改成 false，业务代码零改动。
 */
if (USE_MOCK) {
  service.defaults.adapter = createMockAdapter()
  // eslint-disable-next-line no-console
  console.info('[植语助手] Mock 模式已开启（VITE_USE_MOCK=true），所有接口返回示例数据')
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
