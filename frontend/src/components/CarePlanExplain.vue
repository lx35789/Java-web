<template>
  <div class="gt-explain care-plan-explain">
    <div class="gt-explain-title">
      <el-icon><MagicStick /></el-icon>
      为什么是 {{ plan.effectiveIntervalDays }} 天？
    </div>

    <table class="calc-table">
      <tbody>
        <tr class="calc-base">
          <td class="calc-label">基准间隔</td>
          <td class="calc-coef">—</td>
          <td class="calc-value">{{ fmt(plan.baseIntervalDays) }} 天</td>
        </tr>
        <tr v-for="row in rows" :key="row.key">
          <td class="calc-label">{{ row.label }}</td>
          <td class="calc-coef">× {{ row.coef }}</td>
          <td class="calc-value">{{ fmt(row.running) }} 天</td>
        </tr>
      </tbody>
      <tfoot>
        <tr>
          <td class="calc-label">建议间隔</td>
          <td class="calc-coef"></td>
          <td class="calc-value result">{{ plan.effectiveIntervalDays }} 天</td>
        </tr>
      </tfoot>
    </table>

    <div v-if="plan.calculation" class="calc-formula">{{ plan.calculation }}</div>

    <div v-if="plan.llmExplain" class="calc-llm">
      <el-icon><ChatLineSquare /></el-icon>
      <span>{{ plan.llmExplain }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  plan: { type: Object, required: true }
})

/** 系数的展示顺序，与 docs/02 的公式保持一致 */
const ORDER = ['season', 'light', 'temp', 'humidity', 'pot', 'ac']
const FALLBACK_LABEL = {
  season: '季节系数',
  light: '光照系数',
  temp: '温度系数',
  humidity: '湿度系数',
  pot: '花盆系数',
  ac: '空调系数'
}

const fmt = (n) => (n === null || n === undefined ? '—' : Number(n).toFixed(1))

/** 逐项累乘，让用户看到每一步的数字变化 */
const rows = computed(() => {
  const detail = props.plan?.coefficientDetail || {}
  const labels = props.plan?.coefficientLabels || {}
  let running = Number(props.plan?.baseIntervalDays) || 0
  const out = []
  for (const key of ORDER) {
    const coef = detail[key]
    if (coef === undefined || coef === null) continue
    running = running * Number(coef)
    out.push({
      key,
      label: labels[key] || FALLBACK_LABEL[key] || key,
      coef: Number(coef),
      running
    })
  }
  return out
})
</script>

<style scoped>
.calc-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.calc-table td {
  padding: 4px 0;
  vertical-align: middle;
}

.calc-label {
  color: var(--gt-text-regular);
  width: 45%;
}

.calc-coef {
  color: var(--gt-text-secondary);
  font-family: ui-monospace, Menlo, Consolas, monospace;
  width: 22%;
  text-align: right;
  padding-right: 12px !important;
}

.calc-value {
  text-align: right;
  font-variant-numeric: tabular-nums;
  color: var(--gt-text);
  width: 33%;
}

.calc-base .calc-value {
  color: var(--gt-text-regular);
}

tfoot tr {
  border-top: 1px dashed rgba(47, 125, 93, 0.35);
}

tfoot td {
  padding-top: 8px !important;
}

.calc-value.result {
  font-size: 17px;
  font-weight: 700;
  color: var(--gt-primary-dark);
}

.calc-formula {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid rgba(47, 125, 93, 0.18);
  font-size: 12px;
  color: var(--gt-text-secondary);
  line-height: 1.6;
  word-break: break-all;
}

.calc-llm {
  margin-top: 10px;
  display: flex;
  gap: 8px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--gt-text-regular);
}

.calc-llm .el-icon {
  margin-top: 3px;
  flex: 0 0 auto;
  color: var(--gt-primary);
}
</style>
