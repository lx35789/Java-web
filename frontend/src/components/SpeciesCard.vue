<template>
  <div class="species-card" @click="$emit('click', species)">
    <div class="species-cover">
      <span class="species-emoji">{{ species.emoji || '🌿' }}</span>
      <el-tag
        v-if="species.toxicity === 'TOXIC'"
        class="toxicity-tag"
        size="small"
        type="danger"
        effect="dark"
      >
        有毒
      </el-tag>
    </div>

    <div class="species-body">
      <div class="species-name-row">
        <span class="species-name">{{ species.commonName }}</span>
        <el-tag size="small" effect="plain" :type="difficultyMeta.type">{{ difficultyMeta.label }}</el-tag>
      </div>
      <div class="species-sci">{{ species.commonNameEn }} · {{ species.scientificName }}</div>

      <div class="species-meta">
        <span class="meta-item">
          <el-icon><Sunny /></el-icon>{{ lightMeta.label }}
        </span>
        <span class="meta-item">
          <el-icon><Clock /></el-icon>{{ species.baseWaterIntervalDays }} 天浇水
        </span>
      </div>

      <div v-if="species.aliases && species.aliases.length" class="species-aliases">
        别名：{{ species.aliases.join('、') }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { DIFFICULTY, LIGHT_LEVELS } from '@/utils/constants'

const props = defineProps({
  species: { type: Object, required: true }
})
defineEmits(['click'])

const DIFFICULTY_TYPE = { EASY: 'success', MEDIUM: 'warning', HARD: 'danger' }
const difficultyMeta = computed(() => ({
  ...(DIFFICULTY[props.species.difficulty] || DIFFICULTY.MEDIUM),
  type: DIFFICULTY_TYPE[props.species.difficulty] || 'info'
}))
const lightMeta = computed(() => LIGHT_LEVELS[props.species.lightRequirement] || { label: '未知' })
</script>

<style scoped>
.species-card {
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

.species-card:hover {
  border-color: var(--gt-primary);
  box-shadow: var(--gt-shadow-hover);
  transform: translateY(-2px);
}

.species-cover {
  position: relative;
  height: 96px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #eef6f1 0%, #e2f0e8 100%);
}

.species-emoji {
  font-size: 44px;
  line-height: 1;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.08));
}

.toxicity-tag {
  position: absolute;
  top: 8px;
  right: 8px;
}

.species-body {
  padding: 12px;
  flex: 1;
  display: flex;
  flex-direction: column;
}

.species-name-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.species-name {
  font-size: 15px;
  font-weight: 600;
}

.species-sci {
  font-size: 11px;
  color: var(--gt-text-secondary);
  margin-top: 3px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.species-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 10px;
  font-size: 12px;
  color: var(--gt-text-regular);
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.species-aliases {
  margin-top: 8px;
  font-size: 11px;
  color: var(--gt-text-secondary);
}
</style>
