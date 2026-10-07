<template>
  <PageHeader title="植物库维护" desc="50 种室内绿植的属性与养护规则，会直接决定养护计划的基准值">
    <template #actions>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      <el-button type="primary" :icon="Plus">新增种类</el-button>
    </template>
  </PageHeader>

  <div class="gt-card">
    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column label="" width="54">
        <template #default="{ row }">
          <span class="emoji-cell">{{ row.emoji }}</span>
        </template>
      </el-table-column>

      <el-table-column prop="commonName" label="中文名" width="110" />
      <el-table-column prop="scientificName" label="学名" min-width="180" />

      <el-table-column label="难度" width="110">
        <template #default="{ row }">
          <el-tag size="small" effect="plain" :type="DIFF_TYPE[row.difficulty]">
            {{ DIFFICULTY[row.difficulty]?.label }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="浇水基准" width="110" align="center">
        <template #default="{ row }">{{ row.baseWaterIntervalDays }} 天</template>
      </el-table-column>

      <el-table-column label="光照" width="130">
        <template #default="{ row }">{{ LIGHT_LEVELS[row.lightRequirement]?.label }}</template>
      </el-table-column>

      <el-table-column label="毒性" width="100">
        <template #default="{ row }">
          <el-tag size="small" effect="plain" :type="row.toxicity === 'TOXIC' ? 'danger' : 'success'">
            {{ row.toxicity === 'TOXIC' ? '有毒' : '无毒' }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="识别来源" width="130">
        <template #default="{ row }">
          <el-tag size="small" effect="plain" :type="row.recognitionProvider === 'SELF_MODEL' ? 'success' : 'warning'">
            {{ row.recognitionProvider === 'SELF_MODEL' ? '自研模型' : 'Pl@ntNet' }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="模型类别号" width="110" align="center">
        <template #default="{ row }">
          <span v-if="row.modelClassIndex !== null">{{ row.modelClassIndex }}</span>
          <span v-else class="gt-text-secondary">—</span>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button text size="small" @click="edit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="query.page"
        :page-size="query.size"
        :total="total"
        layout="prev, pager, next"
        background
        @current-change="load"
      />
    </div>
  </div>

  <el-dialog v-model="editVisible" :title="`编辑：${editForm.commonName}`" width="600px">
    <el-form :model="editForm" label-width="130px">
      <el-form-item label="中文名">
        <el-input v-model="editForm.commonName" />
      </el-form-item>
      <el-form-item label="浇水基准间隔">
        <el-input-number v-model="editForm.baseWaterIntervalDays" :min="1" :max="90" />
        <span class="form-hint">天（会再乘各环境系数）</span>
      </el-form-item>
      <el-form-item label="单次水量">
        <el-input-number v-model="editForm.waterAmountMl" :min="0" :max="2000" :step="50" />
        <span class="form-hint">ml</span>
      </el-form-item>
      <el-form-item label="光照需求">
        <el-select v-model="editForm.lightRequirement" style="width: 100%">
          <el-option v-for="(v, k) in LIGHT_LEVELS" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item label="施肥间隔">
        <el-input-number v-model="editForm.fertilizerIntervalDays" :min="7" :max="180" />
        <span class="form-hint">天</span>
      </el-form-item>
      <el-form-item label="毒性">
        <el-radio-group v-model="editForm.toxicity">
          <el-radio value="NONE">无毒</el-radio>
          <el-radio value="TOXIC">有毒</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="模型类别索引">
        <el-input-number v-model="editForm.modelClassIndex" :min="0" :max="99" />
        <span class="form-hint">留空表示自研模型未覆盖，走 Pl@ntNet</span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="editVisible = false">取消</el-button>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Plus } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { adminGetSpecies, adminUpdateSpecies } from '@/api/admin'
import { DIFFICULTY, LIGHT_LEVELS, DEFAULT_PAGE_SIZE } from '@/utils/constants'

const DIFF_TYPE = { EASY: 'success', MEDIUM: 'warning', HARD: 'danger' }

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: DEFAULT_PAGE_SIZE })

const editVisible = ref(false)
const editForm = reactive({})

async function load() {
  loading.value = true
  try {
    const data = await adminGetSpecies({ ...query })
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function edit(row) {
  Object.assign(editForm, JSON.parse(JSON.stringify(row)))
  editVisible.value = true
}

async function save() {
  await adminUpdateSpecies(editForm.id, { ...editForm })
  ElMessage.success('已保存')
  editVisible.value = false
  load()
}

onMounted(load)
</script>

<style scoped>
.emoji-cell {
  font-size: 20px;
}

.form-hint {
  font-size: 12px;
  color: var(--gt-text-secondary);
  margin-left: 10px;
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>
