import request from './request'

/** 植物库列表（支持关键词 / 光照 / 难度 / 毒性筛选） */
export const getSpeciesList = (params) => request.get('/species', { params })

/** 植物详情（含养护规则与季节系数） */
export const getSpeciesDetail = (id) => request.get(`/species/${id}`)
