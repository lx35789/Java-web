<template>
  <PageHeader title="养护日志" desc="浇水、施肥、换盆、修剪的完整记录">
    <template #actions>
      <el-button type="primary" :icon="Plus" @click="openAdd">补录一条</el-button>
    </template>
  </PageHeader>

  <!-- 概览 -->
  <el-row :gutter="12" class="gt-mb-16">
    <el-col :xs="12" :sm="6">
      <StatCard label="累计记录" :value="total" unit="条" icon="Document" color="#2F7D5D" />
    </el-col>
    <el-col v-for="(v, k) in LOG_TYPES" :key="k" :xs="12" :sm="6" :md="4">
      <StatCard :label="v.label" :value="countByType(k)" unit="次" icon="CircleCheck" :color="v.color" />
    </el-col>
  </el-row>

  <div class="gt-card">
    <!-- 筛选 -->
    <div class="filter-bar">
      <el-select v-model="query.userPlantId" placeholder="全部植物" clearable style="width: 160px" @change="load">
        <el-option v-for="p in plants" :key="p.id" :label="p.nickname || p.speciesName" :value="p.id" />
      </el-select>

      <el-select v-model="query.logType" placeholder="全部类型" clearable style="width: 130px" @change="load">
        <el-option v-for="(v, k) in LOG_TYPES" :key="k" :label="v.label" :value="k" />
      </el-select>

      <el-date-picker
        v-model="dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        style="width: 260px"
        @change="onDateChange"
      />
    </div>

    <el-table v-loading="loading" :data="list" class="gt-mt-16" stripe>
      <el-table-column label="时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.loggedAt) }}</template>
      </el-table-column>

      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag size="small" effect="plain" :style="{ color: LOG_TYPES[row.logType]?.color, borderColor: LOG_TYPES[row.logType]?.color }">
            {{ LOG_TYPES[row.logType]?.label }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="植物" width="140">
        <template #default="{ row }">{{ row.plantNickname || row.speciesName }}</template>
      </el-table-column>

      <el-table-column label="用量" width="110">
        <template #default="{ row }">{{ row.amount ? `${row.amount} ${row.unit || ''}` : '—' }}</template>
      </el-table-column>

      <el-table-column prop="detail" label="备注" min-width="160" show-overflow-tooltip />

      <el-table-column label="来源" width="100">
        <template #default="{ row }">
          <el-tag size="small" effect="plain" :type="row.taskId ? 'success' : 'info'">
            {{ row.taskId ? '计划内' : '补录' }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button text size="small" type="danger" @click="remove(row)">删除</el-button>
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

  <!-- 补录对话框 -->
  <el-dialog v-model="addVisible" title="补录养护记录" width="480px">
    <el-form :model="addForm" label-width="80px">
      <el-form-item label="植物" required>
        <el-select v-model="addForm.userPlantId" placeholder="选择植物" style="width: 100%">
          <el-option v-for="p in plants" :key="p.id" :label="`${p.speciesEmoji} ${p.nickname || p.speciesName}`" :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="类型" required>
        <el-select v-model="addForm.logType" style="width: 100%">
          <el-option v-for="(v, k) in LOG_TYPES" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item label="用量">
        <el-input v-model="addForm.amount" placeholder="比如 200">
          <template #append>{{ unitOf(addForm.logType) }}</template>
        </el-input>
      </el-form-item>
      <el-form-item label="时间">
        <el-date-picker
          v-model="addForm.loggedAt"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="addForm.detail" type="textarea" :rows="2" placeholder="比如：土表干了约 2cm" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="addVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import { getMyPlants } from '@/api/myPlant'
import { getCareLogs, createCareLog, deleteCareLog } from '@/api/careLog'
import { formatDateTime } from '@/utils/format'
import { LOG_TYPES, DEFAULT_PAGE_SIZE } from '@/utils/constants'
import dayjs from 'dayjs'

const loading = ref(false)
const saving = ref(false)
const list = ref([])
const total = ref(0)
const plants = ref([])
const dateRange = ref(null)

const query = reactive({ userPlantId: null, logType: '', from: '', to: '', page: 1, size: DEFAULT_PAGE_SIZE })

/** 本页数据里的类型计数（真实场景应由后端统计接口返回） */
const countByType = (type) => list.value.filter((l) => l.logType === type).length

const unitOf = (type) => ({ WATER: 'ml', FERTILIZE: 'g' }[type] || '')

async function load() {
  loading.value = true
  try {
    const data = await getCareLogs({ ...query })
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function onDateChange(v) {
  query.from = v?.[0] || ''
  query.to = v?.[1] || ''
  query.page = 1
  load()
}

/* ---------------- 补录 ---------------- */
const addVisible = ref(false)
const addForm = reactive({
  userPlantId: null,
  logType: 'WATER',
  amount: '',
  detail: '',
  loggedAt: dayjs().format('YYYY-MM-DDTHH:mm:ss')
})

function openAdd() {
  addVisible.value = true
}

async function save() {
  if (!addForm.userPlantId) {
    ElMessage.warning('请选择植物')
    return
  }
  saving.value = true
  try {
    await createCareLog({ ...addForm, amount: addForm.amount ? Number(addForm.amount) : null })
    ElMessage.success('已记录')
    addVisible.value = false
    addForm.amount = ''
    addForm.detail = ''
    load()
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm('确定删除这条记录吗？', '提示', { type: 'warning' })
  } catch {
    return
  }
  await deleteCareLog(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(async () => {
  plants.value = (await getMyPlants()) || []
  load()
})
</script>

<style scoped>
.filter-bar {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>
