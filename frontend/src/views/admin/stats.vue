<template>
  <PageHeader title="AI 用量统计" desc="模型调用、配额消耗，以及基于用户纠正算出的真实场景准确率">
    <template #actions>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </template>
  </PageHeader>

  <!-- ============ 配额警告 ============ -->
  <el-alert
    v-if="quotaPercent >= 80"
    type="warning"
    :closable="false"
    show-icon
    class="gt-mb-16"
    title="Pl@ntNet 免费配额即将用尽"
    description="免费额度为 500 次/天。接近上限时后端会自动降级为「仅自研模型」，识别置信度可能下降。"
  />

  <el-row :gutter="12">
    <el-col :xs="12" :sm="6">
      <StatCard label="识别总次数" :value="stats.totalRecognitions" unit="次" icon="Camera" color="#2F7D5D" />
    </el-col>
    <el-col :xs="12" :sm="6">
      <StatCard
        label="用户已确认"
        :value="stats.confirmedCount"
        unit="次"
        icon="CircleCheck"
        color="#67C23A"
        :extra="`其中纠正 ${stats.correctedCount} 次`"
      />
    </el-col>
    <el-col :xs="12" :sm="6">
      <StatCard
        label="真实场景 Top-1"
        :value="toPercent(stats.realWorldTop1Accuracy, 1)"
        icon="TrendCharts"
        color="#409EFF"
        extra="基于用户采纳/纠正统计"
      />
    </el-col>
    <el-col :xs="12" :sm="6">
      <StatCard
        label="Pl@ntNet 配额"
        :value="`${stats.plantnetQuota.used}/${stats.plantnetQuota.limit}`"
        icon="Odometer"
        :color="quotaPercent >= 80 ? '#F56C6C' : '#E6A23C'"
      />
    </el-col>
  </el-row>

  <el-row :gutter="12" class="gt-mt-16">
    <!-- ============ 模型调用 ============ -->
    <el-col :xs="24" :md="12">
      <div class="gt-card">
        <h3 class="gt-section-title">模型调用分布</h3>
        <ChartBox :option="usageOption" height="280px" />

        <el-table :data="stats.modelUsage" size="small" class="gt-mt-16">
          <el-table-column prop="name" label="模块" />
          <el-table-column prop="calls" label="调用" width="90" align="right" />
          <el-table-column label="平均耗时" width="110" align="right">
            <template #default="{ row }">{{ formatLatency(row.avgLatencyMs) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </el-col>

    <!-- ============ 各物种准确率 ============ -->
    <el-col :xs="24" :md="12">
      <div class="gt-card">
        <h3 class="gt-section-title">各物种识别准确率</h3>
        <div class="gt-text-secondary gt-text-small gt-mb-16">
          准确率 = 1 − 纠正次数 ÷ 识别次数。这比测试集指标更能反映真实使用情况。
        </div>

        <div v-for="s in stats.bySpecies" :key="s.speciesId" class="acc-row">
          <span class="acc-name">{{ s.name }}</span>
          <el-progress
            :percentage="Math.round(s.accuracy * 100)"
            :stroke-width="10"
            :color="accColor(s.accuracy)"
            class="acc-bar"
          />
          <span class="acc-detail gt-text-secondary">
            {{ s.total }} 次 / 纠正 {{ s.corrected }}
          </span>
        </div>
      </div>

      <!-- ============ 数据闭环说明 ============ -->
      <div class="gt-card gt-mt-16">
        <h3 class="gt-section-title">数据闭环</h3>
        <div class="loop">
          <div class="loop-step">
            <span class="loop-num">1</span>用户拍照识别，拿到 Top-3
          </div>
          <div class="loop-step">
            <span class="loop-num">2</span>用户采纳或纠正，结果落库
          </div>
          <div class="loop-step">
            <span class="loop-num">3</span>纠正数据统计出真实场景准确率
          </div>
          <div class="loop-step">
            <span class="loop-num">4</span>长期可作为模型迭代的标注来源
          </div>
        </div>
      </div>
    </el-col>
  </el-row>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import ChartBox from '@/components/ChartBox.vue'
import { adminGetAiUsage } from '@/api/admin'
import { toPercent, formatLatency } from '@/utils/format'

const loading = ref(false)
const stats = ref({
  totalRecognitions: 0,
  confirmedCount: 0,
  correctedCount: 0,
  realWorldTop1Accuracy: 0,
  plantnetQuota: { used: 0, limit: 500 },
  modelUsage: [],
  bySpecies: []
})

const quotaPercent = computed(() => {
  const q = stats.value.plantnetQuota
  return q?.limit ? Math.round((q.used / q.limit) * 100) : 0
})

const usageOption = computed(() => ({
  tooltip: { trigger: 'item', formatter: '{b}<br/>{c} 次 ({d}%)' },
  legend: { bottom: 0, icon: 'circle', textStyle: { fontSize: 11 } },
  series: [
    {
      type: 'pie',
      radius: ['42%', '66%'],
      center: ['50%', '44%'],
      itemStyle: { borderColor: '#fff', borderWidth: 2 },
      label: { show: false },
      data: (stats.value.modelUsage || []).map((m) => ({ name: m.name, value: m.calls }))
    }
  ]
}))

const accColor = (a) => (a >= 0.9 ? '#67C23A' : a >= 0.75 ? '#E6A23C' : '#F56C6C')

async function load() {
  loading.value = true
  try {
    stats.value = await adminGetAiUsage()
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.acc-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 7px 0;
  font-size: 13px;
}

.acc-name {
  width: 64px;
  flex: 0 0 auto;
}

.acc-bar {
  flex: 1;
}

.acc-detail {
  width: 120px;
  text-align: right;
  flex: 0 0 auto;
  font-size: 12px;
}

.loop {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.loop-step {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--gt-text-regular);
}

.loop-num {
  flex: 0 0 20px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--gt-primary-light);
  color: var(--gt-primary-dark);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 600;
}
</style>
