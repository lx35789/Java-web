<template>
  <div class="chat-message" :class="isUser ? 'is-user' : 'is-assistant'">
    <el-avatar v-if="!isUser" :size="30" class="bot-avatar">🌿</el-avatar>

    <div class="bubble-wrap">
      <!-- ★ 先展示检索到的资料，让用户感知到「回答是有依据的」 -->
      <el-collapse v-if="!isUser && chunks.length" class="retrieval">
        <el-collapse-item :name="1">
          <template #title>
            <span class="retrieval-title">
              <el-icon><Search /></el-icon>
              检索到 {{ chunks.length }} 条相关资料
            </span>
          </template>
          <div v-for="c in chunks" :key="c.chunkId" class="chunk-row">
            <span class="chunk-title">{{ c.title }}</span>
            <span class="chunk-score">相似度 {{ toPercent(c.score) }}</span>
          </div>
        </el-collapse-item>
      </el-collapse>

      <div class="bubble" v-html="rendered" />

      <div v-if="!isUser" class="bubble-footer">
        <CitationList :citations="message.citations || []" />
        <div class="feedback-row">
          <span class="gt-text-secondary gt-text-small">
            {{ message.model ? `${message.model} · ${formatLatency(message.latencyMs)}` : '' }}
          </span>
          <div class="feedback-btns">
            <el-button
              text
              size="small"
              :type="message.feedback === 1 ? 'success' : ''"
              @click="$emit('feedback', 1)"
            >
              <el-icon><Select /></el-icon>有用
            </el-button>
            <el-button
              text
              size="small"
              :type="message.feedback === -1 ? 'danger' : ''"
              @click="$emit('feedback', -1)"
            >
              <el-icon><CloseBold /></el-icon>没用
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { toPercent, formatLatency } from '@/utils/format'
import CitationList from './CitationList.vue'

const props = defineProps({
  message: { type: Object, required: true }
})
defineEmits(['feedback'])

const isUser = computed(() => props.message.role === 'USER')
const chunks = computed(() => props.message.retrievedChunks || [])

/**
 * 极简 Markdown 渲染：仅支持 **加粗** 与换行。
 * 先做 HTML 转义再替换，避免 XSS。正式接入时替换为 markdown-it。
 */
const rendered = computed(() => {
  const esc = String(props.message.content || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  return esc.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>').replace(/\n/g, '<br/>')
})
</script>

<style scoped>
.chat-message {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
}

.chat-message.is-user {
  justify-content: flex-end;
}

.bot-avatar {
  background: var(--gt-primary-light);
  font-size: 15px;
  flex: 0 0 auto;
}

.bubble-wrap {
  max-width: 78%;
}

.is-user .bubble-wrap {
  max-width: 70%;
}

.bubble {
  padding: 12px 14px;
  border-radius: 10px;
  font-size: 14px;
  line-height: 1.85;
  word-break: break-word;
}

.is-assistant .bubble {
  background: #fff;
  border: 1px solid var(--gt-border);
  border-top-left-radius: 2px;
}

.is-user .bubble {
  background: var(--gt-primary);
  color: #fff;
  border-top-right-radius: 2px;
}

.retrieval {
  margin-bottom: 8px;
  background: #fafbfc;
  border: 1px solid var(--gt-border);
  border-radius: var(--gt-radius);
  padding: 0 12px;
}

.retrieval-title {
  font-size: 12px;
  color: var(--gt-text-secondary);
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.chunk-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
  padding: 3px 0;
  color: var(--gt-text-regular);
}

.chunk-score {
  color: var(--gt-text-secondary);
  font-variant-numeric: tabular-nums;
}

.bubble-footer {
  margin-top: 6px;
}

.feedback-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 6px;
  flex-wrap: wrap;
}

.feedback-btns {
  display: flex;
  gap: 4px;
}

:deep(.el-collapse),
:deep(.el-collapse-item__header),
:deep(.el-collapse-item__wrap) {
  border: none;
  background: transparent;
}

:deep(.el-collapse-item__header) {
  height: 34px;
  line-height: 34px;
}

:deep(.el-collapse-item__content) {
  padding-bottom: 8px;
}
</style>
