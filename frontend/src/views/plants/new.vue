<template>
  <PageHeader title="添加植物" desc="选择植物种类并填写摆放环境，系统会自动生成养护计划" />

  <el-tabs v-model="tab" class="gt-card">
    <!-- ==================== 手动添加 ==================== -->
    <el-tab-pane label="手动添加" name="manual">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" class="add-form">
        <el-form-item label="植物种类" prop="speciesId">
          <el-select
            v-model="form.speciesId"
            filterable
            placeholder="搜索中文名 / 学名"
            style="width: 100%"
            @change="onSpeciesChange"
          >
            <el-option v-for="s in speciesList" :key="s.id" :label="s.commonName" :value="s.id">
              <span class="opt-emoji">{{ s.emoji }}</span>
              <span>{{ s.commonName }}</span>
              <span class="opt-sci">{{ s.scientificName }}</span>
            </el-option>
          </el-select>
          <div v-if="selectedSpecies" class="species-preview">
            <span class="preview-emoji">{{ selectedSpecies.emoji }}</span>
            <span>
              基准浇水间隔 <b>{{ selectedSpecies.baseWaterIntervalDays }}</b> 天 ·
              光照需求 {{ LIGHT_LEVELS[selectedSpecies.lightRequirement]?.label }} ·
              {{ selectedSpecies.toxicity === 'TOXIC' ? '对宠物有毒' : '对宠物无毒' }}
            </span>
          </div>
        </el-form-item>

        <el-form-item label="给它起个名">
          <el-input v-model="form.nickname" placeholder="比如：小绿" maxlength="20" show-word-limit />
        </el-form-item>

        <el-divider content-position="left">摆放环境<span class="divider-hint">影响浇水频率</span></el-divider>

        <el-form-item label="摆放位置">
          <el-select v-model="form.location" placeholder="选择或输入" filterable allow-create style="width: 100%">
            <el-option v-for="l in LOCATIONS" :key="l" :label="l" :value="l" />
          </el-select>
        </el-form-item>

        <el-form-item label="光照条件">
          <el-radio-group v-model="form.lightLevel">
            <div v-for="(v, k) in LIGHT_LEVELS" :key="k" class="radio-line">
              <el-radio :value="k">
                <span class="radio-label">{{ v.label }}</span>
                <span class="radio-desc">{{ v.desc }}</span>
              </el-radio>
            </div>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="花盆">
          <div class="pot-row">
            <el-select v-model="form.potSize" placeholder="大小" style="width: 160px">
              <el-option v-for="(v, k) in POT_SIZES" :key="k" :label="`${v.label}（${v.desc}）`" :value="k" />
            </el-select>
            <el-select v-model="form.potMaterial" placeholder="材质" style="width: 100%">
              <el-option v-for="(v, k) in POT_MATERIALS" :key="k" :label="`${v.label} · ${v.desc}`" :value="k" />
            </el-select>
          </div>
        </el-form-item>

        <el-form-item label="土壤">
          <el-input v-model="form.soilType" placeholder="比如：通用营养土" />
        </el-form-item>

        <el-form-item label="入手日期">
          <el-date-picker v-model="form.acquiredDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 200px" />
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.note" type="textarea" :rows="2" placeholder="比如：朋友送的，原盆没换" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submit">添加并生成养护计划</el-button>
          <el-button @click="router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-tab-pane>

    <!-- ==================== 自然语言添加（P2） ==================== -->
    <el-tab-pane label="自然语言添加" name="nl">
      <div class="nl-wrap">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="用一句话描述，系统自动解析"
          description="例如：「我新买了一盆龟背竹，放在客厅」。解析后仍会显示给你确认，不会直接写入。"
          class="gt-mb-16"
        />

        <el-input
          v-model="nlText"
          type="textarea"
          :rows="3"
          placeholder="我新买了一盆龟背竹，放在客厅"
          maxlength="120"
          show-word-limit
        />

        <div class="gt-mt-16">
          <el-button type="primary" :loading="parsing" :disabled="!nlText.trim()" @click="parse">
            解析
          </el-button>
          <el-button v-for="ex in examples" :key="ex" text class="gt-text-small" @click="nlText = ex">
            {{ ex }}
          </el-button>
        </div>

        <!-- 解析结果 -->
        <div v-if="parsed" class="parse-result">
          <div class="gt-explain">
            <div class="gt-explain-title">解析结果</div>
            <div v-if="parsed.parsed" class="parse-fields">
              <div class="parse-field">
                <span class="field-label">植物</span>
                <span class="field-value">
                  {{ parsed.parsed.emoji }} {{ parsed.parsed.speciesName }}
                </span>
              </div>
              <div class="parse-field">
                <span class="field-label">位置</span>
                <span class="field-value">{{ parsed.parsed.location || '未提到，稍后可补充' }}</span>
              </div>
              <div class="parse-field">
                <span class="field-label">置信度</span>
                <span class="field-value">{{ toPercent(parsed.parsed.confidence) }}</span>
              </div>
            </div>
            <div v-else class="gt-text-secondary">{{ parsed.message || '没能解析出植物种类，请手动选择' }}</div>
          </div>

          <div v-if="parsed.parsed" class="gt-mt-16">
            <el-button type="primary" :loading="submitting" @click="submitParsed">确认添加</el-button>
            <el-button @click="parsed = null">重新描述</el-button>
          </div>
        </div>
      </div>
    </el-tab-pane>
  </el-tabs>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { getSpeciesList } from '@/api/species'
import { createMyPlant, parseNaturalLanguage } from '@/api/myPlant'
import { toPercent } from '@/utils/format'
import { LIGHT_LEVELS, POT_MATERIALS, POT_SIZES } from '@/utils/constants'

const route = useRoute()
const router = useRouter()

const tab = ref('manual')
const formRef = ref(null)
const submitting = ref(false)
const speciesList = ref([])

const LOCATIONS = ['客厅', '卧室', '书房', '阳台', '厨房', '卫生间', '办公室']

const form = reactive({
  speciesId: null,
  nickname: '',
  location: '客厅',
  lightLevel: 'MEDIUM',
  potSize: 'MEDIUM',
  potMaterial: 'PLASTIC',
  soilType: '',
  acquiredDate: new Date().toISOString().slice(0, 10),
  note: ''
})

const rules = {
  speciesId: [{ required: true, message: '请选择植物种类', trigger: 'change' }]
}

const selectedSpecies = computed(() => speciesList.value.find((s) => s.id === form.speciesId))

function onSpeciesChange(id) {
  const s = speciesList.value.find((i) => i.id === id)
  if (s && !form.soilType) form.soilType = s.soilType || ''
}

async function submit() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    const res = await createMyPlant({ ...form })
    ElMessage.success('已添加，养护计划已生成')
    router.push(res?.id ? `/plants/${res.id}` : '/plants')
  } finally {
    submitting.value = false
  }
}

/* ---------------- 自然语言 ---------------- */
const nlText = ref('')
const parsing = ref(false)
const parsed = ref(null)
const examples = ['我新买了一盆龟背竹，放在客厅', '朋友送了一盆绿萝']

async function parse() {
  parsing.value = true
  try {
    parsed.value = await parseNaturalLanguage(nlText.value)
  } finally {
    parsing.value = false
  }
}

async function submitParsed() {
  submitting.value = true
  try {
    await createMyPlant({
      speciesId: parsed.value.parsed.speciesId,
      location: parsed.value.parsed.location || '客厅',
      lightLevel: 'MEDIUM',
      potSize: 'MEDIUM',
      potMaterial: 'PLASTIC',
      acquiredDate: new Date().toISOString().slice(0, 10)
    })
    ElMessage.success('已添加')
    router.push('/plants')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  const data = await getSpeciesList({ page: 1, size: 100 })
  speciesList.value = data.list || []

  // 从植物库详情页跳转过来时预选种类
  const preset = Number(route.query.speciesId)
  if (preset) {
    form.speciesId = preset
    onSpeciesChange(preset)
  }
})
</script>

<style scoped>
.add-form {
  max-width: 620px;
  padding-top: 8px;
}

.opt-emoji {
  margin-right: 6px;
}

.opt-sci {
  float: right;
  font-size: 12px;
  color: var(--gt-text-secondary);
}

.species-preview {
  margin-top: 8px;
  font-size: 12.5px;
  color: var(--gt-text-secondary);
  display: flex;
  align-items: center;
  gap: 8px;
}

.preview-emoji {
  font-size: 20px;
}

.divider-hint {
  font-size: 12px;
  color: var(--gt-text-secondary);
  margin-left: 8px;
  font-weight: 400;
}

.radio-line {
  width: 100%;
}

.radio-label {
  font-size: 14px;
}

.radio-desc {
  font-size: 12px;
  color: var(--gt-text-secondary);
  margin-left: 6px;
}

.pot-row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.nl-wrap {
  max-width: 620px;
  padding-top: 8px;
}

.parse-result {
  margin-top: 20px;
}

.parse-fields {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.parse-field {
  display: flex;
  font-size: 13px;
}

.field-label {
  width: 70px;
  color: var(--gt-text-secondary);
}

.field-value {
  color: var(--gt-text);
}

:deep(.el-radio) {
  height: auto;
  margin-right: 0;
}

:deep(.el-form-item__content) {
  flex-wrap: wrap;
}
</style>
