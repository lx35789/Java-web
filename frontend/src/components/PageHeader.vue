<template>
  <div class="gt-page">
    <div class="gt-flex-between page-head">
      <div>
        <h2 class="gt-page-title">{{ title }}</h2>
        <p v-if="desc" class="gt-page-desc">{{ desc }}</p>
      </div>
      <div class="page-actions">
        <slot name="actions" />
      </div>
    </div>
    <slot />
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  desc: { type: String, default: '' }
})
</script>

<style scoped>
.page-head {
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
}

.page-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
