import request from './request'

export const getStatsOverview = () => request.get('/stats/overview')
export const getWateringTrend = (weeks = 8) => request.get('/stats/watering-trend', { params: { weeks } })
export const getHealthDistribution = () => request.get('/stats/health-distribution')
