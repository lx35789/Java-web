import request from './request'

export const login = (data) => request.post('/auth/login', data)
export const register = (data) => request.post('/auth/register', data)
export const logout = () => request.post('/auth/logout')
export const refresh = () => request.post('/auth/refresh')

export const getProfile = () => request.get('/users/me')
export const updateProfile = (data) => request.put('/users/me', data)
export const changePassword = (data) => request.put('/users/me/password', data)

export const getEnvironment = () => request.get('/users/me/environment')
export const updateEnvironment = (data) => request.put('/users/me/environment', data)
