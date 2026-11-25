import request from './request'

/** 健康曲线：点 + 摘要 + 拐点 */
export const getObservationSeries = (plantId, days = 90) =>
  request.get(`/my-plants/${plantId}/observations`, { params: { days } })

/** 最近一次观测 */
export const getLatestObservation = (plantId) =>
  request.get(`/my-plants/${plantId}/observations/latest`)

/**
 * 记录一次状态快评
 * @param {number} plantId
 * @param {{leafScore:number, growthScore:number, yellowLeaf:number, note?:string, photoId?:number}} data
 */
export const createObservation = (plantId, data) =>
  request.post(`/my-plants/${plantId}/observations`, data)
