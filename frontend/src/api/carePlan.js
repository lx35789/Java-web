import request from './request'

export const getCarePlans = (params) => request.get('/care-plans', { params })
export const getCarePlanDetail = (id) => request.get(`/care-plans/${id}`)
export const generateCarePlans = (userPlantId) => request.post('/care-plans/generate', { userPlantId })
export const updateCarePlan = (id, data) => request.put(`/care-plans/${id}`, data)
export const disableCarePlan = (id) => request.delete(`/care-plans/${id}`)
