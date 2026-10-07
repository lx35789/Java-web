<template>
  <div v-if="citations && citations.length" class="citation-list">
    <span class="citation-label"><el-icon><Link /></el-icon>依据来源</span>
    <template v-for="(c, i) in citations" :key="i">
      <el-tag
        size="small"
        effect="plain"
        class="citation-tag"
        :style="{ color: meta(c).color, borderColor: meta(c).color }"
      >
        {{ c.title }}
        <span v-if="meta(c).license !== '自有'" class="citation-license">{{ meta(c).license }}</span>
      </el-tag>
    </template>
  </div>
</template>

<script setup>
import { KNOWLEDGE_SOURCE } from '@/utils/constants'

defineProps({
  citations: { type: Array, default: () => [] }
})

const meta = (c) =>
  KNOWLEDGE_SOURCE[c?.source] || { label: c?.source || '未知来源', license: '—', color: '#909399' }
</script>

<style scoped>
.citation-list {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--gt-border);
}

.citation-label {
  font-size: 12px;
  color: var(--gt-text-secondary);
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-right: 2px;
}

.citation-license {
  font-size: 10px;
  opacity: 0.75;
  margin-left: 4px;
}
</style>
