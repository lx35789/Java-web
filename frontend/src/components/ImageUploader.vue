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
        <div class="ph-buttons">
          <button type="button" class="ph-btn" @click.stop="pickCamera">
            <el-icon :size="22"><Camera /></el-icon>
            <span>拍照</span>
          </button>
          <button type="button" class="ph-btn" @click.stop="pickGallery">
            <el-icon :size="22"><Picture /></el-icon>
            <span>相册</span>
          </button>
        </div>
        <div class="ph-desc">{{ hint }}</div>
      </div>
      <div v-if="previewUrl" class="mask">{{ processing ? '正在压缩…' : '点击更换' }}</div>
    </div>

    <!--
      两个独立 input 是有意为之：
        capture 存在  → 移动端系统直接拉起相机，不会给"从相册选择"的选项
        capture 不存在 → 移动端弹出「相机 / 文件（相册）」选择器
      只用一个 input 无法同时保证两个入口，所以拆成两个。
      桌面浏览器两个都退化成文件选择器，因此桌面上不弹面板（见 onAreaClick）。
    -->
    <input
      ref="cameraRef"
      type="file"
      accept="image/*"
      :capture="capture || 'environment'"
      hidden
      @change="onChange"
    />
    <input ref="galleryRef" type="file" accept="image/*" hidden @change="onChange" />

    <div v-if="previewUrl" class="file-bar">
      <span class="file-name">{{ file?.name }}</span>
      <span class="gt-text-secondary gt-text-small">{{ sizeText }}</span>
      <el-button text type="danger" size="small" @click="clear">移除</el-button>
    </div>

    <!-- 底部动作面板：触屏设备上点图片/占位区时弹出，像原生 App 的相册选择 -->
    <teleport to="body">
      <transition name="sheet">
        <div v-if="sheetVisible" class="sheet-mask" @click.self="sheetVisible = false">
          <div class="sheet">
            <div class="sheet-title">{{ previewUrl ? '更换照片' : '添加照片' }}</div>
            <button type="button" class="sheet-item" @click="pickCamera">
              <el-icon :size="20"><Camera /></el-icon>
              <span>拍照</span>
            </button>
            <button type="button" class="sheet-item" @click="pickGallery">
              <el-icon :size="20"><Picture /></el-icon>
              <span>从相册选择</span>
            </button>
            <button v-if="previewUrl" type="button" class="sheet-item danger" @click="onSheetRemove">
              <el-icon :size="20"><Delete /></el-icon>
              <span>移除照片</span>
            </button>
            <button type="button" class="sheet-item cancel" @click="sheetVisible = false">取消</button>
          </div>
        </div>
      </transition>
    </teleport>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Loading, Camera, Picture, Delete } from '@element-plus/icons-vue'
import { compressImage, formatBytes } from '@/utils/image'

const props = defineProps({
  /** 选中的 File 对象 */
  modelValue: { type: Object, default: null },
  hint: { type: String, default: '支持 jpg / png，不超过 10MB' },
  /** 拍照时优先使用的摄像头：environment=后置（默认），user=前置 */
  capture: { type: String, default: 'environment' }
})

const emit = defineEmits(['update:modelValue', 'change'])

const cameraRef = ref(null)
const galleryRef = ref(null)
const previewUrl = ref('')
const processing = ref(false)
const originalSize = ref(0)
const sheetVisible = ref(false)
let objectUrl = ''

const file = computed(() => props.modelValue)

const sizeText = computed(() => {
  const s = props.modelValue?.size
  if (!s) return ''
  const now = formatBytes(s)
  // 压缩后体积明显变小时，把原大小也标出来，避免用户误以为画质被严重破坏
  if (originalSize.value > s * 1.15) {
    return `${now}（原 ${formatBytes(originalSize.value)}）`
  }
  return now
})

/**
 * 是否为触摸设备（手机/平板）。
 *
 * 为什么要判断：桌面浏览器的 input[capture] 不会打开摄像头，而是退化成普通的
 * 文件选择器。桌面上再弹「拍照 / 相册」面板等于让用户多点一次无意义的按钮，
 * 所以桌面直接打开文件选择。
 */
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

function onAreaClick() {
  if (processing.value) return
  if (isTouch()) {
    sheetVisible.value = true
  } else {
    pickGallery()
  }
}

function pickCamera() {
  sheetVisible.value = false
  // 有些浏览器要求 click 与用户手势在同一事件循环内，故用 setTimeout 让面板关闭先渲染
  setTimeout(() => cameraRef.value?.click(), 0)
}

function pickGallery() {
  sheetVisible.value = false
  setTimeout(() => galleryRef.value?.click(), 0)
}

function onSheetRemove() {
  sheetVisible.value = false
  clear()
}

async function onChange(e) {
  const f = e.target.files?.[0]
  // 允许重复选同一个文件
  e.target.value = ''
  if (!f) return

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

.ph-buttons {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  margin-bottom: 10px;
}

.ph-btn {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  width: 82px;
  padding: 12px 0 10px;
  border: 1px solid var(--gt-border);
  border-radius: 10px;
  background: #fff;
  color: var(--gt-primary);
  font-size: 13px;
  cursor: pointer;
  transition: border-color 0.18s ease, background 0.18s ease, transform 0.1s ease;
}

.ph-btn:hover {
  border-color: var(--gt-primary);
  background: #f3f8f5;
}

.ph-btn:active {
  transform: scale(0.97);
}

.ph-desc {
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

/* ---------- 底部动作面板 ---------- */
.sheet-mask {
  position: fixed;
  inset: 0;
  z-index: 3000;
  background: rgba(0, 0, 0, 0.42);
  display: flex;
  align-items: flex-end;
}

.sheet {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 1px;
  background: #eef1ef;
  border-radius: 14px 14px 0 0;
  padding: 8px 10px calc(10px + env(safe-area-inset-bottom, 0px));
  box-sizing: border-box;
}

.sheet-title {
  text-align: center;
  font-size: 12px;
  color: var(--gt-text-secondary);
  padding: 8px 0 10px;
}

.sheet-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 15px 14px;
  border: none;
  background: #fff;
  color: var(--gt-text-regular);
  font-size: 15px;
  text-align: left;
  cursor: pointer;
}

.sheet-item:first-of-type {
  border-radius: 10px 10px 0 0;
}

.sheet-item:active {
  background: #f2f5f3;
}

.sheet-item.danger {
  color: #d9534f;
}

.sheet-item.cancel {
  margin-top: 8px;
  justify-content: center;
  border-radius: 10px;
  background: #f4f6f5;
  color: var(--gt-text-secondary);
  font-size: 14px;
}

.sheet-enter-active,
.sheet-leave-active {
  transition: opacity 0.2s ease;
}

.sheet-enter-active .sheet,
.sheet-leave-active .sheet {
  transition: transform 0.24s cubic-bezier(0.22, 0.61, 0.36, 1);
}

.sheet-enter-from,
.sheet-leave-to {
  opacity: 0;
}

.sheet-enter-from .sheet,
.sheet-leave-to .sheet {
  transform: translateY(100%);
}
</style>
