<template>
  <div class="gt-page">
    <PageHeader
      title="智能诊断"
      desc="拍照 → 认出是什么植物 → 排除病理性病害 → 判断养护问题。三步走完，每一步都告诉你依据"
    >
      <template #actions>
        <el-button text :icon="Clock" @click="router.push('/diagnose/records')">诊断记录</el-button>
        <el-button text :icon="Refresh" @click="reset">重新开始</el-button>
      </template>
    </PageHeader>

    <!-- ============ 步骤条 ============ -->
    <div class="gt-card steps-card">
      <el-steps :active="step" align-center finish-status="success">
        <el-step title="拍照识别" description="这是什么植物" />
        <el-step title="病害识别" description="有没有病理性病害" />
        <el-step title="症状诊断" description="养护上出了什么问题" />
      </el-steps>
    </div>

    <el-row :gutter="12" class="gt-mt-16">
      <!-- ==================== 左：当前步骤 ==================== -->
      <el-col :xs="24" :md="15">
        <div class="gt-card">
          <!-- ---------------- STEP 1 拍照识别 ---------------- -->
          <div v-show="step === 0">
            <h3 class="gt-section-title">第 1 步 · 拍照识别</h3>

            <ImageUploader v-model="file" capture="environment" hint="建议拍完整叶片或整株，光线充足、主体居中" />

            <el-button
              type="primary"
              size="large"
              style="width: 100%"
              class="gt-mt-16"
              :loading="recognizing"
              :disabled="!file"
              @click="startRecognize"
            >
              {{ recognizing ? '识别中…' : '开始识别' }}
            </el-button>

            <!-- 识别结果 -->
            <div v-if="recognition" class="recog-block">
              <div class="gt-flex-between gt-mb-8">
                <span class="block-label">识别结果（点击选择）</span>
                <span class="gt-text-secondary gt-text-small">
                  {{ providerLabel }} · {{ formatLatency(recognition.latencyMs) }}
                </span>
              </div>

              <el-alert
                v-if="recognition.lowConfidence"
                type="warning"
                :closable="false"
                show-icon
                class="gt-mb-8"
                title="置信度较低"
                description="模型不太确定。可以换张更清晰的照片，或直接选一个最像的继续。"
              />

              <div
                v-for="r in recognition.results"
                :key="r.rank"
                class="recog-item"
                :class="{ active: species && species.id === r.speciesId }"
                @click="pickSpecies(r)"
              >
                <span class="recog-emoji">{{ r.emoji || '🌿' }}</span>
                <div class="recog-main">
                  <div class="recog-name">
                    {{ r.commonName }}
                    <ConfidenceTag :confidence="r.confidence" />
                  </div>
                  <div class="recog-sci">{{ r.scientificName }}</div>
                </div>
                <el-icon v-if="species && species.id === r.speciesId" class="recog-check">
                  <CircleCheckFilled />
                </el-icon>
              </div>

              <div class="skip-line">
                都不像？
                <span class="gt-link" @click="species = null">跳过这一步</span>
                ，直接做病害和症状诊断也可以（只是判因会少一个参考维度）
              </div>
            </div>

            <el-alert
              v-else-if="!file"
              type="info"
              :closable="false"
              show-icon
              class="gt-mt-16"
              title="没有照片也能用"
              description="直接点「下一步」跳过识别。第 2 步会自动跳过，第 3 步的症状诊断仍然可用。"
            />
          </div>

          <!-- ---------------- STEP 2 病害识别 ---------------- -->
          <div v-show="step === 1">
            <h3 class="gt-section-title">第 2 步 · 病害识别</h3>

            <!-- 情况 A：没有照片 -->
            <el-alert
              v-if="!file"
              type="info"
              :closable="false"
              show-icon
              title="没有照片，跳过病害识别"
              description="病害识别需要图像。请直接进入第 3 步做症状诊断。"
            />

            <!-- 情况 B：该物种无病害数据 -->
            <el-alert
              v-else-if="!diseaseCovered"
              type="warning"
              :closable="false"
              show-icon
              title="这种植物暂无病害识别数据"
              :description="`公开的室内植物病害数据集目前只覆盖：${DISEASE_COVERED_SPECIES.join('、')}。${
                species ? species.commonName : '该植物'
              }不在其中，硬跑模型只会给出无意义的结果，所以这一步跳过。`"
            />

            <!-- 情况 C：可以识别 -->
            <template v-else>
              <div v-if="!disease">
                <div class="photo-strip">
                  <img v-if="previewUrl" :src="previewUrl" class="strip-img" alt="待识别照片" />
                  <div class="strip-text">
                    <div class="strip-title">
                      将对这张照片做病害识别
                      <template v-if="species">（{{ species.commonName }}）</template>
                    </div>
                    <div class="gt-text-secondary gt-text-small">
                      模型只判断是否有真菌 / 细菌 / 虫害等有明确图像特征的病理性问题
                    </div>
                  </div>
                </div>

                <el-button
                  type="primary"
                  size="large"
                  style="width: 100%"
                  class="gt-mt-16"
                  :loading="diseaseChecking"
                  @click="startDiseaseCheck"
                >
                  {{ diseaseChecking ? '识别中…' : '开始病害识别' }}
                </el-button>
              </div>

              <!-- 识别结果 -->
              <template v-else>
                <div class="disease-main">
                  <div class="disease-head">
                    <span class="disease-name">{{ disease.predicted.nameCn }}</span>
                    <el-tag
                      size="small"
                      effect="plain"
                      :style="{ color: diseaseCategory.color, borderColor: diseaseCategory.color }"
                    >
                      {{ diseaseCategory.label }}
                    </el-tag>
                    <ConfidenceTag :confidence="disease.predicted.confidence" />
                    <el-tag v-if="disease.predicted.severity === 'HIGH'" type="danger" size="small">
                      严重
                    </el-tag>
                  </div>
                </div>

                <div v-if="disease.topK && disease.topK.length > 1" class="topk">
                  <div class="gt-text-secondary gt-text-small gt-mb-8">其他可能</div>
                  <div v-for="k in disease.topK.slice(1)" :key="k.code" class="topk-row">
                    <span class="topk-name">{{ k.nameCn }}</span>
                    <el-progress
                      :percentage="Math.round(k.confidence * 100)"
                      :stroke-width="6"
                      :show-text="false"
                      color="#B9D5C6"
                      class="topk-bar"
                    />
                    <span class="topk-val">{{ toPercent(k.confidence) }}</span>
                  </div>
                </div>

                <el-descriptions v-if="disease.symptoms" :column="1" border class="gt-mt-16">
                  <el-descriptions-item label="典型症状">{{ disease.symptoms }}</el-descriptions-item>
                </el-descriptions>

                <div class="section-block">
                  <div class="gt-explain-title">
                    <el-icon><FirstAidKit /></el-icon>处理建议
                  </div>
                  <div class="treatment">{{ disease.treatment }}</div>
                </div>

                <div v-if="disease.prevention" class="section-block">
                  <div class="gt-explain-title">
                    <el-icon><Umbrella /></el-icon>预防
                  </div>
                  <div class="treatment">{{ disease.prevention }}</div>
                </div>

                <el-alert v-if="disease.note" type="info" :closable="false" class="gt-mt-16">
                  <template #title><span class="gt-text-small">{{ disease.note }}</span></template>
                </el-alert>

                <!-- 两个出口：病害成立 / 继续排查养护问题 -->
                <div class="branch-box">
                  <div class="branch-title">接下来怎么走？</div>
                  <div class="branch-actions">
                    <el-button type="danger" plain @click="finishAsDisease">
                      <el-icon><CircleCheckFilled /></el-icon>
                      确认是这个问题，结束诊断
                    </el-button>
                    <el-button type="primary" @click="goStep(2)">
                      不太像 / 想继续排查养护问题
                      <el-icon><ArrowRight /></el-icon>
                    </el-button>
                  </div>
                  <div class="gt-text-secondary gt-text-small gt-mt-8">
                    病理性病害和养护问题可能同时存在。比如浇水过多导致的根系受损，
                    也会让植株更容易被病菌侵染。
                  </div>
                </div>
              </template>
            </template>
          </div>

          <!-- ---------------- STEP 3 症状诊断 ---------------- -->
          <div v-show="step === 2">
            <h3 class="gt-section-title">第 3 步 · 症状诊断</h3>

            <div class="gt-text-secondary gt-text-small gt-mb-16">
              同一张黄叶照片可能对应 10 种完全不同原因，所以系统靠你的描述缩小范围，再去知识库找依据。
            </div>

            <el-form label-position="top">
              <el-form-item label="是哪盆植物？（可选，选了会结合养护记录判断）">
                <el-select v-model="userPlantId" placeholder="选择我的植物" clearable style="width: 100%">
                  <el-option
                    v-for="p in plants"
                    :key="p.id"
                    :label="`${p.speciesEmoji} ${p.nickname}（${p.speciesName}）`"
                    :value="p.id"
                  />
                </el-select>
              </el-form-item>
            </el-form>

            <SymptomSelector v-model="symptomCodes" :context="symptomContext" @update:context="(c) => (symptomContext = c)" />

            <el-button
              type="primary"
              size="large"
              style="width: 100%"
              class="gt-mt-16"
              :loading="analyzing"
              :disabled="!canAnalyze"
              @click="startAnalyze"
            >
              {{ analyzing ? '分析中…' : '开始分析' }}
            </el-button>
            <div v-if="!canAnalyze" class="gt-text-secondary gt-text-small gt-mt-8">
              请至少勾选一个症状；若勾选了「叶片发黄」，还需说明黄叶从哪个位置开始。
            </div>

            <!-- 判因结果 -->
            <template v-if="symptomResult">
              <el-divider />

              <div class="gt-flex-between gt-mb-16">
                <span class="block-label">诊断结果</span>
                <el-tag size="small" effect="plain" :type="confMeta.type">{{ confMeta.label }}</el-tag>
              </div>

              <!-- 信息不足 → 引导补充，不武断下结论 -->
              <template v-if="symptomResult.needMoreInfo">
                <el-alert type="warning" :closable="false" show-icon title="目前的信息还不足以判断原因">
                  <template #description>
                    <div class="gt-text-small">{{ symptomResult.fallbackSuggestion }}</div>
                  </template>
                </el-alert>

                <div class="followup">
                  <div class="gt-explain-title">补充这些信息会更准确：</div>
                  <ul class="followup-list">
                    <li v-for="(q, i) in symptomResult.followUpQuestions" :key="i">{{ q }}</li>
                  </ul>
                </div>
              </template>

              <template v-else>
                <DiagnosisEvidence
                  v-for="c in symptomResult.candidates"
                  :key="c.causeCode"
                  :candidate="c"
                  :rank="c.rank"
                />

                <div v-if="symptomResult.suggestion" class="gt-explain gt-mt-16">
                  <div class="gt-explain-title">综合建议</div>
                  <div class="suggestion-text">{{ symptomResult.suggestion }}</div>
                </div>

                <CitationList :citations="symptomResult.citations || []" />
              </template>

              <div class="gt-mt-16">
                <el-button type="primary" plain @click="router.push('/qa')">继续追问</el-button>
              </div>
            </template>
          </div>

          <!-- ---------------- 底部导航 ---------------- -->
          <div class="wizard-nav">
            <el-button :disabled="step === 0" @click="prev">
              <el-icon><ArrowLeft /></el-icon>上一步
            </el-button>

            <div class="nav-hint gt-text-secondary gt-text-small">
              第 {{ step + 1 }} / 3 步
            </div>

            <el-button v-if="step < 2" type="primary" @click="next">
              下一步<el-icon><ArrowRight /></el-icon>
            </el-button>
            <el-button v-else type="success" @click="finish">完成诊断</el-button>
          </div>
        </div>
      </el-col>

      <!-- ==================== 右：进度摘要 ==================== -->
      <el-col :xs="24" :md="9">
        <div class="gt-card summary-card">
          <h3 class="gt-section-title">诊断进度</h3>

          <!-- 照片 -->
          <div class="sum-block">
            <div class="sum-label">照片</div>
            <div v-if="previewUrl" class="sum-photo">
              <img :src="previewUrl" alt="照片" />
            </div>
            <div v-else class="sum-empty">未上传</div>
          </div>

          <!-- 植物 -->
          <div class="sum-block">
            <div class="sum-label">植物种类</div>
            <div v-if="species" class="sum-value">
              <span class="sum-emoji">{{ species.emoji }}</span>
              {{ species.commonName }}
              <el-tag v-if="!diseaseCovered" size="small" effect="plain" type="info" class="sum-tag">
                无病害数据
              </el-tag>
            </div>
            <div v-else class="sum-empty">未识别 / 已跳过</div>
          </div>

          <!-- 病害结论 -->
          <div class="sum-block">
            <div class="sum-label">病害识别</div>
            <div v-if="disease" class="sum-value">
              <el-tag size="small" type="danger" effect="plain">{{ disease.predicted.nameCn }}</el-tag>
            </div>
            <div v-else-if="!file" class="sum-empty">无照片，已跳过</div>
            <div v-else-if="!diseaseCovered" class="sum-empty">该物种无数据，已跳过</div>
            <div v-else-if="diseaseDecision === 'care'" class="sum-empty">已排除，继续排查养护问题</div>
            <div v-else class="sum-empty">未执行</div>
          </div>

          <!-- 症状结论 -->
          <div class="sum-block">
            <div class="sum-label">症状诊断</div>
            <div v-if="symptomResult && symptomResult.candidates && symptomResult.candidates.length" class="sum-value">
              {{ symptomResult.candidates[0].nameCn }}
              <span class="gt-text-secondary gt-text-small">
                {{ toPercent(symptomResult.candidates[0].confidence, 0) }}
              </span>
            </div>
            <div v-else-if="symptomResult && symptomResult.needMoreInfo" class="sum-empty">信息不足，待补充</div>
            <div v-else class="sum-empty">未执行</div>
          </div>

          <el-divider />

          <div class="flow-note">
            <div class="flow-step" :class="{ done: step > 0 }">
              <span class="flow-dot" />1 拍照识别
            </div>
            <div class="flow-step" :class="{ done: step > 1 }">
              <span class="flow-dot" />2 病害识别
            </div>
            <div class="flow-step" :class="{ done: !!symptomResult }">
              <span class="flow-dot" />3 症状诊断
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Clock,
  Refresh,
  ArrowLeft,
  ArrowRight,
  CircleCheckFilled,
  FirstAidKit,
  Umbrella
} from '@element-plus/icons-vue'

import PageHeader from '@/components/PageHeader.vue'
import ImageUploader from '@/components/ImageUploader.vue'
import SymptomSelector from '@/components/SymptomSelector.vue'
import DiagnosisEvidence from '@/components/DiagnosisEvidence.vue'
import CitationList from '@/components/CitationList.vue'
import ConfidenceTag from '@/components/ConfidenceTag.vue'

import { uploadPhoto } from '@/api/photo'
import { recognizeSpecies } from '@/api/recognition'
import { diagnoseDisease, diagnoseSymptom } from '@/api/diagnosis'
import { getSpeciesDetail } from '@/api/species'
import { getMyPlants } from '@/api/myPlant'

import { toPercent, formatLatency, diagnosisConfidence } from '@/utils/format'
import { DISEASE_CATEGORY, DISEASE_COVERED_SPECIES, RECOGNITION_PROVIDER } from '@/utils/constants'

const route = useRoute()
const router = useRouter()

/* ---------------- 步骤 ---------------- */
const step = ref(0)

function goStep(n) {
  step.value = Math.max(0, Math.min(2, n))
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
const next = () => goStep(step.value + 1)
const prev = () => goStep(step.value - 1)

/* ---------------- 状态 ---------------- */
const file = ref(null)
const previewUrl = ref('')
const photoId = ref(null)

const recognizing = ref(false)
const recognition = ref(null)
const species = ref(null)

const diseaseChecking = ref(false)
const disease = ref(null)
const diseaseDecision = ref('')

const plants = ref([])
const userPlantId = ref(null)
const symptomCodes = ref([])
const symptomContext = ref({ leafPosition: null, veinPattern: null, causeHint: null })
const analyzing = ref(false)
const symptomResult = ref(null)

/* ---------------- 派生 ---------------- */
/** 供应商标识由后端降级链决定，前端只负责展示 */
const providerLabel = computed(
  () => RECOGNITION_PROVIDER[recognition.value?.provider]?.label || '未知来源'
)

/** 该物种是否有病害识别数据；未识别物种时允许尝试 */
const diseaseCovered = computed(() => {
  if (!species.value) return true
  return DISEASE_COVERED_SPECIES.includes(species.value.commonName)
})

const diseaseCategory = computed(
  () => DISEASE_CATEGORY[disease.value?.predicted?.category] || { label: '未分类', color: '#909399' }
)

const confMeta = computed(() => diagnosisConfidence(symptomResult.value?.candidates?.[0]?.confidence ?? 0))

const canAnalyze = computed(() => {
  if (!symptomCodes.value.length) return false
  if (symptomCodes.value.includes('YELLOW_LEAF') && !symptomContext.value.leafPosition) return false
  return true
})

/* ---------------- 照片预览 ---------------- */
let objectUrl = ''
watch(file, (f) => {
  if (objectUrl) {
    URL.revokeObjectURL(objectUrl)
    objectUrl = ''
  }
  previewUrl.value = ''
  if (f) {
    objectUrl = URL.createObjectURL(f)
    previewUrl.value = objectUrl
  }
  // 换照片后，之前基于旧照片的结论全部作废
  photoId.value = null
  recognition.value = null
  disease.value = null
  diseaseDecision.value = ''
})

onBeforeUnmount(() => {
  if (objectUrl) URL.revokeObjectURL(objectUrl)
})

/* ---------------- 第 1 步 ---------------- */
async function startRecognize() {
  if (!file.value) return
  recognizing.value = true
  try {
    const photo = await uploadPhoto(file.value, { photoType: 'LEAF' })
    photoId.value = photo.id
    recognition.value = await recognizeSpecies(photo.id)
  } catch {
    /* 拦截器已提示 */
  } finally {
    recognizing.value = false
  }
}

/** 选中一个识别结果，并拉取它的完整档案（用于判断是否有病害数据） */
async function pickSpecies(r) {
  try {
    species.value = await getSpeciesDetail(r.speciesId)
  } catch {
    species.value = { id: r.speciesId, commonName: r.commonName, emoji: r.emoji }
  }
}

/* ---------------- 第 2 步 ---------------- */
async function startDiseaseCheck() {
  if (!file.value) return
  diseaseChecking.value = true
  try {
    let pid = photoId.value
    if (!pid) {
      const photo = await uploadPhoto(file.value, { photoType: 'DISEASE' })
      pid = photo.id
      photoId.value = pid
    }
    disease.value = await diagnoseDisease({ photoId: pid, userPlantId: userPlantId.value })
  } finally {
    diseaseChecking.value = false
  }
}

function finishAsDisease() {
  diseaseDecision.value = 'disease'
  ElMessage.success('诊断已完成，可在「诊断记录」中查看')
  router.push('/diagnose/records')
}

/* ---------------- 第 3 步 ---------------- */
async function startAnalyze() {
  analyzing.value = true
  try {
    symptomResult.value = await diagnoseSymptom({
      userPlantId: userPlantId.value,
      symptomCodes: symptomCodes.value,
      context: symptomContext.value
    })
    if (disease.value && diseaseDecision.value !== 'disease') diseaseDecision.value = 'care'
  } finally {
    analyzing.value = false
  }
}

/* ---------------- 全局 ---------------- */
function reset() {
  goStep(0)
  file.value = null
  photoId.value = null
  recognition.value = null
  species.value = null
  disease.value = null
  diseaseDecision.value = ''
  symptomCodes.value = []
  symptomContext.value = { leafPosition: null, veinPattern: null, causeHint: null }
  symptomResult.value = null
}

function finish() {
  ElMessage.success('诊断完成，已记入诊断记录')
  router.push('/diagnose/records')
}

onMounted(async () => {
  plants.value = (await getMyPlants()) || []

  // 从「我的植物」详情页点「诊断」进来时，预选那盆植物
  const pid = Number(route.query.plantId)
  if (pid) userPlantId.value = pid

  // 兼容旧链接：/diagnose?step=2|3 直接跳到对应步骤
  const s = Number(route.query.step)
  if (s >= 1 && s <= 3) step.value = s - 1
})
</script>

<style scoped>
.steps-card {
  padding: 20px 16px 8px;
}

.block-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--gt-text-regular);
}

/* ---------------- 第 1 步：识别结果 ---------------- */
.recog-block {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px dashed var(--gt-border);
}

.recog-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--gt-border);
  border-radius: var(--gt-radius);
  cursor: pointer;
  transition: all 0.15s ease;
  background: #fff;
}

.recog-item + .recog-item {
  margin-top: 8px;
}

.recog-item:hover {
  border-color: var(--gt-primary);
  background: #fafcfb;
}

.recog-item.active {
  border-color: var(--gt-primary);
  background: var(--gt-primary-light);
}

.recog-emoji {
  font-size: 26px;
}

.recog-main {
  flex: 1;
  min-width: 0;
}

.recog-name {
  font-size: 14.5px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}

.recog-sci {
  font-size: 12px;
  color: var(--gt-text-secondary);
  margin-top: 2px;
}

.recog-check {
  color: var(--gt-primary);
  font-size: 18px;
}

.skip-line {
  margin-top: 12px;
  font-size: 12.5px;
  color: var(--gt-text-secondary);
  line-height: 1.7;
}

/* ---------------- 第 2 步 ---------------- */
.photo-strip {
  display: flex;
  gap: 14px;
  align-items: center;
  padding: 12px;
  background: #fafbfc;
  border: 1px solid var(--gt-border);
  border-radius: var(--gt-radius);
}

.strip-img {
  width: 76px;
  height: 76px;
  object-fit: cover;
  border-radius: 8px;
  flex: 0 0 76px;
}

.strip-text {
  flex: 1;
  min-width: 0;
}

.strip-title {
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 4px;
}

.disease-main {
  padding: 14px;
  border: 1px solid var(--gt-danger);
  border-radius: var(--gt-radius);
  background: #fffafa;
}

.disease-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.disease-name {
  font-size: 18px;
  font-weight: 700;
}

.topk {
  margin-top: 16px;
}

.topk-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  padding: 3px 0;
}

.topk-name {
  width: 120px;
  flex: 0 0 auto;
}

.topk-bar {
  flex: 1;
}

.topk-val {
  width: 48px;
  text-align: right;
  color: var(--gt-text-secondary);
  font-variant-numeric: tabular-nums;
}

.section-block {
  margin-top: 18px;
}

.treatment {
  font-size: 13px;
  line-height: 1.95;
  color: var(--gt-text-regular);
  white-space: pre-line;
  margin-top: 6px;
}

.branch-box {
  margin-top: 20px;
  padding: 14px;
  border: 1px dashed var(--gt-primary);
  border-radius: var(--gt-radius);
  background: #fbfdfc;
}

.branch-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--gt-primary-dark);
  margin-bottom: 10px;
}

.branch-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

/* ---------------- 第 3 步 ---------------- */
.followup {
  margin-top: 16px;
}

.followup-list {
  margin: 0;
  padding-left: 20px;
  font-size: 13px;
  line-height: 2;
  color: var(--gt-text-regular);
}

.suggestion-text {
  font-size: 13px;
  line-height: 1.85;
  color: var(--gt-text-regular);
}

/* ---------------- 导航 ---------------- */
.wizard-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--gt-border);
}

.nav-hint {
  flex: 1;
  text-align: center;
}

/* ---------------- 摘要 ---------------- */
.summary-card {
  position: sticky;
  top: 12px;
}

.sum-block + .sum-block {
  margin-top: 16px;
}

.sum-label {
  font-size: 12px;
  color: var(--gt-text-secondary);
  margin-bottom: 6px;
}

.sum-value {
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.sum-emoji {
  font-size: 18px;
}

.sum-tag {
  transform: scale(0.9);
}

.sum-empty {
  font-size: 13px;
  color: #b1b3b8;
}

.sum-photo img {
  width: 100%;
  max-height: 150px;
  object-fit: contain;
  border-radius: 8px;
  background: #000;
}

.flow-note {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.flow-step {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--gt-text-secondary);
}

.flow-step.done {
  color: var(--gt-primary-dark);
  font-weight: 500;
}

.flow-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--gt-border);
  flex: 0 0 auto;
}

.flow-step.done .flow-dot {
  background: var(--gt-primary);
}

@media (max-width: 768px) {
  .summary-card {
    position: static;
    margin-top: 12px;
  }
  .branch-actions .el-button,
  .wizard-nav .el-button {
    flex: 1;
    margin-left: 0;
  }
}
</style>
