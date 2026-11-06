<template>
  <teleport to="body">
    <transition name="cam">
      <div v-if="visible" class="cam-root">
        <!-- 预览画面 -->
        <video
          ref="videoRef"
          class="cam-video"
          :class="{ mirrored: facing === 'user' }"
          autoplay
          playsinline
          muted
        />

        <!-- 取景框：引导用户把叶片放中间 -->
        <div class="cam-frame">
          <div class="corner tl" /><div class="corner tr" />
          <div class="corner bl" /><div class="corner br" />
        </div>

        <!-- 顶部栏 -->
        <div class="cam-top">
          <button type="button" class="cam-icon-btn" @click="dismiss">
            <el-icon :size="22"><Close /></el-icon>
          </button>
          <div class="cam-hint">{{ hint }}</div>
          <button type="button" class="cam-icon-btn" @click="toggleFlash" :class="{ on: flashOn }">
            <el-icon :size="22"><Sunny /></el-icon>
          </button>
        </div>

        <!-- 状态提示 -->
        <div v-if="state !== 'ready'" class="cam-state">
          <template v-if="state === 'opening'">
            <el-icon :size="26" class="is-loading"><Loading /></el-icon>
            <span>正在打开相机…</span>
          </template>
          <template v-else-if="state === 'error'">
            <el-icon :size="26"><WarningFilled /></el-icon>
            <span>{{ errorMsg }}</span>
            <button type="button" class="cam-link" @click="emit('fallback')">改用系统相机</button>
          </template>
        </div>

        <!-- 底部操作栏：左「相册」 中「快门」 右「翻转」 -->
        <div class="cam-bottom">
          <button type="button" class="cam-side" @click="emit('gallery')">
            <el-icon :size="24"><Picture /></el-icon>
            <span>相册</span>
          </button>

          <button type="button" class="cam-shutter" :disabled="state !== 'ready' || capturing" @click="shoot">
            <span class="cam-shutter-inner" :class="{ firing: capturing }" />
          </button>

          <button
            type="button"
            class="cam-side"
            :disabled="cameras.length < 2"
            @click="switchCamera"
          >
            <el-icon :size="24"><Refresh /></el-icon>
            <span>翻转</span>
          </button>
        </div>
      </div>
    </transition>
  </teleport>
</template>

<script setup>
/**
 * 应用内相机。
 *
 * 为什么不用系统相机（Capacitor Camera 的 source='Camera'）：
 *   系统相机是独立 App，画面和按钮都由它控制，**无法在左下角加「进相册」按钮**。
 *   要自定义 UI 只有一个办法：在 WebView 里用 getUserMedia 自己出画面。
 *
 * 权限：
 *   Capacitor 的 BridgeWebChromeClient.onPermissionRequest 会处理
 *   VIDEO_CAPTURE → 申请 android.permission.CAMERA。
 *   所以 AndroidManifest 里**必须声明 CAMERA**（见该文件注释）。
 *
 * 降级：
 *   getUserMedia 不可用 / 用户拒绝 / 没有摄像头 → 触发 fallback，
 *   由父组件切回系统相机插件，保证功能不中断。
 */
import { onBeforeUnmount, ref, watch } from 'vue'
import { Close, Picture, Refresh, Loading, WarningFilled, Sunny } from '@element-plus/icons-vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  /** environment=后置（默认） user=前置 */
  facing: { type: String, default: 'environment' },
  hint: { type: String, default: '把叶片放进取景框内' }
})

const emit = defineEmits(['update:visible', 'capture', 'gallery', 'fallback', 'close'])

const videoRef = ref(null)
const state = ref('opening')   // opening | ready | error
const errorMsg = ref('')
const capturing = ref(false)
const facingMode = ref(props.facing)
const cameras = ref([])
const flashOn = ref(false)

let stream = null

function pickFacing() {
  facingMode.value = facingMode.value === 'environment' ? 'user' : 'environment'
}

async function open() {
  state.value = 'opening'
  errorMsg.value = ''
  await stopStream()   // 先释放旧的
  try {
    if (!navigator.mediaDevices?.getUserMedia) {
      throw new Error('当前环境不支持摄像头')
    }
    stream = await navigator.mediaDevices.getUserMedia({
      video: {
        facingMode: { ideal: facingMode.value },
        width: { ideal: 1920 },
        height: { ideal: 1080 }
      },
      audio: false
    })
    if (videoRef.value) {
      videoRef.value.srcObject = stream
      await videoRef.value.play().catch(() => {})
    }
    // 列出可用摄像头，用于决定「翻转」是否可点
    try {
      const devs = await navigator.mediaDevices.enumerateDevices()
      cameras.value = devs.filter(d => d.kind === 'videoinput')
    } catch { cameras.value = [] }
    state.value = 'ready'
  } catch (e) {
    const m = String(e?.name || e?.message || e)
    if (/NotAllowed|Permission/i.test(m)) {
      errorMsg.value = '相机权限被拒绝，请在系统设置里允许'
    } else if (/NotFound|DevicesNotFound/i.test(m)) {
      errorMsg.value = '没有检测到摄像头'
    } else if (/NotReadable|TrackStart/i.test(m)) {
      errorMsg.value = '相机被其他程序占用'
    } else {
      errorMsg.value = '无法打开相机：' + m
    }
    state.value = 'error'
  }
}

/** 只释放摄像头，不改可见性（打开新流前调用） */
async function stopStream() {
  if (stream) {
    stream.getTracks().forEach(t => t.stop())
    stream = null
  }
  if (videoRef.value) videoRef.value.srcObject = null
}

/** 关闭整个相机界面并释放摄像头 */
async function dismiss() {
  await stopStream()
  emit('update:visible', false)
  emit('close')
}

/** 拍照：把当前视频帧画到 canvas，再转成 File */
async function shoot() {
  if (state.value !== 'ready' || capturing.value) return
  const v = videoRef.value
  if (!v || !v.videoWidth) return
  capturing.value = true
  // 给个短暂的快门反馈
  await new Promise(r => setTimeout(r, 90))
  try {
    const w = v.videoWidth
    const h = v.videoHeight
    const c = document.createElement('canvas')
    c.width = w
    c.height = h
    const g = c.getContext('2d')
    // 前置摄像头预览是镜像的，拍照要还原成真实方向，否则文字/方位会反
    if (facingMode.value === 'user') {
      g.translate(w, 0)
      g.scale(-1, 1)
    }
    g.drawImage(v, 0, 0, w, h)

    const blob = await new Promise(res => c.toBlob(res, 'image/jpeg', 0.94))
    if (!blob) throw new Error('生成图片失败')
    const file = new File([blob], `photo-${Date.now()}.jpg`, { type: 'image/jpeg' })
    await dismiss()
    emit('capture', file)
  } catch (e) {
    errorMsg.value = '拍照失败：' + String(e?.message || e)
    state.value = 'error'
  } finally {
    capturing.value = false
  }
}

async function switchCamera() {
  facingMode.value = facingMode.value === 'environment' ? 'user' : 'environment'
  await open()
}

function toggleFlash() {
  flashOn.value = !flashOn.value
  try {
    const track = stream?.getVideoTracks?.()[0]
    const caps = track?.getCapabilities?.()
    if (caps && 'torch' in caps) {
      track.applyConstraints({ advanced: [{ torch: flashOn.value }] })
    }
  } catch {
    /* 多数设备不支持 torch，静默忽略 */
  }
}

watch(
  () => props.visible,
  async (v) => {
    if (v) await open()
    else await stopStream()
  }
)

onBeforeUnmount(stopStream)

void pickFacing
</script>

<style scoped>
.cam-root {
  position: fixed;
  inset: 0;
  z-index: 4000;
  background: #000;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.cam-video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cam-video.mirrored {
  transform: scaleX(-1);
}

/* ---------- 取景框 ---------- */
.cam-frame {
  position: absolute;
  inset: 18% 10%;
  pointer-events: none;
}

.corner {
  position: absolute;
  width: 26px;
  height: 26px;
  border: 3px solid rgba(255, 255, 255, 0.85);
}
.corner.tl { top: 0; left: 0; border-right: none; border-bottom: none; border-radius: 6px 0 0 0; }
.corner.tr { top: 0; right: 0; border-left: none; border-bottom: none; border-radius: 0 6px 0 0; }
.corner.bl { bottom: 0; left: 0; border-right: none; border-top: none; border-radius: 0 0 0 6px; }
.corner.br { bottom: 0; right: 0; border-left: none; border-top: none; border-radius: 0 0 6px 0; }

/* ---------- 顶部 ---------- */
.cam-top {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  padding: calc(10px + env(safe-area-inset-top, 0px)) 12px 10px;
  display: flex;
  align-items: center;
  gap: 10px;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.55), transparent);
}

.cam-icon-btn {
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.35);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.cam-icon-btn.on {
  background: rgba(255, 214, 102, 0.9);
  color: #333;
}

.cam-hint {
  flex: 1;
  text-align: center;
  color: rgba(255, 255, 255, 0.92);
  font-size: 13px;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.6);
}

/* ---------- 状态 ---------- */
.cam-state {
  position: absolute;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  color: #fff;
  font-size: 14px;
  text-align: center;
  padding: 0 32px;
  background: rgba(0, 0, 0, 0.55);
  border-radius: 12px;
  padding: 20px 26px;
}

.cam-link {
  border: none;
  background: none;
  color: #7fd1a3;
  font-size: 13px;
  text-decoration: underline;
  cursor: pointer;
  margin-top: 4px;
}

/* ---------- 底部 ---------- */
.cam-bottom {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 18px 26px calc(22px + env(safe-area-inset-bottom, 0px));
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(0deg, rgba(0, 0, 0, 0.62), transparent);
}

.cam-side {
  width: 68px;
  border: none;
  background: none;
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  cursor: pointer;
  opacity: 0.95;
}

.cam-side:disabled {
  opacity: 0.35;
}

.cam-side:active {
  opacity: 0.6;
}

.cam-shutter {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  border: 4px solid rgba(255, 255, 255, 0.9);
  background: transparent;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.cam-shutter:disabled {
  opacity: 0.4;
}

.cam-shutter-inner {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: #fff;
  transition: transform 0.12s ease, background 0.12s ease;
}

.cam-shutter:active .cam-shutter-inner {
  transform: scale(0.9);
  background: #d9e8df;
}

.cam-shutter-inner.firing {
  background: #7fd1a3;
  transform: scale(0.86);
}

/* ---------- 过渡 ---------- */
.cam-enter-active,
.cam-leave-active {
  transition: opacity 0.22s ease;
}

.cam-enter-from,
.cam-leave-to {
  opacity: 0;
}
</style>
