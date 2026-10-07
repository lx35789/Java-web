<template>
  <PageHeader title="AI 问答" desc="基于养护知识库检索后回答，每条结论都标注来源">
    <template #actions>
      <el-button :icon="Plus" @click="newSession">新对话</el-button>
    </template>
  </PageHeader>

  <el-row :gutter="12">
    <!-- ==================== 会话列表 ==================== -->
    <el-col :xs="24" :md="7">
      <div class="gt-card session-card">
        <h3 class="gt-section-title">历史对话</h3>
        <div v-if="sessions.length" class="session-list">
          <div
            v-for="s in sessions"
            :key="s.id"
            class="session-item"
            :class="{ active: s.id === activeSessionId }"
            @click="selectSession(s)"
          >
            <div class="session-title">{{ s.title }}</div>
            <div class="session-meta">
              {{ s.messageCount }} 条 · {{ formatRelative(s.updatedAt) }}
            </div>
          </div>
        </div>
        <EmptyState v-else emoji="💬" title="还没有对话" />

        <el-divider />
        <div class="gt-text-secondary gt-text-small">
          <div class="tip-line"><el-icon><Search /></el-icon>先从知识库检索相关资料</div>
          <div class="tip-line"><el-icon><Document /></el-icon>再把资料和问题交给大模型</div>
          <div class="tip-line"><el-icon><Link /></el-icon>回答里标注每条结论的来源</div>
        </div>
      </div>
    </el-col>

    <!-- ==================== 对话区 ==================== -->
    <el-col :xs="24" :md="17">
      <div class="gt-card chat-card">
        <div class="chat-head">
          <div class="gt-flex-center gt-gap-8">
            <span class="chat-title">{{ activeSession?.title || '新对话' }}</span>
          </div>
          <el-select
            v-model="userPlantId"
            placeholder="关联我的植物（可选）"
            clearable
            size="small"
            style="width: 190px"
          >
            <el-option
              v-for="p in plants"
              :key="p.id"
              :label="`${p.speciesEmoji} ${p.nickname}`"
              :value="p.id"
            />
          </el-select>
        </div>

        <div ref="scrollRef" v-loading="loading" class="chat-body">
          <div v-if="!messages.length" class="chat-empty">
            <div class="chat-empty-emoji">🌿</div>
            <div class="chat-empty-title">试着问点什么</div>
            <div class="chat-empty-desc">
              比如「绿萝叶子发黄怎么办」「冬天要不要给虎皮兰浇水」
            </div>
            <div class="chat-examples">
              <el-tag
                v-for="q in examples"
                :key="q"
                class="example-tag"
                effect="plain"
                @click="input = q"
              >
                {{ q }}
              </el-tag>
            </div>
          </div>

          <ChatMessage
            v-for="m in messages"
            :key="m.id"
            :message="m"
            @feedback="(v) => onFeedback(m, v)"
          />

          <div v-if="thinking" class="thinking">
            <el-avatar :size="30" class="bot-avatar">🌿</el-avatar>
            <div class="thinking-bubble">
              <span class="dot" /><span class="dot" /><span class="dot" />
              <span class="thinking-text">正在检索知识库…</span>
            </div>
          </div>
        </div>

        <div class="chat-input">
          <el-input
            v-model="input"
            type="textarea"
            :rows="2"
            resize="none"
            placeholder="描述你的问题，Ctrl + Enter 发送"
            @keydown.ctrl.enter.prevent="send"
          />
          <el-button type="primary" :loading="thinking" :disabled="!input.trim()" @click="send">
            发送
          </el-button>
        </div>
      </div>
    </el-col>
  </el-row>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Search, Document, Link } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import ChatMessage from '@/components/ChatMessage.vue'
import EmptyState from '@/components/EmptyState.vue'
import { getQaSessions, getQaMessages, sendQaMessage, feedbackQaMessage, createQaSession } from '@/api/qa'
import { getMyPlants } from '@/api/myPlant'
import { formatRelative } from '@/utils/format'

const sessions = ref([])
const messages = ref([])
const plants = ref([])
const activeSessionId = ref(null)
const userPlantId = ref(null)
const input = ref('')
const loading = ref(false)
const thinking = ref(false)
const scrollRef = ref(null)

const examples = ['绿萝叶子发黄怎么办', '冬天要不要给虎皮兰浇水', '叶尖为什么总是焦']

const activeSession = computed(() => sessions.value.find((s) => s.id === activeSessionId.value))

async function loadSessions() {
  sessions.value = (await getQaSessions()) || []
  if (sessions.value.length && !activeSessionId.value) {
    selectSession(sessions.value[0])
  }
}

async function selectSession(s) {
  activeSessionId.value = s.id
  loading.value = true
  try {
    messages.value = (await getQaMessages(s.id)) || []
    scrollToBottom()
  } finally {
    loading.value = false
  }
}

function newSession() {
  activeSessionId.value = null
  messages.value = []
  input.value = ''
}

async function send() {
  const content = input.value.trim()
  if (!content) return

  let sessionId = activeSessionId.value
  if (!sessionId) {
    const s = await createQaSession({ content, userPlantId: userPlantId.value })
    sessionId = s.id
    activeSessionId.value = s.id
    sessions.value.unshift(s)
  }

  // 乐观插入用户消息
  messages.value.push({
    id: `local_${Date.now()}`,
    role: 'USER',
    content,
    createdAt: new Date().toISOString()
  })
  input.value = ''
  thinking.value = true
  scrollToBottom()

  try {
    const res = await sendQaMessage(sessionId, { content, userPlantId: userPlantId.value })
    // 真实后端为 SSE 流式，这里替换为一次性追加
    messages.value.push(res.assistantMessage)
    scrollToBottom()
  } catch {
    /* 拦截器已提示 */
  } finally {
    thinking.value = false
  }
}

async function onFeedback(message, value) {
  if (String(message.id).startsWith('local')) return
  await feedbackQaMessage(message.id, value)
  message.feedback = value
  ElMessage.success(value === 1 ? '感谢反馈' : '已记录，我们会改进')
}

function scrollToBottom() {
  nextTick(() => {
    const el = scrollRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

onMounted(async () => {
  plants.value = (await getMyPlants()) || []
  loadSessions()
})
</script>

<style scoped>
.session-card,
.chat-card {
  height: calc(100vh - 200px);
  min-height: 520px;
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

.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 0;
}

.chat-empty {
  text-align: center;
  padding: 60px 20px;
}

.chat-empty-emoji {
  font-size: 44px;
}

.chat-empty-title {
  font-size: 16px;
  font-weight: 600;
  margin-top: 12px;
}

.chat-empty-desc {
  font-size: 13px;
  color: var(--gt-text-secondary);
  margin-top: 6px;
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

@media (max-width: 768px) {
  .session-card {
    height: auto;
    min-height: 0;
    margin-bottom: 12px;
  }
  .chat-card {
    height: calc(100vh - 220px);
  }
}
</style>
