import request from './request'

export const getCareLogs = (params) => request.get('/care-logs', { params })
export const createCareLog = (data) => request.post('/care-logs', data)
export const updateCareLog = (id, data) => request.put(`/care-logs/${id}`, data)
export const deleteCareLog = (id) => request.delete(`/care-logs/${id}`)
