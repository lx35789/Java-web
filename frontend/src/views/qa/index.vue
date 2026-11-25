<template>
  <PageHeader
    title="AI 养护顾问"
    desc="基于这盆植物自己的档案、浇水计划和健康曲线回答，不是通用模板"
  >
    <template #actions>
      <el-button :icon="Plus" @click="newSession">新对话</el-button>
    </template>
  </PageHeader>

  <el-row :gutter="12">
    <!-- ==================== 会话列表 ==================== -->
    <el-col :xs="24" :md="7" class="session-col">
      <div class="gt-card session-card">
        <h3 class="gt-section-title">历史对话</h3>
        <div v-if="sessions.length" class="session-list">
          <div
            v-for="s in sessions"
            :key="s.id"
            class="session-item"
            :class="{ active: s.id === activeSessionId }"
            @click="openSession(s)"
          >
            <div class="session-title">{{ s.title || '新对话' }}</div>
            <div class="session-meta">
              {{ plantLabel(s.userPlantId) }}
              <span v-if="plantLabel(s.userPlantId)">·</span>
              {{ s.messageCount || 0 }} 条 · {{ s.updatedAt || '' }}
            </div>
          </div>
        </div>
        <EmptyState v-else emoji="💬" title="还没有对话" desc="从「继续追问」进来，或直接在右侧提问" />

        <el-divider />
        <div class="gt-text-secondary gt-text-small">
          <div class="tip-line"><el-icon><Document /></el-icon>先读植物档案与养护记录</div>
          <div class="tip-line"><el-icon><ChatDotRound /></el-icon>再由千问生成建议</div>
          <div class="tip-line"><el-icon><Lock /></el-icon>只聊园艺，数据不出本机配置</div>
        </div>
      </div>
    </el-col>

    <!-- ==================== 对话区 ==================== -->
    <el-col :xs="24" :md="17">
      <div class="gt-card chat-card">
        <div class="chat-head">
          <div class="gt-flex-center gt-gap-8 chat-head-main">
            <!-- 手机上会话列表在抽屉里，这两个按钮只在窄屏出现 -->
            <el-button
              class="mobile-only chat-head-btn"
              text
              :icon="Clock"
              title="历史对话"
              @click="historyVisible = true"
            />
            <span class="chat-title">{{ activeSession?.title || '新对话' }}</span>
            <el-tag v-if="plantDetail" size="small" effect="plain" :type="healthTagType">
              {{ healthText }}
            </el-tag>
          </div>
          <div class="gt-flex-center gt-gap-8">
            <el-button
              class="mobile-only chat-head-btn"
              text
              :icon="Plus"
              title="新对话"
              @click="newSession"
            />
            <el-select
              v-model="userPlantId"
              placeholder="关联我的植物"
              clearable
              size="small"
              class="plant-select"
              @change="onPlantChange"
            >
              <el-option
                v-for="p in plants"
                :key="p.id"
                :label="`${p.speciesEmoji || '🌿'} ${p.nickname}`"
                :value="p.id"
              />
            </el-select>
          </div>
        </div>

        <!-- 个性化依据：植物档案 + 诊断起点 -->
        <div v-if="plantDetail || contextText" class="context-bar">
          <div v-if="plantDetail" class="ctx-item">
            <span class="ctx-emoji">{{ plantDetail.speciesEmoji || '🌿' }}</span>
            <div class="ctx-main">
              <div class="ctx-title">
                {{ plantDetail.nickname }}
                <span
                  v-if="plantDetail.speciesName && plantDetail.speciesName !== plantDetail.nickname"
                  class="gt-text-secondary gt-text-small"
                  >{{ plantDetail.speciesName }}</span
                >
              </div>
              <div class="ctx-desc">
                上次浇水 {{ plantDetail.lastWateredAt || '暂无记录' }}
                <template v-if="plantDetail.nextWaterDueDate"> · 下次 {{ plantDetail.nextWaterDueDate }}</template>
                <template v-if="waterInterval"> · 每 {{ waterInterval }} 天</template>
              </div>
            </div>
          </div>
          <div v-if="contextText" class="ctx-item ctx-diagnosis">
            <el-icon class="ctx-icon"><FirstAidKit /></el-icon>
            <div class="ctx-main">
              <div class="ctx-title">本次从一次诊断开始{{ contextTitle ? `：${contextTitle}` : '' }}</div>
              <div class="ctx-desc clamp-2">{{ contextText }}</div>
            </div>
          </div>
        </div>

        <div ref="scrollRef" v-loading="loading" class="chat-body">
          <div v-if="!messages.length" class="chat-empty">
            <div class="chat-empty-emoji">🌿</div>
            <div class="chat-empty-title">我是植语，先说说它现在怎么了</div>
            <div class="chat-empty-desc">{{ greeting }}</div>
            <div class="chat-examples">
              <el-tag
                v-for="q in examples"
                :key="q"
                class="example-tag"
                effect="plain"
                @click="askExample(q)"
              >
                {{ q }}
              </el-tag>
            </div>
            <div class="gt-text-secondary gt-text-small chat-empty-tip">
              回答由通义千问生成，会结合这盆植物的浇水与健康记录；涉及用药请以说明书为准。
            </div>
          </div>

          <ChatMessage
            v-for="m in messages"
            :key="m.id"
            :message="m"
            :class="{ 'is-streaming': m.streaming }"
            @feedback="(v) => onFeedback(m, v)"
          />

          <div v-if="thinking && !streamingId" class="thinking">
            <el-avatar :size="30" class="bot-avatar">🌿</el-avatar>
            <div class="thinking-bubble">
              <span class="dot" /><span class="dot" /><span class="dot" />
              <span class="thinking-text">正在读它的档案…</span>
            </div>
          </div>
        </div>

        <div class="chat-input">
          <el-input
            v-model="input"
            type="textarea"
            :rows="isNarrow ? 1 : 2"
            resize="none"
            :disabled="streaming"
            :placeholder="inputPlaceholder"
            @keydown.ctrl.enter.prevent="send()"
          />
          <el-button v-if="streaming" type="danger" plain @click="stop">停止</el-button>
          <el-button v-else type="primary" :disabled="!input.trim()" @click="send()">发送</el-button>
        </div>
      </div>
    </el-col>
  </el-row>

  <!-- 手机端：历史对话从左侧抽屉出，正文才有整屏高度 -->
  <el-drawer
    v-model="historyVisible"
    direction="ltr"
    size="76%"
    title="历史对话"
    class="history-drawer"
  >
    <el-button type="primary" plain :icon="Plus" class="history-new" @click="newSession">
      新对话
    </el-button>
    <div v-if="sessions.length" class="session-list">
      <div
        v-for="s in sessions"
        :key="s.id"
        class="session-item"
        :class="{ active: s.id === activeSessionId }"
        @click="openSession(s)"
      >
        <div class="session-title">{{ s.title || '新对话' }}</div>
        <div class="session-meta">
          {{ plantLabel(s.userPlantId) }}
          <span v-if="plantLabel(s.userPlantId)">·</span>
          {{ s.messageCount || 0 }} 条 · {{ s.updatedAt || '' }}
        </div>
      </div>
    </div>
    <div v-else class="gt-text-secondary gt-text-small history-empty">还没有对话</div>
  </el-drawer>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Document, ChatDotRound, Lock, FirstAidKit, Clock } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import ChatMessage from '@/components/ChatMessage.vue'
import EmptyState from '@/components/EmptyState.vue'
import {
  getQaSessions,
  getQaSession,
  createQaSession,
  updateQaSession,
  feedbackQaMessage,
  streamQaMessage
} from '@/api/qa'
import { getMyPlants, getMyPlantDetail } from '@/api/myPlant'

const route = useRoute()
const router = useRouter()

const sessions = ref([])
const messages = ref([])
const plants = ref([])
const plantDetail = ref(null)
const activeSessionId = ref(null)
const userPlantId = ref(null)
/** 手机端历史会话抽屉（窄屏时左栏是隐藏的） */
const historyVisible = ref(false)
/** 窄屏标记：输入框提示语里的「Ctrl + Enter」在手机上没意义 */
const narrowQuery = window.matchMedia('(max-width: 768px)')
// 初始值同步取一次，等 onMounted 再设会让首屏按桌面配置渲染（输入框会高一行）
const isNarrow = ref(narrowQuery.matches)
const syncNarrow = () => {
  isNarrow.value = narrowQuery.matches
}
const inputPlaceholder = computed(() =>
  isNarrow.value ? '说说它现在的情况…' : '描述你的问题，Ctrl + Enter 发送'
)
const input = ref('')
const loading = ref(false)
const thinking = ref(false)
const streaming = ref(false)
const streamingId = ref(null)
const scrollRef = ref(null)

/** 从诊断页带过来的上下文（一次性，发第一条消息时交给后端存进会话） */
const pendingContext = ref(null)
let controller = null

const activeSession = computed(() => sessions.value.find((s) => s.id === activeSessionId.value))
const contextText = computed(
  () => activeSession.value?.diagnosisContext || pendingContext.value?.text || ''
)
const contextTitle = computed(() => pendingContext.value?.title || '')

const healthText = computed(() => {
  const map = { HEALTHY: '健康', WATCH: '需关注', SICK: '有病害', CRITICAL: '情况较差', DISEASED: '有病害' }
  return map[plantDetail.value?.healthStatus] || '状态未知'
})
const healthTagType = computed(() => {
  const s = plantDetail.value?.healthStatus
  if (s === 'HEALTHY') return 'success'
  if (s === 'WATCH') return 'warning'
  if (s === 'SICK' || s === 'CRITICAL' || s === 'DISEASED') return 'danger'
  return 'info'
})
const waterInterval = computed(() => {
  const plans = plantDetail.value?.carePlans || []
  const water = plans.find((p) => p.planType === 'WATER')
  return water?.effectiveIntervalDays || null
})

const greeting = computed(() => {
  if (plantDetail.value) {
    const p = plantDetail.value
    const parts = [`${p.speciesName}`, healthText.value]
    if (p.lastWateredAt) parts.push(`上次浇水 ${p.lastWateredAt.slice(5, 16)}`)
    if (waterInterval.value) parts.push(`现在按 ${waterInterval.value} 天一次浇`)
    return `我已经看过「${p.nickname}」的档案：${parts.join('，')}。想问哪方面？`
  }
  if (contextText.value) return '这次的问题是从刚才的诊断带过来的，我会接着诊断结论往下说。'
  return '先在右上角选一盆植物，回答会更贴合它的情况；不选也行，我会按室内观叶植物通用原则说。'
})

const examples = computed(() => {
  if (contextText.value) {
    return ['那我现在最该先做哪一步？', '按这个处理，几天能看出好转？', '需要先把它隔离吗？']
  }
  if (plantDetail.value) {
    return ['它最近状态怎么样？', '下一次该什么时候浇水？', '现在适合施肥或换盆吗？']
  }
  return ['绿萝叶子发黄怎么办', '冬天要不要给虎皮兰浇水', '叶尖为什么总是焦']
})

/* ==================== 数据加载 ==================== */

async function loadSessions() {
  sessions.value = (await getQaSessions()) || []
}

async function loadPlant(id) {
  if (!id) {
    plantDetail.value = null
    return
  }
  try {
    plantDetail.value = await getMyPlantDetail(id)
  } catch {
    plantDetail.value = null
  }
}

function plantLabel(id) {
  if (!id) return ''
  const p = plants.value.find((x) => x.id === id)
  return p ? `${p.speciesEmoji || '🌿'} ${p.nickname}` : ''
}

async function openSession(s) {
  historyVisible.value = false
  if (streaming.value) {
    ElMessage.warning('回答还没结束，先停止再切换')
    return
  }
  activeSessionId.value = s.id
  loading.value = true
  try {
    const data = await getQaSession(s.id)
    messages.value = data.messages || []
    if (data.session?.userPlantId) {
      userPlantId.value = data.session.userPlantId
      loadPlant(userPlantId.value)
    } else {
      userPlantId.value = null
      plantDetail.value = null
    }
    pendingContext.value = null
    scrollToBottom()
  } finally {
    loading.value = false
  }
}

function newSession() {
  historyVisible.value = false
  if (streaming.value) stop()
  activeSessionId.value = null
  messages.value = []
  input.value = ''
  pendingContext.value = null
  if (route.query.from) router.replace({ path: '/qa' })
}

async function onPlantChange(id) {
  await loadPlant(id)
  if (activeSessionId.value) {
    try {
      await updateQaSession(activeSessionId.value, { userPlantId: id })
      const s = sessions.value.find((x) => x.id === activeSessionId.value)
      if (s) s.userPlantId = id
    } catch {
      /* 静默：下次提问时后端还会再记一次 */
    }
  }
}

/* ==================== 提问 ==================== */

function askExample(q) {
  input.value = q
  send()
}

async function send() {
  const content = input.value.trim()
  if (!content || streaming.value) return

  let sessionId = activeSessionId.value
  if (!sessionId) {
    const s = await createQaSession({
      content,
      userPlantId: userPlantId.value,
      diagnosisContext: pendingContext.value?.text
    })
    sessionId = s.id
    activeSessionId.value = s.id
    sessions.value.unshift({ ...s, messageCount: 0, updatedAt: s.createdAt })
  }

  messages.value.push({
    id: `local_u_${Date.now()}`,
    role: 'USER',
    content,
    createdAt: new Date().toISOString()
  })
  input.value = ''

  const reply = {
    id: `local_a_${Date.now()}`,
    role: 'ASSISTANT',
    content: '',
    streaming: true
  }
  messages.value.push(reply)
  streamingId.value = reply.id
  thinking.value = true
  streaming.value = true
  scrollToBottom()

  controller = new AbortController()
  let finished = false

  try {
    await streamQaMessage(
      sessionId,
      {
        content,
        userPlantId: userPlantId.value,
        diagnosisContext: pendingContext.value?.text
      },
      {
        signal: controller.signal,
        onMeta: (meta) => {
          if (meta.title) {
            const s = sessions.value.find((x) => x.id === sessionId)
            if (s) s.title = meta.title
          }
        },
        onDelta: (text) => {
          reply.content += text
          if (thinking.value) thinking.value = false
          scrollToBottom()
        },
        onDone: ({ assistantMessage }) => {
          finished = true
          Object.assign(reply, assistantMessage || {}, { streaming: false })
          streamingId.value = null
          streaming.value = false
          thinking.value = false
          pendingContext.value = null
          scrollToBottom()
          refreshSessionsQuietly()
        },
        onError: (msg) => {
          finished = true
          reply.error = true
          reply.content = reply.content || `⚠️ ${msg}`
          reply.streaming = false
          streamingId.value = null
          streaming.value = false
          thinking.value = false
        }
      }
    )
  } catch (e) {
    if (e?.name === 'AbortError') {
      reply.content += '\n（已停止）'
    } else {
      reply.error = true
      reply.content = reply.content || `⚠️ ${e.message || '连接失败，请稍后再试'}`
      ElMessage.error(e.message || '连接失败，请稍后再试')
    }
  } finally {
    reply.streaming = false
    streamingId.value = null
    streaming.value = false
    thinking.value = false
    controller = null
    if (!finished) refreshSessionsQuietly()
    scrollToBottom()
  }
}

function stop() {
  controller?.abort()
}

async function refreshSessionsQuietly() {
  try {
    const list = (await getQaSessions()) || []
    sessions.value = list
  } catch {
    /* 列表刷新失败不影响当前对话 */
  }
}

async function onFeedback(message, value) {
  if (String(message.id).startsWith('local')) return
  await feedbackQaMessage(message.id, value)
  message.feedback = value
  ElMessage.success(value === 1 ? '感谢反馈' : '已记录，会继续改进')
}

function scrollToBottom() {
  nextTick(() => {
    const el = scrollRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

/* ==================== 生命周期 ==================== */

onMounted(async () => {
  syncNarrow()
  narrowQuery.addEventListener('change', syncNarrow)
  plants.value = (await getMyPlants()) || []

  // 诊断页跳过来时带的一次性上下文
  const raw = sessionStorage.getItem('gt:ai-context')
  if (raw) {
    try {
      pendingContext.value = JSON.parse(raw)
    } catch {
      pendingContext.value = null
    }
    sessionStorage.removeItem('gt:ai-context')
  }

  const queryPlantId = Number(route.query.plantId)
  if (queryPlantId) userPlantId.value = queryPlantId

  if (route.query.session) {
    const target = { id: Number(route.query.session) }
    await loadSessions()
    await openSession(target)
    return
  }

  if (pendingContext.value) {
    // 从诊断进来：开一个干净的对话，上下文已就位
    await loadSessions()
    activeSessionId.value = null
    messages.value = []
    if (pendingContext.value.plantId) {
      userPlantId.value = pendingContext.value.plantId
    }
    await loadPlant(userPlantId.value)
    return
  }

  await loadSessions()
  if (sessions.value.length) {
    await openSession(sessions.value[0])
  } else if (userPlantId.value) {
    await loadPlant(userPlantId.value)
  }
})

onBeforeUnmount(() => {
  narrowQuery.removeEventListener('change', syncNarrow)
  controller?.abort()
})
</script>

<style scoped>
.session-card,
.chat-card {
  height: calc(100vh - 200px);
  min-height: 540px;
  display: flex;
  flex-direction: column;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  margin: 0 -4px;
}

.session-item {
  padding: 10px 12px;
  border-radius: var(--gt-radius);
  cursor: pointer;
  transition: background 0.15s ease;
  margin-bottom: 4px;
}

.session-item:hover {
  background: #f5f7fa;
}

.session-item.active {
  background: var(--gt-primary-light);
}

.session-title {
  font-size: 13.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-meta {
  font-size: 11.5px;
  color: var(--gt-text-secondary);
  margin-top: 3px;
}

.tip-line {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 3px 0;
  line-height: 1.6;
}

.chat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--gt-border);
}

.chat-title {
  font-size: 15px;
  font-weight: 600;
}

/* ---- 个性化依据条 ---- */
.context-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 0 0;
}

.ctx-item {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  flex: 1 1 240px;
  min-width: 0;
  padding: 8px 10px;
  background: var(--gt-primary-light);
  border-radius: var(--gt-radius);
}

.ctx-item.ctx-diagnosis {
  background: #fff8e8;
}

.ctx-emoji {
  font-size: 18px;
  line-height: 1.2;
}

.ctx-icon {
  color: #d98e04;
  margin-top: 2px;
}

.ctx-main {
  min-width: 0;
  flex: 1;
}

.ctx-title {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--gt-text);
}

.ctx-desc {
  font-size: 11.5px;
  color: var(--gt-text-secondary);
  margin-top: 2px;
  line-height: 1.55;
}

.clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 0;
}

.chat-empty {
  text-align: center;
  padding: 40px 20px 20px;
}

.chat-empty-emoji {
  font-size: 42px;
}

.chat-empty-title {
  font-size: 16px;
  font-weight: 600;
  margin-top: 10px;
}

.chat-empty-desc {
  font-size: 13px;
  color: var(--gt-text-secondary);
  margin-top: 6px;
  line-height: 1.7;
  max-width: 520px;
  margin-left: auto;
  margin-right: auto;
}

.chat-examples {
  display: flex;
  gap: 8px;
  justify-content: center;
  flex-wrap: wrap;
  margin-top: 18px;
}

.example-tag {
  cursor: pointer;
}

.chat-empty-tip {
  margin-top: 18px;
  opacity: 0.75;
}

.chat-input {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  padding-top: 12px;
  border-top: 1px solid var(--gt-border);
}

.thinking {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
}

.bot-avatar {
  background: var(--gt-primary-light);
  font-size: 15px;
  flex: 0 0 auto;
}

.thinking-bubble {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 12px 16px;
  background: #fff;
  border: 1px solid var(--gt-border);
  border-radius: 10px;
  border-top-left-radius: 2px;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--gt-primary);
  animation: blink 1.4s infinite ease-in-out both;
}

.dot:nth-child(2) {
  animation-delay: 0.2s;
}
.dot:nth-child(3) {
  animation-delay: 0.4s;
}

.thinking-text {
  font-size: 12px;
  color: var(--gt-text-secondary);
  margin-left: 4px;
}

/* 流式输出时在气泡末尾加一个光标 */
.is-streaming :deep(.bubble::after) {
  content: '▍';
  color: var(--gt-primary);
  animation: caret 1s steps(2) infinite;
}

@keyframes blink {
  0%,
  80%,
  100% {
    opacity: 0.25;
  }
  40% {
    opacity: 1;
  }
}

@keyframes caret {
  0%,
  100% {
    opacity: 0;
  }
  50% {
    opacity: 1;
  }
}

/* 窄屏才出现的元素，桌面上不占位（必须写在下面的媒体查询之前，
   否则同优先级下会被后面的 display:none 覆盖掉） */
.mobile-only {
  display: none;
}
.plant-select {
  width: 200px;
}

.history-new {
  width: 100%;
  margin-bottom: 10px;
}
.history-empty {
  padding: 12px 0;
}

@media (max-width: 768px) {
  /* 手机上不要「半屏对话」：
     左栏（历史会话）和页头都收起来，聊天卡片直接贴在顶栏下方铺满整屏，
     输入框固定在底部并预留安全区，中间的消息区自己滚动。 */
  :deep(.page-head) {
    display: none;
  }
  .session-col {
    display: none;
  }
  .chat-card {
    position: fixed;
    top: var(--gt-header-height);
    right: 0;
    bottom: 0;
    left: 0;
    height: auto;
    min-height: 0;
    padding: 0 12px calc(8px + env(safe-area-inset-bottom));
    border-radius: 0;
    box-shadow: none;
  }
  .mobile-only {
    display: inline-flex;
  }
  .chat-head {
    padding: 8px 0;
    gap: 8px;
    flex-wrap: nowrap;
  }
  .chat-head-main {
    min-width: 0;
    flex: 1;
  }
  .chat-title {
    font-size: 14px;
    max-width: 38vw;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .chat-head-btn {
    margin: 0;
    padding: 4px 6px;
  }
  .plant-select {
    width: 112px;
    flex: 0 0 auto;
  }
  /* 依据条在手机上压缩：一行字够看懂，不抢消息区高度 */
  .context-bar {
    padding-top: 6px;
    gap: 6px;
  }
  .ctx-item {
    flex: 1 1 100%;
    padding: 6px 8px;
  }
  .ctx-desc {
    font-size: 11px;
  }
  .clamp-2 {
    -webkit-line-clamp: 1;
  }
  .chat-body {
    padding: 8px 0;
    overscroll-behavior: contain;
  }
  .chat-empty {
    padding: 20px 8px 12px;
  }
  .chat-empty-emoji {
    font-size: 34px;
  }
  .chat-input {
    padding-top: 8px;
  }
}

</style>
