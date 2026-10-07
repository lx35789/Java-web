<template>
  <div class="evidence-card" :class="`rank-${rank}`">
    <div class="evidence-head">
      <div class="gt-flex-center gt-gap-8">
        <el-tag v-if="rank === 1" type="danger" effect="dark" size="small">最可能</el-tag>
        <el-tag v-else type="info" effect="plain" size="small">次可能</el-tag>
        <span class="cause-name">{{ candidate.nameCn }}</span>
        <el-tag
          v-if="candidate.category"
          size="small"
          effect="plain"
          :style="{ color: categoryMeta.color, borderColor: categoryMeta.color }"
        >
          {{ categoryMeta.label }}
        </el-tag>
        <el-tag v-if="candidate.urgency" :type="urgencyMeta.type" size="small" effect="plain">
          {{ urgencyMeta.label }}
        </el-tag>
      </div>
      <div v-if="candidate.confidence > 0" class="evidence-conf">
        <span class="conf-num">{{ toPercent(candidate.confidence, 0) }}</span>
        <span class="conf-label">置信度</span>
      </div>
    </div>

    <div v-if="candidate.evidence && candidate.evidence.length" class="evidence-section">
      <div class="section-label">
        <el-icon><Search /></el-icon>判断依据
      </div>
      <ul class="evidence-list">
        <li v-for="(e, i) in candidate.evidence" :key="i">{{ e }}</li>
      </ul>
    </div>

    <div v-if="candidate.actionPlan && candidate.actionPlan.length" class="evidence-section">
      <div class="section-label">
        <el-icon><FirstAidKit /></el-icon>建议这样做
      </div>
      <ol class="action-list">
        <li v-for="(a, i) in candidate.actionPlan" :key="i">{{ a }}</li>
      </ol>
    </div>

    <div v-if="candidate.hitRuleIds && candidate.hitRuleIds.length" class="rule-ref">
      命中规则 #{{ candidate.hitRuleIds.join('、#') }}
      <span class="gt-text-secondary">（可在管理端「症状规则」中查看与调权）</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { toPercent } from '@/utils/format'
import { CAUSE_CATEGORIES, URGENCY } from '@/utils/constants'

const props = defineProps({
  candidate: { type: Object, required: true },
  rank: { type: Number, default: 1 }
})

const categoryMeta = computed(
  () => CAUSE_CATEGORIES[props.candidate.category] || { label: '未分类', color: '#909399' }
)
const urgencyMeta = computed(() => URGENCY[props.candidate.urgency] || URGENCY.MEDIUM)
</script>

<style scoped>
.evidence-card {
  background: #fff;
  border: 1px solid var(--gt-border);
  border-radius: var(--gt-radius);
  padding: 14px 16px;
}

.evidence-card.rank-1 {
  border-color: var(--gt-primary);
  box-shadow: 0 2px 10px rgba(47, 125, 93, 0.08);
}

.evidence-card + .evidence-card {
  margin-top: 12px;
}

.evidence-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.cause-name {
  font-size: 16px;
  font-weight: 600;
}

.evidence-conf {
  text-align: right;
  line-height: 1.1;
}

.conf-num {
  font-size: 19px;
  font-weight: 700;
  color: var(--gt-primary-dark);
  font-variant-numeric: tabular-nums;
}

.conf-label {
  font-size: 11px;
  color: var(--gt-text-secondary);
  margin-left: 4px;
}

.evidence-section {
  margin-top: 14px;
}

.section-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--gt-text-regular);
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.evidence-list,
.action-list {
  margin: 0;
  padding-left: 20px;
  font-size: 13px;
  line-height: 1.85;
  color: var(--gt-text-regular);
}

.evidence-list li::marker {
  color: var(--gt-primary);
}

.action-list li::marker {
  color: var(--gt-accent);
  font-weight: 600;
}

.rule-ref {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed var(--gt-border);
  font-size: 12px;
  color: var(--gt-text-secondary);
}
</style>
