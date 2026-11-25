import request from './request'
import { getApiBase } from '@/utils/serverConfig'
import { getToken } from '@/utils/auth'

/* ==================== 会话 ==================== */

export const getQaSessions = () => request.get('/qa/sessions')
export const createQaSession = (data) => request.post('/qa/sessions', data)
export const getQaSession = (id) => request.get(`/qa/sessions/${id}`)
export const updateQaSession = (id, data) => request.put(`/qa/sessions/${id}`, data)
export const deleteQaSession = (id) => request.delete(`/qa/sessions/${id}`)
export const feedbackQaMessage = (id, feedback) =>
  request.post(`/qa/messages/${id}/feedback`, { feedback })

/* ==================== 流式提问 ==================== */

/**
 * 向千问提问，SSE 流式拿回答。
 *
 * 后端事件序列：meta → delta × N → done（出错则是 error 代替 done）。
 * 不用 EventSource 的原因：EventSource 只能 GET，既带不了请求体，也带不了 Authorization 头。
 *
 * @param {number} sessionId
 * @param {{content:string, userPlantId?:number|null, diagnosisContext?:string}} data
 * @param {{onMeta?:Function,onDelta?:Function,onDone?:Function,onError?:Function,signal?:AbortSignal}} handlers
 */
export async function streamQaMessage(sessionId, data, handlers = {}) {
  const { onMeta, onDelta, onDone, onError, signal } = handlers
  const token = getToken()

  const resp = await fetch(`${getApiBase()}/qa/sessions/${sessionId}/messages`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify(data),
    signal
  })

  if (resp.status === 401) {
    throw Object.assign(new Error('登录已过期，请重新登录'), { status: 401 })
  }
  if (!resp.ok) {
    let detail = ''
    try {
      const body = await resp.json()
      detail = body?.message || ''
    } catch {
      /* 非 JSON 响应，忽略 */
    }
    throw Object.assign(new Error(detail || `请求失败（${resp.status}）`), { status: resp.status })
  }
  if (!resp.body) throw new Error('当前环境不支持流式响应')

  const reader = resp.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  const handleFrame = (raw) => {
    let event = 'message'
    const dataLines = []
    for (const line of raw.split('\n')) {
      const trimmed = line.replace(/\r$/, '')
      if (trimmed.startsWith('event:')) event = trimmed.slice(6).trim()
      else if (trimmed.startsWith('data:')) dataLines.push(trimmed.slice(5).trim())
    }
    if (!dataLines.length) return
    let payload = {}
    try {
      payload = JSON.parse(dataLines.join('\n'))
    } catch {
      payload = { text: dataLines.join('\n') }
    }
    if (event === 'meta') onMeta?.(payload)
    else if (event === 'delta') onDelta?.(payload.text || '')
    else if (event === 'done') onDone?.(payload)
    else if (event === 'error') onError?.(payload.message || '回答失败')
  }

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    let idx
    while ((idx = buffer.indexOf('\n\n')) >= 0) {
      const raw = buffer.slice(0, idx)
      buffer = buffer.slice(idx + 2)
      if (raw.trim()) handleFrame(raw)
    }
  }
}
