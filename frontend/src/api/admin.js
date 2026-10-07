import request from './request'

/* 植物库维护 */
export const adminGetSpecies = (params) => request.get('/admin/species', { params })
export const adminUpdateSpecies = (id, data) => request.put(`/admin/species/${id}`, data)

/* 病害信息 */
export const adminGetDiseases = (params) => request.get('/admin/diseases', { params })

/* 症状判因规则（可调权重） */
export const adminGetSymptomRules = (params) => request.get('/admin/symptom-rules', { params })
export const adminCreateSymptomRule = (data) => request.post('/admin/symptom-rules', data)
export const adminUpdateSymptomRule = (id, data) => request.put(`/admin/symptom-rules/${id}`, data)
export const adminDeleteSymptomRule = (id) => request.delete(`/admin/symptom-rules/${id}`)

/* 知识库 */
export const adminGetArticles = (params) => request.get('/admin/knowledge/articles', { params })
export const adminReindexArticle = (id) => request.post(`/admin/knowledge/articles/${id}/reindex`)

/* 统计 */
export const adminGetAiUsage = () => request.get('/admin/stats/ai-usage')
export const adminGetRecognitionAccuracy = () => request.get('/admin/stats/recognition-accuracy')
