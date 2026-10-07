<template>
  <PageHeader title="环境设置" desc="这些信息决定了你的养护计划怎么算——同一种植物在不同环境下浇水频率可以差一倍" />

  <el-row :gutter="12">
    <el-col :xs="24" :md="14">
      <div class="gt-card">
        <h3 class="gt-section-title">所在城市</h3>
        <el-form label-width="110px">
          <el-form-item label="城市">
            <el-input v-model="form.city" placeholder="比如：杭州" style="max-width: 240px">
              <template #append>
                <el-button :loading="geocoding" @click="resolveCity">解析</el-button>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item v-if="env.latitude" label="坐标">
            <span class="gt-text-secondary">
              {{ env.latitude }}, {{ env.longitude }}
              <el-tag size="small" effect="plain" type="success" class="gt-ml-8">已缓存</el-tag>
            </span>
          </el-form-item>
        </el-form>

        <el-alert type="info" :closable="false" show-icon class="gt-mb-16">
          <template #title>
            <span class="gt-text-small">
              Open-Meteo 的天气接口只接受经纬度，不接受城市名。所以填写城市后需要先解析一次并缓存坐标，
              之后每天同步天气就不用再解析了。
            </span>
          </template>
        </el-alert>

        <el-divider content-position="left">室内条件</el-divider>

        <el-form label-width="110px">
          <el-form-item label="有空调">
            <el-switch v-model="form.hasAirConditioner" />
            <span class="switch-hint">夏季空调房空气干燥，浇水间隔会缩短</span>
          </el-form-item>

          <el-form-item label="有暖气">
            <el-switch v-model="form.hasHeating" />
            <span class="switch-hint">冬季暖气房同样干燥</span>
          </el-form-item>

          <el-form-item label="空气湿度">
            <el-radio-group v-model="form.indoorHumidityLevel">
              <el-radio value="LOW">偏干</el-radio>
              <el-radio value="MEDIUM">适中</el-radio>
              <el-radio value="HIGH">偏湿</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="自动同步天气">
            <el-switch v-model="form.weatherSyncEnabled" />
            <span class="switch-hint">每天定时拉取温湿度，自动调整浇水提醒</span>
          </el-form-item>
        </el-form>

        <el-button type="primary" :loading="saving" @click="save">保存设置</el-button>
      </div>
    </el-col>

    <el-col :xs="24" :md="10">
      <!-- 当前天气 -->
      <div class="gt-card">
        <div class="gt-flex-between gt-mb-16">
          <h3 class="gt-section-title" style="margin: 0">当前天气</h3>
          <el-button text size="small" :icon="Refresh" :loading="syncing" @click="sync">同步</el-button>
        </div>
        <div v-if="weather.city">
          <div class="weather-row">
            <span class="weather-label">温度</span>
            <span class="weather-value">{{ weather.temperature }} °C</span>
          </div>
          <div class="weather-row">
            <span class="weather-label">相对湿度</span>
            <span class="weather-value">{{ weather.relativeHumidity }} %</span>
          </div>
          <div class="weather-row">
            <span class="weather-label">数据来源</span>
            <span class="weather-value">{{ WEATHER_SOURCE[weather.source]?.label || weather.source }}</span>
          </div>
          <div class="weather-row">
            <span class="weather-label">观测时间</span>
            <span class="weather-value">{{ formatDateTime(weather.observedAt) }}</span>
          </div>

          <div class="gt-explain gt-mt-16">
            <div class="gt-explain-title">对养护计划的影响</div>
            <div class="impact-note">{{ weather.impactOnCare?.note }}</div>
          </div>
        </div>
        <EmptyState v-else emoji="🌤️" title="还没有天气数据" desc="先设置城市并同步" />
      </div>

      <!-- 系数说明 -->
      <div class="gt-card gt-mt-16">
        <h3 class="gt-section-title">环境如何影响浇水间隔</h3>
        <el-table :data="coefRows" size="small" :show-header="false">
          <el-table-column prop="label" />
          <el-table-column prop="value" width="90" align="right">
            <template #default="{ row }">
              <span class="coef-value">{{ row.value }}</span>
            </template>
          </el-table-column>
        </el-table>
        <div class="gt-text-secondary gt-text-small gt-mt-8">
          系数 &lt; 1 表示间隔缩短（需水更多），&gt; 1 表示间隔延长。完整拆解可在「养护计划」页查看。
        </div>
      </div>
    </el-col>
  </el-row>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import { getEnvironment, updateEnvironment } from '@/api/auth'
import { getCurrentWeather, syncWeather } from '@/api/weather'
import { formatDateTime } from '@/utils/format'
import { WEATHER_SOURCE } from '@/utils/constants'

const env = ref({})
const weather = ref({})
const saving = ref(false)
const syncing = ref(false)
const geocoding = ref(false)

const form = reactive({
  city: '',
  hasAirConditioner: false,
  hasHeating: false,
  indoorHumidityLevel: 'MEDIUM',
  weatherSyncEnabled: false
})

const coefRows = computed(() => [
  { label: '户外高温（> 30°C）', value: '× 0.85' },
  { label: '户外低温（< 10°C）', value: '× 1.30' },
  { label: '空气干燥（< 30%）', value: '× 0.90' },
  { label: '空气潮湿（> 60%）', value: '× 1.15' },
  { label: '空调房', value: '× 0.90' }
])

async function load() {
  env.value = (await getEnvironment()) || {}
  Object.assign(form, {
    city: env.value.city || '',
    hasAirConditioner: !!env.value.hasAirConditioner,
    hasHeating: !!env.value.hasHeating,
    indoorHumidityLevel: env.value.indoorHumidityLevel || 'MEDIUM',
    weatherSyncEnabled: !!env.value.weatherSyncEnabled
  })
  weather.value = (await getCurrentWeather()) || {}
}

/** 城市名 → 经纬度（真实由后端调用 Open-Meteo Geocoding API） */
async function resolveCity() {
  if (!form.city) {
    ElMessage.warning('请先填写城市')
    return
  }
  geocoding.value = true
  try {
    env.value = await updateEnvironment({ ...form })
    ElMessage.success('已解析并缓存坐标')
  } finally {
    geocoding.value = false
  }
}

async function save() {
  saving.value = true
  try {
    env.value = await updateEnvironment({ ...form })
    ElMessage.success('已保存，养护计划将在下次生成时应用')
  } finally {
    saving.value = false
  }
}

async function sync() {
  syncing.value = true
  try {
    weather.value = await syncWeather()
    ElMessage.success('天气已同步')
  } finally {
    syncing.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.switch-hint {
  font-size: 12px;
  color: var(--gt-text-secondary);
  margin-left: 12px;
}

.weather-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 7px 0;
  border-bottom: 1px dashed var(--gt-border);
  font-size: 13px;
}

.weather-row:last-of-type {
  border-bottom: none;
}

.weather-label {
  color: var(--gt-text-secondary);
}

.weather-value {
  font-weight: 500;
}

.impact-note {
  font-size: 12.5px;
  line-height: 1.8;
  color: var(--gt-text-regular);
}

.coef-value {
  font-family: ui-monospace, Menlo, Consolas, monospace;
  color: var(--gt-primary-dark);
}
</style>
