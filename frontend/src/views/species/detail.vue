<template>
  <div v-loading="loading" class="gt-page">
    <el-page-header class="gt-mb-16" @back="router.back()">
      <template #content>
        <span class="gt-text-secondary">植物库 / {{ species.commonName || '详情' }}</span>
      </template>
    </el-page-header>

    <template v-if="species.id">
      <!-- ============ 头部 ============ -->
      <div class="gt-card hero">
        <div class="hero-cover">{{ species.emoji }}</div>
        <div class="hero-info">
          <div class="hero-title">
            <h2>{{ species.commonName }}</h2>
            <el-tag :type="difficultyType" effect="plain">{{ difficultyMeta.label }}</el-tag>
            <el-tag v-if="species.toxicity === 'TOXIC'" type="danger" effect="dark" size="small">对宠物有毒</el-tag>
          </div>
          <div class="hero-sci">{{ species.commonNameEn }} · <i>{{ species.scientificName }}</i> · {{ species.family }}</div>
          <p class="hero-desc">{{ species.description }}</p>
          <div class="hero-tags">
            <el-tag v-if="species.aliases?.length" size="small" effect="plain" type="info">
              别名：{{ species.aliases.join('、') }}
            </el-tag>
            <el-tag size="small" effect="plain">
              模型覆盖：{{ species.recognitionProvider === 'SELF_MODEL' ? '自研模型' : 'Pl@ntNet 兜底' }}
            </el-tag>
          </div>
        </div>
        <div class="hero-actions">
          <el-button type="primary" :icon="Plus" @click="addToMyPlants">加入我的植物</el-button>
        </div>
      </div>

      <el-row :gutter="12" class="gt-mt-16">
        <el-col :xs="24" :md="14">
          <!-- ============ 养护规则 ============ -->
          <div class="gt-card">
            <h3 class="gt-section-title">养护规则</h3>
            <el-descriptions :column="2" border size="default">
              <el-descriptions-item label="浇水间隔">
                <b>{{ species.baseWaterIntervalDays }}</b> 天（基准值）
              </el-descriptions-item>
              <el-descriptions-item label="单次水量">
                {{ species.waterAmountMl ? species.waterAmountMl + ' ml' : '—' }}
              </el-descriptions-item>
              <el-descriptions-item label="光照需求">
                {{ lightMeta.label }}
                <span class="gt-text-secondary gt-text-small">（{{ lightMeta.desc }}）</span>
              </el-descriptions-item>
              <el-descriptions-item label="湿度需求">
                {{ HUMIDITY_LEVELS[species.humidityRequirement]?.label || '—' }}
              </el-descriptions-item>
              <el-descriptions-item label="施肥间隔">
                {{ species.fertilizerIntervalDays }} 天
              </el-descriptions-item>
              <el-descriptions-item label="肥料类型">
                {{ species.fertilizerType || '—' }}
              </el-descriptions-item>
              <el-descriptions-item label="适宜温度">
                {{ species.tempMinC }} – {{ species.tempMaxC }} °C
              </el-descriptions-item>
              <el-descriptions-item label="土壤">{{ species.soilType || '—' }}</el-descriptions-item>
            </el-descriptions>

            <div class="gt-explain gt-mt-16">
              <div class="gt-explain-title">基准值怎么变成你的养护计划？</div>
              <div class="rule-note">
                上面的是<b>基准值</b>。系统会结合你所在城市、季节、摆放位置的光照、花盆材质和室温湿度，
                自动算出属于你这盆植物的实际间隔。加入我的植物后就能看到完整拆解。
              </div>
            </div>
          </div>

          <!-- ============ 季节系数 ============ -->
          <div v-if="species.seasonCoefficients" class="gt-card gt-mt-16">
            <h3 class="gt-section-title">季节系数</h3>
            <div class="season-grid">
              <div v-for="(coef, season) in species.seasonCoefficients" :key="season" class="season-item">
                <div class="season-name" :style="{ color: SEASONS[season]?.color }">
                  {{ SEASONS[season]?.label || season }}
                </div>
                <div class="season-coef">× {{ coef }}</div>
                <div class="season-days">
                  {{ Math.round(species.baseWaterIntervalDays * coef) }} 天
                </div>
              </div>
            </div>
            <div class="gt-text-secondary gt-text-small gt-mt-8">
              夏季生长旺盛需水多（系数 &lt; 1 表示间隔缩短），冬季休眠需水少（系数 &gt; 1 表示间隔延长）。
            </div>
          </div>

          <!-- ============ 养护提示 ============ -->
          <div v-if="species.careTips?.length" class="gt-card gt-mt-16">
            <h3 class="gt-section-title">养护提示</h3>
            <ul class="tips-list">
              <li v-for="(t, i) in species.careTips" :key="i">{{ t }}</li>
            </ul>
          </div>
        </el-col>

        <el-col :xs="24" :md="10">
          <!-- ============ 宠物安全 ============ -->
          <div class="gt-card">
            <h3 class="gt-section-title">宠物与安全</h3>
            <div v-if="species.toxicity === 'TOXIC'" class="safety safety--danger">
              <el-icon :size="18"><WarningFilled /></el-icon>
              <div>
                <div class="safety-title">对宠物有毒</div>
                <div class="safety-desc">
                  请放在猫狗够不到的地方。误食可能引起流口水、呕吐、口腔刺激。
                </div>
              </div>
            </div>
            <div v-else class="safety safety--safe">
              <el-icon :size="18"><CircleCheckFilled /></el-icon>
              <div>
                <div class="safety-title">对宠物无毒</div>
                <div class="safety-desc">可以安心摆放在宠物活动区域。</div>
              </div>
            </div>

            <div v-if="species.toxicTo?.length" class="toxic-table">
              <div v-for="t in species.toxicTo" :key="t.target" class="toxic-row">
                <span class="toxic-target">{{ TOXIC_TARGETS[t.target] || t.target }}</span>
                <el-tag :type="t.level === 'HIGH' ? 'danger' : 'warning'" size="small" effect="plain">
                  {{ t.level === 'HIGH' ? '高风险' : '中等风险' }}
                </el-tag>
              </div>
            </div>
          </div>

          <!-- ============ 状态提示 ============ -->
          <div class="gt-card gt-mt-16">
            <h3 class="gt-section-title">模块支持情况</h3>
            <div class="support-row">
              <span>物种识别</span>
              <el-tag
                :type="species.recognitionProvider === 'SELF_MODEL' ? 'success' : 'warning'"
                size="small"
                effect="plain"
              >
                {{ species.recognitionProvider === 'SELF_MODEL' ? '自研模型直接覆盖' : '走 Pl@ntNet 兜底' }}
              </el-tag>
            </div>
            <div class="support-row">
              <span>病害识别</span>
              <el-tag
                :type="diseaseCovered ? 'success' : 'info'"
                size="small"
                effect="plain"
              >
                {{ diseaseCovered ? '有对应病害数据' : '暂无标注数据' }}
              </el-tag>
            </div>
            <div class="gt-text-secondary gt-text-small gt-mt-8">
              病害识别目前覆盖绿萝、虎皮兰、芦荟、吊兰、多肉等有公开标注数据的物种；
              其余植物的叶片问题走「症状诊断」（规则 + 知识库）。
            </div>
          </div>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Plus } from '@element-plus/icons-vue'
import { getSpeciesDetail } from '@/api/species'
import { DIFFICULTY, LIGHT_LEVELS, HUMIDITY_LEVELS, TOXIC_TARGETS, SEASONS, DISEASE_COVERED_SPECIES } from '@/utils/constants'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const species = ref({})

const DIFFICULTY_TYPE = { EASY: 'success', MEDIUM: 'warning', HARD: 'danger' }
const difficultyMeta = computed(() => DIFFICULTY[species.value.difficulty] || DIFFICULTY.MEDIUM)
const difficultyType = computed(() => DIFFICULTY_TYPE[species.value.difficulty] || 'info')
const lightMeta = computed(() => LIGHT_LEVELS[species.value.lightRequirement] || { label: '—', desc: '' })

/** 是否有公开病害标注数据（与 docs/02 的 F10 覆盖范围一致，见 constants.js） */
const diseaseCovered = computed(() => DISEASE_COVERED_SPECIES.includes(species.value.commonName))

async function load() {
  loading.value = true
  try {
    species.value = await getSpeciesDetail(route.params.id)
  } finally {
    loading.value = false
  }
}

function addToMyPlants() {
  router.push({ path: '/plants/new', query: { speciesId: route.params.id, from: 'species' } })
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
  width: 96px;
  height: 96px;
  flex: 0 0 96px;
  border-radius: 12px;
  background: linear-gradient(135deg, #eef6f1 0%, #e2f0e8 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 46px;
}

.hero-info {
  flex: 1;
  min-width: 0;
}

.hero-title {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.hero-title h2 {
  margin: 0;
  font-size: 22px;
}

.hero-sci {
  font-size: 13px;
  color: var(--gt-text-secondary);
  margin-top: 4px;
}

.hero-desc {
  font-size: 13.5px;
  line-height: 1.8;
  color: var(--gt-text-regular);
  margin: 10px 0;
}

.hero-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.hero-actions {
  flex: 0 0 auto;
}

.season-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(80px, 1fr));
  gap: 10px;
}

.season-item {
  text-align: center;
  padding: 12px 8px;
  border: 1px solid var(--gt-border);
  border-radius: var(--gt-radius);
}

.season-name {
  font-size: 14px;
  font-weight: 600;
}

.season-coef {
  font-size: 12px;
  color: var(--gt-text-secondary);
  margin: 4px 0;
  font-family: ui-monospace, Menlo, Consolas, monospace;
}

.season-days {
  font-size: 15px;
  font-weight: 700;
  color: var(--gt-primary-dark);
}

.rule-note {
  font-size: 12.5px;
  line-height: 1.8;
  color: var(--gt-text-regular);
}

.tips-list {
  margin: 0;
  padding-left: 20px;
  font-size: 13.5px;
  line-height: 2;
  color: var(--gt-text-regular);
}

.tips-list li::marker {
  color: var(--gt-primary);
}

.safety {
  display: flex;
  gap: 10px;
  padding: 12px;
  border-radius: var(--gt-radius);
}

.safety--danger {
  background: #fef0f0;
  color: var(--gt-danger);
}

.safety--safe {
  background: #f0f9eb;
  color: var(--gt-success);
}

.safety-title {
  font-size: 14px;
  font-weight: 600;
}

.safety-desc {
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--gt-text-regular);
  margin-top: 3px;
}

.toxic-table {
  margin-top: 12px;
}

.toxic-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 7px 0;
  border-bottom: 1px dashed var(--gt-border);
  font-size: 13px;
}

.toxic-row:last-child {
  border-bottom: none;
}

.toxic-target {
  color: var(--gt-text-regular);
}

.support-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px dashed var(--gt-border);
  font-size: 13px;
}

.support-row:last-of-type {
  border-bottom: none;
}

@media (max-width: 768px) {
  .hero {
    flex-wrap: wrap;
  }
  .hero-actions {
    width: 100%;
  }
  .hero-actions .el-button {
    width: 100%;
  }
}
</style>
