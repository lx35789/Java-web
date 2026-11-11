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
            <span>入手于 {{ formatDate(plant.acquiredDate) }}</span>
            <el-divider direction="vertical" />
            <span>最近浇水 {{ formatRelative(plant.lastWateredAt) }}</span>
            <el-divider direction="vertical" />
            <span class="next-due">下次浇水 {{ formatRelative(plant.nextWaterDueDate) }}</span>
          </div>
        </div>
        <div class="hero-actions">
          <el-button :icon="Camera" @click="router.push('/recognize')">拍照</el-button>
          <el-button :icon="Warning" @click="router.push(`/diagnose?plantId=${route.params.id}`)">诊断</el-button>
          <el-button type="primary" :icon="Edit" @click="editVisible = true">编辑</el-button>
        </div>
      </div>

      <el-row :gutter="12" class="gt-mt-16">
        <el-col :xs="24" :md="14">
          <!-- ============ 养护计划（★ 价值主张） ============ -->
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
                      <span class="gt-text-secondary">生成方式</span>
                      <el-tag size="small" effect="plain" :type="GENERATED_BY[p.generatedBy]?.type">
                        {{ GENERATED_BY[p.generatedBy]?.label }}
                      </el-tag>
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

          <!-- ============ 养护日志 ============ -->
          <div class="gt-card gt-mt-16">
            <div class="gt-flex-between gt-mb-16">
              <h3 class="gt-section-title" style="margin: 0">最近养护记录</h3>
              <el-button text size="small" @click="router.push('/care/logs')">
                全部日志<el-icon><ArrowRight /></el-icon>
              </el-button>
            </div>

            <el-timeline v-if="plant.recentLogs?.length">
              <el-timeline-item
                v-for="l in plant.recentLogs"
                :key="l.id"
                :timestamp="formatDateTime(l.loggedAt)"
                placement="top"
                :color="LOG_TYPES[l.logType]?.color"
              >
                <div class="log-line">
                  <b>{{ LOG_TYPES[l.logType]?.label }}</b>
                  <span v-if="l.amount" class="gt-text-secondary">{{ l.amount }}{{ l.unit }}</span>
                  <span v-if="l.detail" class="gt-text-secondary">· {{ l.detail }}</span>
                </div>
              </el-timeline-item>
            </el-timeline>
            <EmptyState v-else emoji="📝" title="还没有养护记录" />
          </div>
        </el-col>

        <el-col :xs="24" :md="10">
          <!-- ============ 成长时间线（P2） ============ -->
          <div class="gt-card">
            <h3 class="gt-section-title">成长时间线</h3>
            <div v-if="timeline.length" class="timeline-photos">
              <div v-for="t in timeline" :key="t.id" class="timeline-item">
                <div class="tl-cover">{{ t.emoji }}</div>
                <div class="tl-date">{{ formatDate(t.date) }}</div>
                <div class="tl-note">{{ t.note }}</div>
              </div>
            </div>
            <EmptyState v-else emoji="📷" title="还没有照片记录" desc="每次拍照都会自动进入时间线" />
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
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Camera, Warning, Edit, Refresh } from '@element-plus/icons-vue'
import CarePlanExplain from '@/components/CarePlanExplain.vue'
import EmptyState from '@/components/EmptyState.vue'
import { getMyPlantDetail, updateMyPlant, getTimeline } from '@/api/myPlant'
import { generateCarePlans } from '@/api/carePlan'
import { formatDate, formatDateTime, formatRelative } from '@/utils/format'
import {
  HEALTH_STATUS,
  LIGHT_LEVELS,
  POT_MATERIALS,
  PLAN_TYPES,
  GENERATED_BY,
  LOG_TYPES,
  DIAGNOSIS_TYPE,
  DIAGNOSIS_STATUS
} from '@/utils/constants'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const plant = ref({})
const timeline = ref([])
const activePlanTab = ref('')
const editVisible = ref(false)
const saving = ref(false)
const editForm = reactive({})

const healthMeta = computed(() => HEALTH_STATUS[plant.value.healthStatus] || HEALTH_STATUS.HEALTHY)
const plans = computed(() => plant.value.carePlans || [])

async function load() {
  loading.value = true
  try {
    plant.value = await getMyPlantDetail(route.params.id)
    if (plans.value.length) activePlanTab.value = String(plans.value[0].id)
    Object.assign(editForm, {
      nickname: plant.value.nickname,
      location: plant.value.location,
      lightLevel: plant.value.lightLevel,
      potMaterial: plant.value.potMaterial,
      healthStatus: plant.value.healthStatus
    })
    timeline.value = (await getTimeline(route.params.id))?.photos || []
  } finally {
    loading.value = false
  }
}

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

watch(() => route.params.id, load)
onMounted(load)
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

.next-due {
  color: var(--gt-primary-dark);
  font-weight: 600;
}

.hero-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.plan-summary {
  display: flex;
  align-items: center;
  gap: 28px;
  flex-wrap: wrap;
}

.plan-big {
  display: flex;
  align-items: baseline;
  gap: 3px;
}

.plan-num {
  font-size: 34px;
  font-weight: 700;
  color: var(--gt-primary-dark);
  line-height: 1;
}

.plan-unit {
  font-size: 13px;
  color: var(--gt-text-secondary);
}

.plan-side {
  flex: 1;
  min-width: 200px;
}

.side-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
  padding: 3px 0;
}

.log-line {
  font-size: 13px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.timeline-photos {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(110px, 1fr));
  gap: 10px;
}

.timeline-item {
  border: 1px solid var(--gt-border);
  border-radius: var(--gt-radius);
  overflow: hidden;
}

.tl-cover {
  height: 62px;
  background: linear-gradient(135deg, #eef6f1 0%, #e2f0e8 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30px;
}

.tl-date,
.tl-note {
  padding: 0 8px;
}

.tl-date {
  font-size: 11px;
  color: var(--gt-text-secondary);
  margin-top: 6px;
}

.tl-note {
  font-size: 11.5px;
  line-height: 1.5;
  padding-bottom: 8px;
  margin-top: 2px;
}

.diag-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.diag-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border: 1px solid var(--gt-border);
  border-radius: var(--gt-radius);
}

.diag-main {
  flex: 1;
  min-width: 0;
}

.diag-summary {
  font-size: 13px;
}

.diag-time {
  font-size: 11px;
  color: var(--gt-text-secondary);
  margin-top: 2px;
}

@media (max-width: 768px) {
  .hero {
    flex-wrap: wrap;
  }
  .hero-actions {
    width: 100%;
  }
}
</style>
