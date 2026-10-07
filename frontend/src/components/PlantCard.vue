<template>
  <div class="plant-card" @click="$emit('click', plant)">
    <div class="plant-cover">
      <span class="plant-emoji">{{ plant.speciesEmoji || '🌿' }}</span>
    </div>

    <div class="plant-body">
      <div class="plant-head">
        <span class="plant-nickname">{{ plant.nickname || plant.speciesName }}</span>
        <el-tag :type="healthMeta.type" size="small" effect="plain">{{ healthMeta.label }}</el-tag>
      </div>

      <div class="plant-species">{{ plant.speciesName }}</div>

      <div class="plant-meta">
        <span v-if="plant.location" class="meta-item">
          <el-icon><Location /></el-icon>{{ plant.location }}
        </span>
        <span v-if="lightMeta" class="meta-item">
          <el-icon><Sunny /></el-icon>{{ lightMeta.label }}
        </span>
      </div>

      <div class="plant-footer">
        <template v-if="plant.nextWaterDueDate">
          <el-icon><Clock /></el-icon>
          <span>下次浇水 {{ formatRelative(plant.nextWaterDueDate) }}</span>
        </template>
        <template v-else>
          <span class="gt-text-secondary">尚未生成养护计划</span>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { formatRelative } from '@/utils/format'
import { HEALTH_STATUS, LIGHT_LEVELS } from '@/utils/constants'

const props = defineProps({
  plant: { type: Object, required: true }
})
defineEmits(['click'])

const healthMeta = computed(() => HEALTH_STATUS[props.plant.healthStatus] || HEALTH_STATUS.HEALTHY)
const lightMeta = computed(() => (props.plant.lightLevel ? LIGHT_LEVELS[props.plant.lightLevel] : null))
</script>

<style scoped>
.plant-card {
  background: #fff;
  border: 1px solid var(--gt-border);
  border-radius: var(--gt-radius);
  overflow: hidden;
  cursor: pointer;
  transition: all 0.18s ease;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.plant-card:hover {
  border-color: var(--gt-primary);
  box-shadow: var(--gt-shadow-hover);
  transform: translateY(-2px);
}

.plant-cover {
  height: 84px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #eef6f1 0%, #e2f0e8 100%);
}

.plant-emoji {
  font-size: 40px;
  line-height: 1;
}

.plant-body {
  padding: 12px;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.plant-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.plant-nickname {
  font-size: 15px;
  font-weight: 600;
}

.plant-species {
  font-size: 12px;
  color: var(--gt-text-secondary);
}

.plant-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 12px;
  color: var(--gt-text-regular);
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.plant-footer {
  margin-top: auto;
  padding-top: 8px;
  border-top: 1px dashed var(--gt-border);
  font-size: 12px;
  color: var(--gt-primary-dark);
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>
