<template>
  <div v-loading="loading" class="gt-page">
    <el-page-header class="gt-mb-16" @back="router.push('/plants')">
      <template #content>
        <span class="gt-text-secondary">我的植物 / {{ plant.nickname || plant.speciesName }}</span>
      </template>
    </el-page-header>

    <template v-if="plant.id">
      <!-- ============ 头部 ============ -->
      <div class="gt-card hero">
        <div class="hero-cover">{{ plant.speciesEmoji }}</div>
        <div class="hero-info">
          <div class="hero-title">
            <h2>{{ plant.nickname || plant.speciesName }}</h2>
            <el-tag :type="healthMeta.type" effect="plain">{{ healthMeta.label }}</el-tag>
          </div>
          <div class="hero-sub">
            {{ plant.speciesName }} ·
            {{ plant.location || '未设置位置' }} ·
            {{ LIGHT_LEVELS[plant.lightLevel]?.label || '光照未设置' }} ·
            {{ POT_MATERIALS[plant.potMaterial]?.label || '花盆未设置' }}
          </div>
          <div class="hero-meta">
            <span>已陪伴 <b class="accent">{{ daysTogether }}</b> 天</span>
            <el-divider direction="vertical" />
            <span>近期记录 <b class="accent">{{ careCount }}</b> 条</span>
            <el-divider direction="vertical" />
            <span v-if="latest">最近观测 {{ latest.time }}</span>
            <span v-else class="gt-text-secondary">还没有状态观测</span>
          </div>
        </div>
        <div class="hero-actions">
          <el-button :icon="Camera" @click="router.push('/recognize')">拍照</el-button>
          <el-button :icon="Warning" @click="router.push(`/diagnose?plantId=${route.params.id}`)">诊断</el-button>
          <el-button type="primary" :icon="Edit" @click="editVisible = true">编辑</el-button>
        </div>
      </div>

      <!-- ============ 今天要做什么 ============ -->
      <div class="gt-card gt-mt-16 today-card">
        <div class="today-head">
          <div>
            <h3 class="gt-section-title" style="margin: 0">今天要做什么</h3>
            <div class="today-sub">
              {{ todayTasks.length ? `有 ${todayTasks.length} 件事到期或已逾期` : '今天没有到期的事，可以歇一歇' }}
            </div>
          </div>
          <el-button text size="small" @click="router.push('/care/calendar')">
            完整日历<el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>

        <div v-if="todayTasks.length" class="today-list">
          <div v-for="t in todayTasks" :key="t.id" class="today-item" :class="{ overdue: isOverdue(t) }">
            <span class="today-dot" :style="{ background: PLAN_TYPES[t.taskType]?.color }" />
            <div class="today-main">
              <div class="today-title">{{ PLAN_TYPES[t.taskType]?.label || t.taskType }}</div>
              <div class="today-meta">
                {{ formatDate(t.dueDate) }}
                <span v-if="isOverdue(t)" class="overdue-tag">逾期 {{ overdueDays(t) }} 天</span>
              </div>
            </div>
            <el-button size="small" type="primary" plain @click="complete(t)">完成</el-button>
            <el-button size="small" text @click="snooze(t)">顺延 3 天</el-button>
          </div>
        </div>
        <div v-else class="today-empty">
          下次浇水约在 <b>{{ nextWaterText }}</b>
        </div>
      </div>

      <el-row :gutter="12" class="gt-mt-16">
        <el-col :xs="24" :md="15">
          <!-- ============ 健康曲线 ============ -->
          <div class="gt-card">
            <div class="gt-flex-between gt-mb-16">
              <div>
                <h3 class="gt-section-title" style="margin: 0">健康曲线</h3>
                <div class="section-sub">每次快评或拍照都会在这条线上留一个点</div>
              </div>
              <div class="curve-actions">
                <el-tag v-if="series.summary?.latest != null" size="small" effect="plain" :type="trendType">
                  {{ series.summary.latest }} 分 · {{ trendLabel }}
                </el-tag>
                <el-button type="primary" size="small" :icon="EditPen" @click="openObserve">记录状态</el-button>
              </div>
            </div>

            <div v-if="points.length >= 2" ref="chartEl" class="chart"></div>
            <div v-else-if="points.length === 1" class="one-point">
              <div class="one-score">{{ points[0].index }}</div>
              <div class="one-hint">已记录 1 次（{{ points[0].date }}），再记一次就能看到趋势</div>
            </div>
            <EmptyState
              v-else
              emoji="📈"
              title="还没有健康记录"
              desc="每隔几天记一次「叶片精神吗、有没有新叶、黄叶几片」，就能看出这株植物的走势"
            >
              <el-button type="primary" @click="openObserve">记录第一次</el-button>
            </EmptyState>

            <div v-if="series.turns?.length" class="turns">
              <el-icon><TrendCharts /></el-icon>
              <span v-for="(t, i) in series.turns.slice(-3)" :key="i" class="turn">
                {{ t.date }} {{ t.reason }} {{ t.delta > 0 ? '+' : '' }}{{ t.delta }}
              </span>
            </div>
          </div>

          <!-- ============ 连续养护时间轴 ============ -->
          <div class="gt-card gt-mt-16">
            <div class="gt-flex-between gt-mb-16">
              <div>
                <h3 class="gt-section-title" style="margin: 0">连续养护记录</h3>
                <div class="section-sub">浇水、施肥、拍照、诊断、状态观测按时间混排</div>
              </div>
              <el-button text size="small" @click="router.push('/care/logs')">
                全部日志<el-icon><ArrowRight /></el-icon>
              </el-button>
            </div>

            <el-timeline v-if="events.length" class="care-timeline">
              <el-timeline-item
                v-for="e in events"
                :key="e.id"
                :timestamp="`${e.date} ${e.clock}`"
                placement="top"
                :color="TYPE_META[e.type]?.color || '#C0C4CC'"
              >
                <div class="ev">
                  <span class="ev-emoji">{{ TYPE_META[e.type]?.emoji }}</span>
                  <div class="ev-main">
                    <div class="ev-title">
                      {{ e.title }}
                      <el-tag v-if="e.type === 'CARE' && !e.fromTask" size="small" effect="plain" type="info">
                        补录
                      </el-tag>
                    </div>
                    <div v-if="e.detail" class="ev-detail">{{ e.detail }}</div>
                  </div>
                  <el-image v-if="e.url" :src="e.url" fit="cover" class="ev-thumb" :preview-src-list="[e.url]" />
                </div>
              </el-timeline-item>
            </el-timeline>
            <EmptyState v-else emoji="📝" title="还没有任何记录" desc="浇水、拍照、诊断都会自动出现在这里" />
          </div>
        </el-col>

        <el-col :xs="24" :md="9">
          <!-- ============ 养护计划 ============ -->
          <div class="gt-card">
            <div class="gt-flex-between gt-mb-16">
              <h3 class="gt-section-title" style="margin: 0">养护计划</h3>
              <el-button text size="small" :icon="Refresh" @click="regenerate">重新生成</el-button>
            </div>

            <el-tabs v-if="plans.length" v-model="activePlanTab">
              <el-tab-pane
                v-for="p in plans"
                :key="p.id"
                :name="String(p.id)"
                :label="PLAN_TYPES[p.planType]?.label || p.planType"
              >
                <div class="plan-summary">
                  <div class="plan-big">
                    <span class="plan-num">{{ p.effectiveIntervalDays }}</span>
                    <span class="plan-unit">天一次</span>
                  </div>
                  <div class="plan-side">
                    <div class="side-row">
                      <span class="gt-text-secondary">下次到期</span>
                      <b>{{ formatDate(p.nextDueDate) }}（{{ formatRelative(p.nextDueDate) }}）</b>
                    </div>
                    <div class="side-row">
                      <span class="gt-text-secondary">状态</span>
                      <el-tag size="small" :type="p.isActive ? 'success' : 'info'" effect="plain">
                        {{ p.isActive ? '生效中' : '已停用' }}
                      </el-tag>
                    </div>
                  </div>
                </div>

                <CarePlanExplain :plan="p" class="gt-mt-16" />
              </el-tab-pane>
            </el-tabs>

            <EmptyState
              v-else
              emoji="📋"
              title="还没有养护计划"
              desc="点击「重新生成」按当前环境计算"
            />
          </div>

          <!-- ============ 诊断记录 ============ -->
          <div class="gt-card gt-mt-16">
            <h3 class="gt-section-title">诊断记录</h3>
            <div v-if="plant.recentDiagnoses?.length" class="diag-list">
              <div v-for="d in plant.recentDiagnoses" :key="d.id" class="diag-item">
                <el-tag size="small" effect="plain" :type="DIAGNOSIS_TYPE[d.diagnosisType]?.type">
                  {{ DIAGNOSIS_TYPE[d.diagnosisType]?.label }}
                </el-tag>
                <div class="diag-main">
                  <div class="diag-summary">{{ d.summary }}</div>
                  <div class="diag-time">{{ formatDateTime(d.createdAt) }}</div>
                </div>
                <el-tag size="small" :type="DIAGNOSIS_STATUS[d.status]?.type" effect="plain">
                  {{ DIAGNOSIS_STATUS[d.status]?.label }}
                </el-tag>
              </div>
            </div>
            <EmptyState v-else emoji="🩺" title="还没有诊断记录" />
          </div>
        </el-col>
      </el-row>
    </template>

    <!-- ============ 状态快评 ============ -->
    <el-dialog v-model="observeVisible" title="记录这次的状态" width="520px">
      <div class="obs-tip">三个问题，十秒填完。系统会自动折算成 0-100 的健康分并连成曲线。</div>

      <el-form label-width="76px" class="obs-form">
        <el-form-item label="叶片">
          <el-radio-group v-model="obsForm.leafScore">
            <el-radio-button :value="1">精神饱满</el-radio-button>
            <el-radio-button :value="2">一般</el-radio-button>
            <el-radio-button :value="3">有点蔫</el-radio-button>
            <el-radio-button :value="4">明显不好</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="长势">
          <el-radio-group v-model="obsForm.growthScore">
            <el-radio-button :value="1">有新叶</el-radio-button>
            <el-radio-button :value="2">没变化</el-radio-button>
            <el-radio-button :value="3">有落叶</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="黄叶">
          <el-radio-group v-model="obsForm.yellowLeaf">
            <el-radio-button :value="0">没有</el-radio-button>
            <el-radio-button :value="1">1-2 片</el-radio-button>
            <el-radio-button :value="2">3-5 片</el-radio-button>
            <el-radio-button :value="3">很多</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="照片">
          <el-upload
            :auto-upload="false"
            :limit="1"
            accept="image/*"
            :show-file-list="true"
            :on-change="onPickPhoto"
            :on-remove="() => (obsFile = null)"
            list-type="picture"
          >
            <el-button size="small" :icon="Camera">选一张（可选）</el-button>
          </el-upload>
          <div class="obs-photo-tip">带照片时会额外用色彩分析算绿/黄/褐占比，曲线更客观</div>
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="obsForm.note" type="textarea" :rows="2" placeholder="比如：搬到窗边两天，新叶展开了" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="observeVisible = false">取消</el-button>
        <el-button type="primary" :loading="obsSaving" @click="submitObserve">保存</el-button>
      </template>
    </el-dialog>

    <!-- ============ 编辑对话框 ============ -->
    <el-dialog v-model="editVisible" title="编辑植物信息" width="520px">
      <el-form :model="editForm" label-width="90px">
        <el-form-item label="昵称">
          <el-input v-model="editForm.nickname" />
        </el-form-item>
        <el-form-item label="位置">
          <el-input v-model="editForm.location" />
        </el-form-item>
        <el-form-item label="光照条件">
          <el-select v-model="editForm.lightLevel" style="width: 100%">
            <el-option v-for="(v, k) in LIGHT_LEVELS" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="花盆材质">
          <el-select v-model="editForm.potMaterial" style="width: 100%">
            <el-option v-for="(v, k) in POT_MATERIALS" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="健康状态">
          <el-select v-model="editForm.healthStatus" style="width: 100%">
            <el-option v-for="(v, k) in HEALTH_STATUS" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Camera, Warning, Edit, EditPen, Refresh, ArrowRight, TrendCharts } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import dayjs from 'dayjs'
import CarePlanExplain from '@/components/CarePlanExplain.vue'
import EmptyState from '@/components/EmptyState.vue'
import { getMyPlantDetail, updateMyPlant, getTimeline } from '@/api/myPlant'
import { generateCarePlans } from '@/api/carePlan'
import { getCareTasks, completeTask, snoozeTask } from '@/api/careTask'
import { getObservationSeries, createObservation } from '@/api/plantObservation'
import { uploadPhoto } from '@/api/photo'
import { formatDate, formatDateTime, formatRelative } from '@/utils/format'
import {
  HEALTH_STATUS,
  LIGHT_LEVELS,
  POT_MATERIALS,
  PLAN_TYPES,
  DIAGNOSIS_TYPE,
  DIAGNOSIS_STATUS
} from '@/utils/constants'

/** 时间轴四类事件的视觉标识 */
const TYPE_META = {
  OBSERVATION: { emoji: '🩺', color: '#2F7D5D', label: '状态观测' },
  CARE: { emoji: '💧', color: '#409EFF', label: '养护' },
  PHOTO: { emoji: '📷', color: '#909399', label: '照片' },
  DIAGNOSIS: { emoji: '🔍', color: '#F56C6C', label: '诊断' }
}

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const plant = ref({})
const events = ref([])
const tasks = ref([])
const series = ref({ points: [], turns: [], summary: {} })
const activePlanTab = ref('')
const editVisible = ref(false)
const saving = ref(false)
const editForm = reactive({})

const chartEl = ref(null)
let chart = null

const healthMeta = computed(() => HEALTH_STATUS[plant.value.healthStatus] || HEALTH_STATUS.HEALTHY)
const plans = computed(() => plant.value.carePlans || [])
const points = computed(() => series.value.points || [])
const latest = computed(() => points.value[points.value.length - 1] || null)
const careCount = computed(() => events.value.length)

const daysTogether = computed(() => {
  if (!plant.value.acquiredDate) return '—'
  return Math.max(1, dayjs().diff(dayjs(plant.value.acquiredDate), 'day'))
})

/** 到期或已逾期的待办：只留今天及以前，未来任务交给日历页 */
const todayTasks = computed(() => {
  const today = dayjs().endOf('day')
  return tasks.value
    .filter((t) => t.status !== 'DONE' && t.status !== 'SKIPPED' && t.dueDate)
    .filter((t) => dayjs(t.dueDate).isBefore(today))
    .sort((a, b) => String(a.dueDate).localeCompare(String(b.dueDate)))
})

const nextWaterText = computed(() => {
  const w = plans.value.find((p) => p.planType === 'WATER' && p.isActive)
  if (!w?.nextDueDate) return '未计划'
  return `${formatDate(w.nextDueDate)}（${formatRelative(w.nextDueDate)}）`
})

const trendLabel = computed(() => {
  const t = series.value.summary?.trend
  if (t === 'UP') return '在变好'
  if (t === 'DOWN') return '在下滑'
  return '基本平稳'
})

const trendType = computed(() => {
  const t = series.value.summary?.trend
  if (t === 'UP') return 'success'
  if (t === 'DOWN') return 'danger'
  return 'info'
})

function isOverdue(t) {
  return dayjs(t.dueDate).isBefore(dayjs().startOf('day'))
}

function overdueDays(t) {
  return dayjs().startOf('day').diff(dayjs(t.dueDate), 'day')
}

async function load() {
  loading.value = true
  try {
    const id = route.params.id
    plant.value = await getMyPlantDetail(id)
    if (plans.value.length && !activePlanTab.value) activePlanTab.value = String(plans.value[0].id)
    Object.assign(editForm, {
      nickname: plant.value.nickname,
      location: plant.value.location,
      lightLevel: plant.value.lightLevel,
      potMaterial: plant.value.potMaterial,
      healthStatus: plant.value.healthStatus
    })

    const [tl, ts, s] = await Promise.all([
      getTimeline(id).catch(() => ({ events: [] })),
      getCareTasks({ userPlantId: id }).catch(() => []),
      getObservationSeries(id, 120).catch(() => ({ points: [], turns: [], summary: {} }))
    ])
    events.value = tl?.events || []
    tasks.value = ts || []
    series.value = s || { points: [], turns: [], summary: {} }

    await nextTick()
    renderChart()
  } finally {
    loading.value = false
  }
}

/* ---------------- 曲线 ---------------- */
function renderChart() {
  if (!chartEl.value || points.value.length < 2) {
    chart?.dispose()
    chart = null
    return
  }
  if (!chart) chart = echarts.init(chartEl.value)

  const dates = points.value.map((p) => String(p.date).slice(5))
  const values = points.value.map((p) => p.index)
  const down = series.value.summary?.trend === 'DOWN'

  chart.setOption(
    {
      grid: { left: 38, right: 18, top: 24, bottom: 28 },
      tooltip: {
        trigger: 'axis',
        formatter: (ps) => {
          const p = points.value[ps[0].dataIndex]
          return `${p.date}<br/>健康指数 <b>${p.index}</b>${p.note ? `<br/>${p.note}` : ''}`
        }
      },
      xAxis: {
        type: 'category',
        data: dates,
        boundaryGap: false,
        axisLine: { lineStyle: { color: '#E4E8E5' } },
        axisLabel: { color: '#7D8881', fontSize: 11 }
      },
      yAxis: {
        type: 'value',
        min: 0,
        max: 100,
        splitLine: { lineStyle: { color: '#F0F3F1' } },
        axisLabel: { color: '#7D8881', fontSize: 11 }
      },
      series: [
        {
          type: 'line',
          data: values,
          smooth: true,
          symbolSize: 7,
          lineStyle: { width: 2.5, color: down ? '#C57B2D' : '#2F7D5D' },
          itemStyle: { color: down ? '#C57B2D' : '#2F7D5D' },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: down ? 'rgba(197,123,45,0.22)' : 'rgba(47,125,93,0.20)' },
              { offset: 1, color: 'rgba(255,255,255,0)' }
            ])
          },
          markLine: {
            silent: true,
            symbol: 'none',
            lineStyle: { type: 'dashed', color: '#D6DBD8' },
            data: [{ yAxis: 55, label: { formatter: '警戒线', color: '#9AA5A0', fontSize: 10 } }]
          }
        }
      ]
    },
    true
  )
}

function onResize() {
  chart?.resize()
}

/* ---------------- 操作 ---------------- */
async function save() {
  saving.value = true
  try {
    await updateMyPlant(route.params.id, { ...editForm })
    ElMessage.success('已保存')
    editVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function regenerate() {
  await generateCarePlans(Number(route.params.id))
  ElMessage.success('已按当前环境重新生成养护计划')
  load()
}

async function complete(t) {
  await completeTask(t.id, {})
  ElMessage.success('已完成，并记入养护记录')
  load()
}

async function snooze(t) {
  const d = dayjs(t.dueDate).isBefore(dayjs()) ? dayjs().add(3, 'day') : dayjs(t.dueDate).add(3, 'day')
  await snoozeTask(t.id, d.format('YYYY-MM-DD'))
  ElMessage.success('已顺延 3 天')
  load()
}

/* ---------------- 状态快评 ---------------- */
const observeVisible = ref(false)
const obsSaving = ref(false)
const obsFile = ref(null)
const obsForm = reactive({ leafScore: 2, growthScore: 2, yellowLeaf: 0, note: '' })

function openObserve() {
  obsForm.leafScore = 2
  obsForm.growthScore = 2
  obsForm.yellowLeaf = 0
  obsForm.note = ''
  obsFile.value = null
  observeVisible.value = true
}

function onPickPhoto(uploadFile) {
  obsFile.value = uploadFile.raw
}

async function submitObserve() {
  obsSaving.value = true
  try {
    let photoId = null
    if (obsFile.value) {
      const photo = await uploadPhoto(obsFile.value, {
        photoType: 'OBSERVATION',
        userPlantId: Number(route.params.id)
      })
      photoId = photo?.id || null
    }
    await createObservation(Number(route.params.id), {
      leafScore: obsForm.leafScore,
      growthScore: obsForm.growthScore,
      yellowLeaf: obsForm.yellowLeaf,
      note: obsForm.note || null,
      photoId
    })
    observeVisible.value = false
    ElMessage.success('已记录，曲线和计划都会跟着更新')
    load()
  } catch (e) {
    ElMessage.error(e?.message || '记录失败')
  } finally {
    obsSaving.value = false
  }
}

watch(
  () => route.params.id,
  () => load()
)
onMounted(() => {
  load()
  window.addEventListener('resize', onResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
})
</script>

<style scoped>
.hero {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

.hero-cover {
  width: 88px;
  height: 88px;
  flex: 0 0 88px;
  border-radius: 12px;
  background: linear-gradient(135deg, #eef6f1 0%, #e2f0e8 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 42px;
}

.hero-info {
  flex: 1;
  min-width: 0;
}

.hero-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.hero-title h2 {
  margin: 0;
  font-size: 21px;
}

.hero-sub {
  font-size: 13px;
  color: var(--gt-text-secondary);
  margin-top: 5px;
}

.hero-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 10px;
  font-size: 12.5px;
  color: var(--gt-text-regular);
}

.accent {
  color: var(--gt-primary-dark);
}

/* 今日待办 */
.today-card {
  border-left: 3px solid var(--gt-primary-dark, #2f7d5d);
}

.today-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.today-sub {
  font-size: 12.5px;
  color: var(--gt-text-secondary);
  margin-top: 4px;
}

.today-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 14px;
}

.today-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--gt-border);
  border-radius: var(--gt-radius);
}

.today-item.overdue {
  border-color: #f3c9c6;
  background: #fffafa;
}

.today-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex: 0 0 auto;
}

.today-main {
  flex: 1;
  min-width: 0;
}

.today-title {
  font-size: 13.5px;
  font-weight: 500;
}

.today-meta {
  font-size: 12px;
  color: var(--gt-text-secondary);
  margin-top: 2px;
}

.overdue-tag {
  color: #c0392b;
  margin-left: 6px;
}

.today-empty {
  margin-top: 12px;
  font-size: 13px;
  color: var(--gt-text-secondary);
}

/* 曲线 */
.section-sub {
  font-size: 12px;
  color: var(--gt-text-secondary);
  margin-top: 3px;
}

.curve-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.chart {
  width: 100%;
  height: 240px;
}

.one-point {
  text-align: center;
  padding: 26px 0 10px;
}

.one-score {
  font-size: 40px;
  font-weight: 600;
  color: var(--gt-primary-dark);
  line-height: 1;
}

.one-hint {
  margin-top: 8px;
  font-size: 12.5px;
  color: var(--gt-text-secondary);
}

.turns {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--gt-border);
  font-size: 12px;
  color: var(--gt-text-secondary);
}

.turn + .turn {
  padding-left: 10px;
  border-left: 1px solid var(--gt-border);
}

/* 时间轴 */
.care-timeline {
  padding-left: 2px;
}

.care-timeline :deep(.el-timeline-item__timestamp) {
  font-size: 11.5px;
  color: var(--gt-text-secondary);
}

.ev {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.ev-emoji {
  font-size: 15px;
  line-height: 1.3;
}

.ev-main {
  flex: 1;
  min-width: 0;
}

.ev-title {
  font-size: 13.5px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 6px;
}

.ev-detail {
  font-size: 12.5px;
  color: var(--gt-text-secondary);
  margin-top: 2px;
  word-break: break-all;
}

.ev-thumb {
  width: 44px;
  height: 44px;
  border-radius: 6px;
  flex: 0 0 44px;
}

/* 计划 */
.plan-summary {
  display: flex;
  align-items: center;
  gap: 18px;
}

.plan-big {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.plan-num {
  font-size: 34px;
  font-weight: 600;
  color: var(--gt-primary-dark);
  line-height: 1;
}

.plan-unit {
  font-size: 12.5px;
  color: var(--gt-text-secondary);
}

.plan-side {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.side-row {
  display: flex;
  gap: 8px;
  font-size: 12.5px;
}

/* 诊断 */
.diag-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 4px;
}

.diag-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.diag-main {
  flex: 1;
  min-width: 0;
}

.diag-summary {
  font-size: 13px;
}

.diag-time {
  font-size: 11.5px;
  color: var(--gt-text-secondary);
  margin-top: 2px;
}

/* 快评对话框 */
.obs-tip {
  font-size: 12.5px;
  color: var(--gt-text-secondary);
  margin-bottom: 16px;
  padding: 8px 10px;
  background: #f5faf7;
  border-radius: 6px;
}

.obs-form :deep(.el-radio-button__inner) {
  padding: 8px 12px;
}

.obs-photo-tip {
  font-size: 11.5px;
  color: var(--gt-text-secondary);
  margin-top: 6px;
  width: 100%;
}
</style>
