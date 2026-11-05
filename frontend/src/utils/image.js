/**
 * 图片压缩（上传前）
 *
 * 为什么必须做：
 *   手机主摄一张 3–8MB，后端限制 10MB，且外网/隧道上传带宽有限。
 *   不压缩时，走公网隧道上传一张图可能要几十秒，甚至直接 413。
 *
 * 策略：
 *   - 长边压到 1600px、JPEG 质量 0.8 —— 对叶片识别足够清晰，体积通常降到 200–600KB
 *   - 本来就很小的图（≤1.2MB 且长边已 ≤1600）不重编码，避免无谓的画质损失
 *   - 压缩失败（格式不支持、浏览器限制）时**原样返回**，绝不阻断上传
 */

const DEFAULT_MAX_EDGE = 1600
const DEFAULT_QUALITY = 0.8
const SKIP_BELOW_BYTES = 1.2 * 1024 * 1024

function isImage(file) {
  return !!file && typeof file.type === 'string' && file.type.startsWith('image/')
}

async function loadBitmap(file) {
  // createImageBitmap 能正确处理 EXIF 方向，且比 <img> 更省内存
  if (typeof createImageBitmap === 'function') {
    try {
      return await createImageBitmap(file, { imageOrientation: 'from-image' })
    } catch {
      /* 某些浏览器不支持该选项，退回 <img> */
    }
  }
  return await new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      URL.revokeObjectURL(url)
      resolve(img)
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('图片解码失败'))
    }
    img.src = url
  })
}

function toJpgName(name) {
  const base = (name || 'photo').replace(/\.[^.]+$/, '')
  return `${base}.jpg`
}

/**
 * 压缩一张图片。
 * @param {File} file 原始文件
 * @param {{maxEdge?: number, quality?: number}} [options]
 * @returns {Promise<File>} 压缩后的 File；无法压缩时返回原文件
 */
export async function compressImage(file, options = {}) {
  const maxEdge = options.maxEdge || DEFAULT_MAX_EDGE
  const quality = options.quality || DEFAULT_QUALITY

  if (!isImage(file)) return file
  // GIF 可能是动图，重编码会丢帧
  if (file.type === 'image/gif') return file

  let bitmap = null
  try {
    bitmap = await loadBitmap(file)
    const width = bitmap.width || bitmap.naturalWidth || 0
    const height = bitmap.height || bitmap.naturalHeight || 0
    if (!width || !height) return file

    const longest = Math.max(width, height)
    const scale = Math.min(1, maxEdge / longest)

    // 已经够小 → 不做有损重编码
    if (scale === 1 && file.size <= SKIP_BELOW_BYTES) return file

    const targetW = Math.max(1, Math.round(width * scale))
    const targetH = Math.max(1, Math.round(height * scale))

    const canvas = document.createElement('canvas')
    canvas.width = targetW
    canvas.height = targetH
    const ctx = canvas.getContext('2d')
    if (!ctx) return file
    // 白底：PNG 透明区域转 JPEG 后会变黑
    ctx.fillStyle = '#ffffff'
    ctx.fillRect(0, 0, targetW, targetH)
    ctx.drawImage(bitmap, 0, 0, targetW, targetH)

    const blob = await new Promise((resolve) =>
      canvas.toBlob(resolve, 'image/jpeg', quality)
    )
    if (blob && blob.size > 0 && blob.size < file.size) {
      return new File([blob], toJpgName(file.name), {
        type: 'image/jpeg',
        lastModified: Date.now()
      })
    }
    return file
  } catch {
    // 压缩属于优化，失败绝不能阻断用户上传
    return file
  } finally {
    if (bitmap && typeof bitmap.close === 'function') bitmap.close()
  }
}

/** 人类可读的体积文本 */
export function formatBytes(bytes) {
  if (!bytes && bytes !== 0) return ''
  return bytes < 1024 * 1024
    ? `${(bytes / 1024).toFixed(0)} KB`
    : `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
