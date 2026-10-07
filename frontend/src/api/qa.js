import request from './request'

export const getQaSessions = () => request.get('/qa/sessions')
export const createQaSession = (data) => request.post('/qa/sessions', data)
export const getQaMessages = (sessionId) => request.get(`/qa/sessions/${sessionId}`)

/**
 * 提问
 * 真实后端返回 text/event-stream（SSE 流式），事件序列：
 *   retrieval → delta* → citations → done
 * 骨架阶段 Mock 返回一次性完整消息，接入时替换为流式解析。
 */
export const sendQaMessage = (sessionId, data) => request.post(`/qa/sessions/${sessionId}/messages`, data)

export const feedbackQaMessage = (messageId, feedback) => request.post(`/qa/messages/${messageId}/feedback`, { feedback })
