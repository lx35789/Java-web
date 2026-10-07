import request from './request'

/** 病害识别（病理性：真菌 / 细菌 / 虫害） */
export const diagnoseDisease = (data) => request.post('/diagnoses/disease', data)

/**
 * 症状诊断（生理性：黄叶 / 焦边 / 落叶…）
 * @param {{
 *   userPlantId?: number,
 *   photoId?: number,
 *   symptomCodes: string[],
 *   context: { leafPosition?: string, veinPattern?: string, causeHint?: string }
 * }} data
 *
 * 注意：wateringRatio / lightLevel / humidity / temp / season 等
 * 由后端自动从养护日志、植物档案、环境设置和天气推算，前端不必传。
 */
export const diagnoseSymptom = (data) => request.post('/diagnoses/symptom', data)

export const getDiagnoses = (params) => request.get('/diagnoses', { params })
export const getDiagnosisDetail = (id) => request.get(`/diagnoses/${id}`)
export const updateDiagnosisStatus = (id, status) => request.put(`/diagnoses/${id}/status`, { status })
