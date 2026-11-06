/**
 * 原生相机 / 相册接入。
 *
 * 为什么不再直接用 <input type="file" capture>：
 *   Capacitor 的 BridgeWebChromeClient.onShowFileChooser 里，
 *   拍照分支依赖 `fileChooserParams.isCaptureEnabled()` 且
 *   `ACTION_IMAGE_CAPTURE.resolveActivity()` 不为 null。
 *   在部分国产 ROM（实测华为 Mate 60）上 resolveActivity 返回 null，
 *   Capacitor 会**静默降级**到普通文件选择器 —— 表现就是
 *   「点拍照却进了相册」，且没有任何报错。
 *
 *   改用 @capacitor/camera 后，相机由插件自己的 Intent 拉起，
 *   并且 source: 'Prompt' 会弹出系统级的「拍照 / 从相册选择」面板，
 *   行为与原生 App 一致。
 *
 * 浏览器环境下这套 API 不可用，调用方需回退到 input[type=file]。
 */

/** 是否运行在原生容器（Capacitor）里 */
export function isNative() {
  return typeof window !== 'undefined' && !!(window.Capacitor?.isNativePlatform?.())
}

/** 是否具备 Camera 插件 */
export function hasCameraPlugin() {
  return !!(window.Capacitor?.Plugins?.Camera)
}

/**
 * 让用户拍照或从相册选一张，返回 File。
 *
 * @param source 'Prompt' | 'Camera' | 'Photos'
 * @returns File；用户取消时返回 null
 */
export async function pickPhotoNative(source = 'Prompt') {
  const Camera = window.Capacitor?.Plugins?.Camera
  if (!Camera) throw new Error('Camera 插件不可用')

  const photo = await Camera.getPhoto({
    quality: 92,
    allowEditing: false,
    // 返回可 fetch 的 webPath，避免一次性把大图塞成 base64 字符串占内存
    resultType: 'uri',
    source,
    // 不要存进系统相册，避免用户的相册被测试图塞满
    saveToGallery: false,
    // 前端自己会再压一次，这里只做兜底
    width: 2048,
    height: 2048,
    correctOrientation: true,
    presentationStyle: 'popover'
  })

  if (!photo?.webPath) return null

  const res = await fetch(photo.webPath)
  const blob = await res.blob()
  const ext = (photo.format || 'jpeg').toLowerCase()
  const name = `photo-${Date.now()}.${ext === 'jpg' ? 'jpeg' : ext}`
  return new File([blob], name, { type: blob.type || `image/${ext}` })
}
