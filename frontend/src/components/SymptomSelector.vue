<template>
  <div class="symptom-selector">
    <div class="selector-block">
      <div class="block-title">
        这盆植物怎么了？<span class="required">可多选</span>
      </div>
      <div class="symptom-grid">
        <div
          v-for="s in SYMPTOMS"
          :key="s.code"
          class="symptom-item"
          :class="{ active: isSelected(s.code) }"
          @click="toggleSymptom(s.code)"
        >
          <div class="symptom-check">
            <el-icon v-if="isSelected(s.code)"><CircleCheckFilled /></el-icon>
            <el-icon v-else><CirclePlus /></el-icon>
          </div>
          <div class="symptom-text">
            <div class="symptom-label">{{ s.label }}</div>
            <div class="symptom-hint">{{ s.hint }}</div>
          </div>
          <el-tag v-if="s.urgencyHint === 'HIGH'" size="small" type="danger" effect="plain">紧急</el-tag>
        </div>
      </div>
    </div>

    <!-- ★ 关键追问：叶片位置。这一项能把候选病因从 10 个收缩到 2–3 个 -->
    <div v-if="needLeafPosition" class="selector-block follow-up">
      <div class="block-title">
        <el-icon class="warn-icon"><WarningFilled /></el-icon>
        黄叶是从哪里开始的？<span class="required">必选 · 直接决定判断结果</span>
      </div>
      <el-radio-group
        :model-value="context.leafPosition"
        class="position-group"
        @update:model-value="(v) => patchContext({ leafPosition: v })"
      >
        <div v-for="p in LEAF_POSITIONS" :key="p.value" class="position-item">
          <el-radio :value="p.value" size="large">
            <div class="position-text">
              <span class="position-label">{{ p.label }}</span>
              <span class="position-desc">{{ p.desc }}</span>
            </div>
          </el-radio>
        </div>
      </el-radio-group>
      <div class="hint-line">
        选「说不清」时系统不会硬下结论，而是给出置信度较低的提示并引导你补充信息。
      </div>
    </div>

    <!-- 可选补强特征 -->
    <div v-if="isSelected('YELLOW_LEAF')" class="selector-block">
      <div class="block-title">叶脉是什么状态？<span class="optional">可选 · 用于区分缺铁</span></div>
      <el-radio-group
        :model-value="context.veinPattern"
        @update:model-value="(v) => patchContext({ veinPattern: v })"
      >
        <el-radio v-for="v in VEIN_PATTERNS" :key="v.value" :value="v.value">{{ v.label }}</el-radio>
      </el-radio-group>
    </div>

    <el-alert
      v-if="isSelected('SPOT')"
      type="info"
      :closable="false"
      show-icon
      class="gt-mt-16"
      title="斑点类症状会优先走病害模型"
      description="叶斑的形态（边缘、霉层、同心轮纹）在图像上是可区分的，如果你上传了照片，系统会先做病害识别；识别不确定时才回到症状判因。"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { SYMPTOMS, LEAF_POSITIONS, VEIN_PATTERNS } from '@/utils/constants'

const props = defineProps({
  /** 已选症状编码 */
  modelValue: { type: Array, default: () => [] },
  /** 判因上下文（叶片位置、叶脉、线索） */
  context: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['update:modelValue', 'update:context'])

const isSelected = (code) => props.modelValue.includes(code)

const needLeafPosition = computed(() => {
  const s = SYMPTOMS.find((i) => i.code === 'YELLOW_LEAF')
  return s && isSelected('YELLOW_LEAF') && s.leafPositionRequired
})

function toggleSymptom(code) {
  const next = isSelected(code) ? props.modelValue.filter((c) => c !== code) : [...props.modelValue, code]
  emit('update:modelValue', next)

  // 取消黄叶后清掉只对它有意义的两项
  if (code === 'YELLOW_LEAF' && isSelected('YELLOW_LEAF')) {
    patchContext({ leafPosition: null, veinPattern: null })
  }
}

function patchContext(patch) {
  emit('update:context', { ...props.context, ...patch })
}
</script>

<style scoped>
.selector-block + .selector-block {
  margin-top: 20px;
}

.block-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.required {
  font-size: 12px;
  font-weight: 400;
  color: var(--gt-danger);
}

.optional {
  font-size: 12px;
  font-weight: 400;
  color: var(--gt-text-secondary);
}

.warn-icon {
  color: var(--gt-warning);
}

.symptom-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 10px;
}

.symptom-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--gt-border);
  border-radius: var(--gt-radius);
  cursor: pointer;
  transition: all 0.15s ease;
  background: #fff;
}

.symptom-item:hover {
  border-color: var(--gt-primary);
  background: #fafcfb;
}

.symptom-item.active {
  border-color: var(--gt-primary);
  background: var(--gt-primary-light);
}

.symptom-check {
  color: var(--gt-primary);
  display: flex;
  font-size: 17px;
  flex: 0 0 auto;
}

.symptom-text {
  flex: 1;
  min-width: 0;
}

.symptom-label {
  font-size: 14px;
  color: var(--gt-text);
  font-weight: 500;
}

.symptom-hint {
  font-size: 12px;
  color: var(--gt-text-secondary);
  margin-top: 2px;
}

.follow-up {
  border-left: 3px solid var(--gt-warning);
  padding-left: 14px;
}

.position-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.position-item {
  width: 100%;
}

.position-text {
  display: flex;
  flex-direction: column;
  line-height: 1.4;
  white-space: normal;
  padding: 2px 0;
}

.position-label {
  font-size: 14px;
}

.position-desc {
  font-size: 12px;
  color: var(--gt-text-secondary);
}

.hint-line {
  font-size: 12px;
  color: var(--gt-text-secondary);
  margin-top: 8px;
  line-height: 1.6;
}

:deep(.el-radio) {
  height: auto;
  align-items: flex-start;
  margin-right: 0;
}

:deep(.el-radio__label) {
  white-space: normal;
}
</style>
