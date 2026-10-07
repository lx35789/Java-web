import request from './request'

export const getCareTasks = (params) => request.get('/care-tasks', { params })
export const getCareCalendar = (month) => request.get('/care-tasks/calendar', { params: { month } })
export const getTodayTasks = () => request.get('/care-tasks/today')

/** 标记完成：后端会同时写入一条养护日志并回填关联 */
export const completeTask = (id, data) => request.post(`/care-tasks/${id}/complete`, data)
export const skipTask = (id) => request.post(`/care-tasks/${id}/skip`)
export const snoozeTask = (id, dueDate) => request.post(`/care-tasks/${id}/snooze`, { dueDate })
