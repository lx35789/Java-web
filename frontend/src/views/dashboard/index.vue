<template>
  <PageHeader title="统计看板" desc="你的植物、养护执行情况和健康状态一览">
    <template #actions>
      <el-button :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
      <el-button type="primary" :icon="Camera" @click="router.push('/recognize')">拍照识别</el-button>
    </template>
  </PageHeader>

  <!-- ============ 指标卡 ============ -->
  <el-row :gutter="12">
    <el-col :xs="12" :sm="12" :md="6">
      <StatCard label="我的植物" :value="overview.plantCount" unit="盆" icon="House" color="#2F7D5D" />
    </el-col>
    <el-col :xs="12" :sm="12" :md="6">
      <StatCard
        label="本月浇水"
        :value="overview.careStats.waterCountThisMonth"
        unit="次"
        icon="Sunny"
        color="#409EFF"
        :extra="`累计 ${overview.careStats.waterCountTotal} 次`"
      />
    </el-col>
    <el-col :xs="12" :sm="12" :md="6">
      <StatCard
        label="按时完成率"
        :value="toPercent(overview.careStats.onTimeRate, 0)"
        icon="CircleCheck"
        color="#67C23A"
        :extra="`逾期 ${overview.careStats.overdueCount} 条`"
      />
    </el-col>
    <el-col :xs="12" :sm="12" :md="6">
      <StatCard
        label="待处理诊断"
        :value="overview.diagnosisStats.open"
        unit="条"
        icon="FirstAidKit"
        :color="overview.diagnosisStats.open > 0 ? '#F56C6C' : '#909399'"
        :extra="`累计诊断 ${overview.diagnosisStats.total} 次`"
      />
    </el-col>
  </el-row>

  <el-row :gutter="12" class="gt-mt-16">
    <!-- ============ 左：今日待办 + 趋势 ============ -->
    <el-col :xs="24" :md="16">
      <div class="gt-card">
        <div class="gt-flex-between gt-mb-16">
          <h3 class="gt-section-title" style="margin: 0">今日待办</h3>
          <el-button text size="small" @click="router.push('/care/calendar')">
            查看日历<el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>

        <div v-if="todayTasks.length" class="task-list">
          <div v-for="t in todayTasks" :key="t.id" class="task-item">
            <span class="task-dot" :style="{ background: planMeta(t.taskType).color }" />
            <div class="task-main">
              <div class="task-title">
                {{ planMeta(t.taskType).label }} · {{ t.plantNickname || t.speciesName }}
              </div>
              <div class="task-sub">
                {{ t.speciesName }} · 应于 {{ formatRelative(t.dueDate) }}
              </div>
            </div>
            <el-tag :type="TASK_STATUS[t.status]?.type || 'info'" size="small" effect="plain">
              {{ TASK_STATUS[t.status]?.label || t.status }}
            </el-tag>
            <el-button size="small" type="primary" plain @click="complete(t)">标记完成</el-button>
          </div>
        </div>
        <EmptyState
          v-else
          emoji="✅"
          title="今天没有待办"
          desc="所有养护任务都已完成，保持住"
        />
      </div>

      <div class="gt-card gt-mt-16">
        <h3 class="gt-section-title">浇水执行趋势（近 8 周）</h3>
        <ChartBox :option="trendOption" height="260px" />
      </div>
    </el-col>

    <!-- ============ 右：健康分布 + 天气 ============ -->
    <el-col :xs="24" :md="8">
      <div class="gt-card">
        <h3 class="gt-section-title">植物健康状态</h3>
        <ChartBox :option="healthOption" height="220px" />
        <div class="health-legend">
          <div v-for="(v, k) in overview.healthDistribution" :key="k" class="legend-row">
            <span class="legend-dot" :style="{ background: HEALTH_STATUS[k]?.color }" />
            <span class="legend-label">{{ HEALTH_STATUS[k]?.label || k }}</span>
            <span class="legend-value">{{ v }} 盆</span>
          </div>
        </div>
      </div>

      <div class="gt-card gt-mt-16">
        <div class="gt-flex-between gt-mb-8">
          <h3 class="gt-section-title" style="margin: 0">当前天气</h3>
          <el-tag size="small" effect="plain">{{ weather.city }}</el-tag>
        </div>
        <div v-if="weather.city" class="weather-body">
          <div class="weather-main">
            <span class="weather-temp">{{ weather.temperature }}°C</span>
            <span class="weather-sub">湿度 {{ weather.relativeHumidity }}%</span>
          </div>
          <div class="gt-explain gt-mt-8">
            <div class="gt-explain-title">对养护计划的影响</div>
            <div class="weather-note">{{ weather.impactOnCare?.note }}</div>
          </div>
          <div class="gt-text-secondary gt-text-small gt-mt-8">
            数据来源 Open-Meteo · {{ formatDateTime(weather.observedAt) }}
          </div>
        </div>
      </div>
    </el-col>
  </el-row>

  <!-- ============ 快捷入口 ============ -->
  <div class="gt-card gt-mt-16">
    <h3 class="gt-section-title">快捷入口</h3>
    <div class="quick-grid">
      <div v-for="q in quickLinks" :key="q.path" class="quick-item" @click="router.push(q.path)">
        <el-icon :size="20" :style="{ color: q.color }"><component :is="q.icon" /></el-icon>
        <span>{{ q.label }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Camera } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import ChartBox from '@/components/ChartBox.vue'
import EmptyState from '@/components/EmptyState.vue'
import { getStatsOverview, getWateringTrend } from '@/api/stats'
import { getTodayTasks, completeTask } from '@/api/careTask'
import { getCurrentWeather } from '@/api/weather'
import { toPercent, formatRelative, formatDateTime } from '@/utils/format'
import { HEALTH_STATUS, PLAN_TYPES, TASK_STATUS } from '@/utils/constants'

const router = useRouter()
const loading = ref(false)

const overview = ref({
  plantCount: 0,
  speciesCount: 0,
  healthDistribution: {},
  careStats: { waterCountTotal: 0, waterCountThisMonth: 0, overdueCount: 0, onTimeRate: 0 },
  diagnosisStats: { total: 0, resolved: 0, open: 0 },
  qaCount: 0
})
const trend = ref({ weeks: [] })
const todayTasks = ref([])
const weather = ref({})

const planMeta = (t) => PLAN_TYPES[t] || { label: t, color: '#909399' }

const quickLinks = [
  { path: '/recognize', label: '拍照识别', icon: 'Camera', color: '#2F7D5D' },
  { path: '/diagnose', label: '智能诊断', icon: 'FirstAidKit', color: '#F56C6C' },
  { path: '/qa', label: 'AI 问答', icon: 'ChatDotRound', color: '#409EFF' },
  { path: '/plants/new', label: '添加植物', icon: 'Plus', color: '#67C23A' },
  { path: '/care/calendar', label: '养护日历', icon: 'Calendar', color: '#909399' }
]

/* ---------------- 图表配置 ---------------- */
const trendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['计划次数', '实际完成'], right: 0, top: 0, icon: 'roundRect' },
  grid: { left: 8, right: 8, bottom: 8, top: 36, containLabel: true },
  xAxis: {
    type: 'category',
    data: trend.value.weeks.map((w) => w.week),
    axisLine: { lineStyle: { color: '#E4E7ED' } },
    axisLabel: { color: '#909399' }
  },
  yAxis: {
    type: 'value',
    splitLine: { lineStyle: { color: '#F2F3F5' } },
    axisLabel: { color: '#909399' }
  },
  series: [
    {
      name: '计划次数',
      type: 'bar',
      data: trend.value.weeks.map((w) => w.planned),
      barWidth: 14,
      itemStyle: { color: '#D0E2D9', borderRadius: [4, 4, 0, 0] }
    },
    {
      name: '实际完成',
      type: 'line',
      smooth: true,
      data: trend.value.weeks.map((w) => w.actual),
      lineStyle: { width: 3, color: '#2F7D5D' },
      itemStyle: { color: '#2F7D5D' },
      areaStyle: { color: 'rgba(47,125,93,0.10)' },
      symbolSize: 7
    }
  ]
}))

const healthOption = computed(() => {
  const dist = overview.value.healthDistribution || {}
  const data = Object.keys(dist).map((k) => ({
    name: HEALTH_STATUS[k]?.label || k,
    value: dist[k],
    itemStyle: { color: HEALTH_STATUS[k]?.color }
  }))
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} 盆 ({d}%)' },
    series: [
      {
        type: 'pie',
        radius: ['52%', '74%'],
        center: ['50%', '50%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: '#fff', borderWidth: 2 },
        label: { show: true, formatter: '{c}', color: '#606266', fontSize: 12 },
        labelLine: { length: 6, length2: 6 },
        data
      }
    ]
  }
})

/* ---------------- 数据 ---------------- */
async function loadAll() {
  loading.value = true
  try {
    const [o, t, tasks, w] = await Promise.all([
      getStatsOverview(),
      getWateringTrend(8),
      getTodayTasks(),
      getCurrentWeather()
    ])
    overview.value = o
    trend.value = t
    todayTasks.value = tasks || []
    weather.value = w || {}
  } finally {
    loading.value = false
  }
}

async function complete(task) {
  await completeTask(task.id, {})
  ElMessage.success('已完成，并记录到养护日志')
  loadAll()
}

onMounted(loadAll)
</script>

<style scoped>
.task-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.task-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--gt-border);
  border-radius: var(--gt-radius);
  transition: background 0.15s ease;
}

.task-item:hover {
  background: #fafcfb;
}

.task-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex: 0 0 auto;
}

.task-main {
  flex: 1;
  min-width: 0;
}

.task-title {
  font-size: 14px;
  font-weight: 500;
}

.task-sub {
  font-size: 12px;
  color: var(--gt-text-secondary);
  margin-top: 2px;
}

.health-legend {
  margin-top: 8px;
  border-top: 1px dashed var(--gt-border);
  padding-top: 10px;
}

.legend-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  padding: 3px 0;
}

.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.legend-label {
  flex: 1;
  color: var(--gt-text-regular);
}

.legend-value {
  color: var(--gt-text-secondary);
  font-variant-numeric: tabular-nums;
}

.weather-main {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.weather-temp {
  font-size: 30px;
  font-weight: 700;
  color: var(--gt-primary-dark);
}

.weather-sub {
  font-size: 13px;
  color: var(--gt-text-secondary);
}

.weather-note {
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--gt-text-regular);
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 10px;
}

.quick-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--gt-border);
  border-radius: var(--gt-radius);
  cursor: pointer;
  font-size: 13px;
  transition: all 0.15s ease;
}

.quick-item:hover {
  border-color: var(--gt-primary);
  background: #fafcfb;
  transform: translateY(-1px);
}
</style>
