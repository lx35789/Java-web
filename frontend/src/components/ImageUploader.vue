<template>
  <div class="image-uploader">
    <div class="upload-area" :class="{ 'has-image': !!previewUrl }" @click="pick">
      <img v-if="previewUrl" :src="previewUrl" class="preview" alt="预览" />
      <div v-else class="placeholder">
        <el-icon :size="30"><Plus /></el-icon>
        <div class="ph-title">点击上传照片</div>
        <div class="ph-desc">{{ hint }}</div>
      </div>
      <div v-if="previewUrl" class="mask">重新选择</div>
    </div>

    <input
      ref="inputRef"
      type="file"
      accept="image/*"
      :capture="capture || undefined"
      hidden
      @change="onChange"
    />

    <div v-if="previewUrl" class="file-bar">
      <span class="file-name">{{ file?.name }}</span>
      <span class="gt-text-secondary gt-text-small">{{ sizeText }}</span>
      <el-button text type="danger" size="small" @click="clear">移除</el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps({
  /** 选中的 File 对象 */
  modelValue: { type: Object, default: null },
  hint: { type: String, default: '支持 jpg / png，不超过 10MB' },
  /** 移动端直接调用后置摄像头 */
  capture: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue', 'change'])

const inputRef = ref(null)
const previewUrl = ref('')
let objectUrl = ''

const file = computed(() => props.modelValue)

const sizeText = computed(() => {
  const s = props.modelValue?.size
  if (!s) return ''
  return s < 1024 * 1024 ? `${(s / 1024).toFixed(0)} KB` : `${(s / 1024 / 1024).toFixed(1)} MB`
})

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

function pick() {
  inputRef.value?.click()
}

function onChange(e) {
  const f = e.target.files?.[0]
  if (!f) return
  emit('update:modelValue', f)
  emit('change', f)
  // 允许重复选同一个文件
  e.target.value = ''
}

function clear() {
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
}

.ph-title {
  margin-top: 8px;
  font-size: 14px;
  color: var(--gt-text-regular);
}

.ph-desc {
  margin-top: 4px;
  font-size: 12px;
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
