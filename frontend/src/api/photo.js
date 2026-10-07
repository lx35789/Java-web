import request from './request'

/**
 * 上传照片
 * @param {File} file
 * @param {{photoType?: string, userPlantId?: number}} extra
 */
export function uploadPhoto(file, extra = {}) {
  const form = new FormData()
  form.append('file', file)
  if (extra.photoType) form.append('photoType', extra.photoType)
  if (extra.userPlantId) form.append('userPlantId', extra.userPlantId)
  return request.post('/photos', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export const deletePhoto = (id) => request.delete(`/photos/${id}`)
