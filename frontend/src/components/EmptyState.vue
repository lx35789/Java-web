<template>
  <div class="empty-state">
    <div class="empty-emoji">{{ emoji }}</div>
    <div class="empty-title">{{ title }}</div>
    <div v-if="desc" class="empty-desc">{{ desc }}</div>
    <div v-if="$slots.default" class="empty-action">
      <slot />
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, default: '这里还没有内容' },
  desc: { type: String, default: '' },
  emoji: { type: String, default: '🌱' }
})
</script>

<style scoped>
.empty-state {
  text-align: center;
  padding: 48px 16px;
  color: var(--gt-text-secondary);
}

.empty-emoji {
  font-size: 40px;
  line-height: 1;
  margin-bottom: 12px;
  opacity: 0.7;
}

.empty-title {
  font-size: 14px;
  color: var(--gt-text-regular);
  margin-bottom: 4px;
}

.empty-desc {
  font-size: 13px;
  margin-bottom: 12px;
}
</style>
