<template>
  <PageHeader title="养护日历" desc="到期提醒一目了然，完成后自动记入养护日志">
    <template #actions>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </template>
  </PageHeader>

  <el-row :gutter="12">
    <el-col :xs="24" :md="16">
      <div class="gt-card">
        <el-calendar v-model="calendarDate">
          <template #header="{ date }">
            <div class="cal-header">
              <span class="cal-title">{{ date }}</span>
              <div class="cal-summary">
                <el-tag size="small" effect="plain" type="primary">待完成 {{ summary.pending }}</el-tag>
                <el-tag size="small" effect="plain" type="success">已完成 {{ summary.done }}</el-tag>
                <el-tag v-if="summary.overdue" size="small" effect="plain" type="danger">
                  逾期 {{ summary.overdue }}
                </el-tag>
              </div>
            </div>
          </template>

          <template #date-cell="{ data }">
            <div class="cal-cell" :class="{ 'has-task': tasksOf(data.day).length }">
              <div class="cal-day">{{ data.day.split('-')[2] }}</div>
              <div class="cal-dots">
                <span
                  v-for="t in tasksOf(data.day).slice(0, 4)"
                  :key="t.id"
                  class="dot"
                  :style="{ background: dotColor(t) }"
                  :title="`${PLAN_TYPES[t.taskType]?.label} · ${t.plantNickname}`"
                />
              </div>
            </div>
          </template>
        </el-calendar>
      </div>
    </el-col>

    <el-col :xs="24" :md="8">
      <!-- 选中日期的任务 -->
      <div class="gt-card">
        <h3 class="gt-section-title">{{ selectedDateText }} 的任务</h3>
        <div v-if="selectedTasks.length" class="task-list">
          <div v-for="t in selectedTasks" :key="t.id" class="task-item">
            <span class="task-dot" :style="{ background: PLAN_TYPES[t.taskType]?.color }" />
            <div class="task-main">
              <div class="task-title">{{ PLAN_TYPES[t.taskType]?.label }} · {{ t.plantNickname }}</div>
              <div class="task-sub">{{ t.speciesName }}</div>
            </div>
            <el-tag size="small" effect="plain" :type="TASK_STATUS[t.status]?.type">
              {{ TASK_STATUS[t.status]?.label }}
            </el-tag>
            <el-button
              v-if="t.status !== 'DONE'"
              size="small"
              type="primary"
              plain
              @click="complete(t)"
            >
              完成
            </el-button>
          </div>
        </div>
        <EmptyState v-else emoji="☕" title="这天没有安排" desc="可以休息一下" />
      </div>

      <!-- 图例 -->
      <div class="gt-card gt-mt-16">
        <h3 class="gt-section-title">图例</h3>
        <div class="legend">
          <div v-for="(v, k) in PLAN_TYPES" :key="k" class="legend-row">
            <span class="legend-dot" :style="{ background: v.color }" />
            <span>{{ v.label }}</span>
          </div>
        </div>
        <el-divider />
        <div class="legend">
          <div class="legend-row">
            <span class="legend-dot" style="background: #F56C6C" />
            <span>逾期未完成</span>
          </div>
        </div>
      </div>
    </el-col>
  </el-row>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import { getCareCalendar, completeTask } from '@/api/careTask'
import { PLAN_TYPES, TASK_STATUS } from '@/utils/constants'

const loading = ref(false)
const calendarDate = ref(new Date())
const days = ref([])
const summary = ref({ total: 0, pending: 0, done: 0, overdue: 0 })

const monthKey = computed(() => dayjs(calendarDate.value).format('YYYY-MM'))

const selectedDateText = computed(() => dayjs(calendarDate.value).format('M 月 D 日'))

const taskMap = computed(() => {
  const map = {}
  for (const d of days.value) map[d.date] = d.tasks
  return map
})

const tasksOf = (day) => taskMap.value[day] || []

const selectedTasks = computed(() => {
  const day = dayjs(calendarDate.value).format('YYYY-MM-DD')
  return taskMap.value[day] || []
})

/** 逾期任务用红色点，其余按类型着色 */
function dotColor(t) {
  if (t.status === 'OVERDUE') return '#F56C6C'
  if (t.status === 'DONE') return '#C8CDD4'
  return PLAN_TYPES[t.taskType]?.color || '#909399'
}

async function load() {
  loading.value = true
  try {
    const data = await getCareCalendar(monthKey.value)
    days.value = data.days || []
    summary.value = data.summary || summary.value
  } finally {
    loading.value = false
  }
}

async function complete(task) {
  await completeTask(task.id, {})
  ElMessage.success('已完成，并记入养护日志')
  load()
}

watch(monthKey, load)
onMounted(load)
</script>

<style scoped>
.cal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 0 4px;
}

.cal-title {
  font-size: 15px;
  font-weight: 600;
}

.cal-summary {
  display: flex;
  gap: 6px;
}

.cal-cell {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
}

.cal-day {
  font-size: 13px;
}

.cal-dots {
  display: flex;
  gap: 3px;
  height: 6px;
}

.dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.task-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border: 1px solid var(--gt-border);
  border-radius: var(--gt-radius);
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
  font-size: 13.5px;
  font-weight: 500;
}

.task-sub {
  font-size: 12px;
  color: var(--gt-text-secondary);
}

.legend {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.legend-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--gt-text-regular);
}

.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

:deep(.el-calendar-day) {
  height: 62px;
  padding: 0;
}

:deep(.el-calendar-day:hover) {
  background: #f7fbf9;
}
</style>
