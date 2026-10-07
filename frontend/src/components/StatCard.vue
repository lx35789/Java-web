<template>
  <div class="stat-card" :style="{ '--accent': color }">
    <div class="stat-icon">
      <el-icon :size="22"><component :is="icon" /></el-icon>
    </div>
    <div class="stat-body">
      <div class="stat-value">
        {{ value }}<span v-if="unit" class="stat-unit">{{ unit }}</span>
      </div>
      <div class="stat-label">{{ label }}</div>
      <div v-if="extra" class="stat-extra">{{ extra }}</div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  label: { type: String, required: true },
  value: { type: [String, Number], default: '—' },
  unit: { type: String, default: '' },
  icon: { type: String, default: 'DataLine' },
  color: { type: String, default: '#2F7D5D' },
  extra: { type: String, default: '' }
})
</script>

<style scoped>
.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  background: #fff;
  border-radius: var(--gt-radius);
  box-shadow: var(--gt-shadow);
  padding: 16px;
  transition: box-shadow 0.2s ease, transform 0.2s ease;
}

.stat-card:hover {
  box-shadow: var(--gt-shadow-hover);
  transform: translateY(-1px);
}

.stat-icon {
  width: 44px;
  height: 44px;
  flex: 0 0 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 12%, transparent);
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.1;
  color: var(--gt-text);
}

.stat-unit {
  font-size: 13px;
  font-weight: 400;
  color: var(--gt-text-secondary);
  margin-left: 2px;
}

.stat-label {
  font-size: 13px;
  color: var(--gt-text-secondary);
  margin-top: 2px;
}

.stat-extra {
  font-size: 12px;
  color: var(--gt-text-secondary);
  margin-top: 4px;
}
</style>
