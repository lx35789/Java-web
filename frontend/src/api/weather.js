import request from './request'

/** 当前天气（后端按用户城市调用 Open-Meteo，并返回对养护计划的影响） */
export const getCurrentWeather = () => request.get('/weather/current')
export const syncWeather = () => request.post('/weather/sync')
