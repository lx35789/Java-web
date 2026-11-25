import request from './request'

export const getMyPlants = (params) => request.get('/my-plants', { params })
export const getMyPlantDetail = (id) => request.get(`/my-plants/${id}`)
export const createMyPlant = (data) => request.post('/my-plants', data)
export const updateMyPlant = (id, data) => request.put(`/my-plants/${id}`, data)
export const deleteMyPlant = (id) => request.delete(`/my-plants/${id}`)

/** 成长时间线（P2） */
export const getTimeline = (id) => request.get(`/my-plants/${id}/timeline`)

/** 自然语言添加植物（P2）："我新买了一盆龟背竹，放在客厅" */
export const parseNaturalLanguage = (text) => request.post('/my-plants/natural-language', { text })

/**
 * 把一次诊断结果认领成我的植物，并按这次诊断生成养护计划。
 * @param {{recordId:number, speciesId?:number, speciesName?:string,
 *   diseaseLabel?:string, diseaseCategory?:string, nickname?:string,
 *   location?:string, lightLevel?:string, potSize?:string,
 *   potMaterial?:string, soilType?:string}} data
 */
export const adoptFromDiagnosis = (data) => request.post('/my-plants/from-diagnosis', data)
