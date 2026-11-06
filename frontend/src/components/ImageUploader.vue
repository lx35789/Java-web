<template>
  <div class="image-uploader">
    <div class="upload-area" :class="{ 'has-image': !!previewUrl }" @click="onAreaClick">
      <img v-if="previewUrl" :src="previewUrl" class="preview" alt="预览" />
      <div v-else-if="processing" class="placeholder">
        <el-icon :size="30" class="is-loading"><Loading /></el-icon>
        <div class="ph-title">正在压缩图片…</div>
        <div class="ph-desc">大图会压到长边 1600px，上传更快</div>
      </div>
      <div v-else class="placeholder">
        <el-icon :size="34" class="ph-icon"><Camera /></el-icon>
        <div class="ph-title">点击拍照</div>
        <div class="ph-desc">{{ hint }}</div>
      </div>
      <div v-if="previewUrl" class="mask">{{ processing ? '正在压缩…' : '点击重拍' }}</div>
    </div>

    <!--
      只有一个 input：相册入口。
      相机不再走 input[capture] —— 那条路在部分国产 ROM 上会因
      ACTION_IMAGE_CAPTURE 解析失败而静默退化成相册（表现就是「点拍照进了相册」）。
      现在相机由应用内 CameraCapture 组件负责，本 input 只服务「从相册选」。
    -->
    <input ref="galleryRef" type="file" accept="image/*" hidden @change="onChange" />

    <div v-if="previewUrl" class="file-bar">
      <span class="file-name">{{ file?.name }}</span>
      <span class="gt-text-secondary gt-text-small">{{ sizeText }}</span>
      <el-button text type="danger" size="small" @click="clear">移除</el-button>
    </div>

    <!-- 应用内相机：左下角有「相册」入口 -->
    <CameraCapture
      v-model:visible="cameraVisible"
      :facing="capture || 'environment'"
      :hint="hint"
      @capture="onCameraCapture"
      @gallery="onGalleryFromCamera"
      @fallback="useSystemCamera"
    />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Loading, Camera } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { compressImage, formatBytes } from '@/utils/image'
import { hasCameraPlugin, isNative, pickPhotoNative } from '@/utils/nativeCamera'
import CameraCapture from '@/components/CameraCapture.vue'

const props = defineProps({
  /** 选中的 File 对象 */
  modelValue: { type: Object, default: null },
  hint: { type: String, default: '把叶片放进取景框，光线充足、主体居中' },
  /** 相机朝向：environment=后置（默认） user=前置 */
  capture: { type: String, default: 'environment' }
})

const emit = defineEmits(['update:modelValue', 'change'])

const galleryRef = ref(null)
const previewUrl = ref('')
const processing = ref(false)
const originalSize = ref(0)
const cameraVisible = ref(false)
let objectUrl = ''

/** 原生容器且装了 Camera 插件 —— 用于 getUserMedia 失败时兜底 */
const nativeMode = isNative() && hasCameraPlugin()

const file = computed(() => props.modelValue)

const sizeText = computed(() => {
  const s = props.modelValue?.size
  if (!s) return ''
  const now = formatBytes(s)
  if (originalSize.value > s * 1.15) {
    return `${now}（原 ${formatBytes(originalSize.value)}）`
  }
  return now
})

/** 触摸设备才用应用内相机；桌面上 getUserMedia 通常无摄像头，直接开文件选择 */
function isTouch() {
  try {
    return window.matchMedia?.('(pointer: coarse)')?.matches ?? false
  } catch {
    return false
  }
}

function revoke() {
  if (objectUrl) {
    URL.revokeObjectURL(objectUrl)
    objectUrl = ''
  }
  previewUrl.value = ''
}

watch(
  () => props.modelValue,
  (f) => {
    if (!f) {
      revoke()
      return
    }
    revoke()
    objectUrl = URL.createObjectURL(f)
    previewUrl.value = objectUrl
  }
)

onBeforeUnmount(revoke)

/** 点一下直接拍照，不再弹「拍照 / 相册」选择 */
function onAreaClick() {
  if (processing.value) return
  if (isTouch()) {
    cameraVisible.value = true
  } else {
    // 桌面：没有摄像头，直接开文件选择
    galleryRef.value?.click()
  }
}

/** 应用内相机拍到照片 */
async function onCameraCapture(f) {
  await consume(f)
}

/** 在相机界面点左下角「相册」 */
function onGalleryFromCamera() {
  cameraVisible.value = false
  setTimeout(() => {
    if (nativeMode) {
      void pickFromNativeAlbum()
    } else {
      galleryRef.value?.click()
    }
  }, 120)
}

async function pickFromNativeAlbum() {
  processing.value = true
  try {
    const f = await pickPhotoNative('Photos')
    if (f) await consume(f)
  } catch (e) {
    const msg = String(e?.message || e || '')
    if (!/cancel/i.test(msg)) ElMessage.error('打开相册失败：' + msg)
  } finally {
    processing.value = false
  }
}

/** getUserMedia 不可用时的兜底：交给系统相机 */
async function useSystemCamera() {
  cameraVisible.value = false
  if (!nativeMode) {
    // 浏览器环境没有原生插件，退回文件选择
    galleryRef.value?.click()
    return
  }
  processing.value = true
  try {
    const f = await pickPhotoNative('Camera')
    if (f) await consume(f)
  } catch (e) {
    const msg = String(e?.message || e || '')
    if (!/cancel/i.test(msg)) ElMessage.error('打开相机失败：' + msg)
  } finally {
    processing.value = false
  }
}

async function onChange(e) {
  const f = e.target.files?.[0]
  e.target.value = ''   // 允许重复选同一个文件
  if (!f) return
  await consume(f)
}

async function consume(f) {
  processing.value = true
  try {
    const compressed = await compressImage(f)
    originalSize.value = f.size
    emit('update:modelValue', compressed)
    emit('change', compressed)
  } finally {
    processing.value = false
  }
}

function clear() {
  originalSize.value = 0
  emit('update:modelValue', null)
  emit('change', null)
}
</script>

<style scoped>
.upload-area {
  position: relative;
  border: 1px dashed var(--gt-border);
  border-radius: var(--gt-radius);
  background: #fafbfc;
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  overflow: hidden;
  transition: border-color 0.18s ease, background 0.18s ease;
}

.upload-area:hover {
  border-color: var(--gt-primary);
  background: #f7fbf9;
}

.upload-area.has-image {
  border-style: solid;
  background: #000;
}

.preview {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.placeholder {
  text-align: center;
  color: var(--gt-text-secondary);
  width: 100%;
  padding: 0 12px;
  box-sizing: border-box;
}

.ph-icon {
  color: var(--gt-primary);
}

.ph-title {
  margin-top: 8px;
  font-size: 15px;
  color: var(--gt-text-regular);
}

.ph-desc {
  margin-top: 5px;
  font-size: 12px;
  line-height: 1.5;
}

.mask {
  position: absolute;
  inset: auto 0 0 0;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: 12px;
  text-align: center;
  padding: 6px;
  opacity: 0;
  transition: opacity 0.18s ease;
}

.upload-area:hover .mask {
  opacity: 1;
}

.file-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--gt-text-regular);
}

.file-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
