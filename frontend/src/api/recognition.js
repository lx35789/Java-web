import request from './request'

/** 物种识别：返回 Top-3 + 置信度，低置信度时后端会自动走 Pl@ntNet 兜底 */
export const recognizeSpecies = (photoId) => request.post('/recognitions', { photoId })

export const getRecognition = (id) => request.get(`/recognitions/${id}`)

/** 采纳 / 纠正识别结果；纠正结果会落库，用于统计真实场景准确率 */
export const confirmRecognition = (id, data) => request.post(`/recognitions/${id}/confirm`, data)

export const getRecognitionHistory = (params) => request.get('/recognitions/history', { params })
