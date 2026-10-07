<template>
  <PageHeader title="诊断记录" desc="症状诊断与病害识别的历史记录">
    <template #actions>
      <el-button type="primary" :icon="Warning" @click="router.push('/diagnose/symptom')">新建诊断</el-button>
    </template>
  </PageHeader>

  <div class="gt-card">
    <div class="filter-bar">
      <el-radio-group v-model="query.diagnosisType" @change="load">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="SYMPTOM">症状诊断</el-radio-button>
        <el-radio-button value="DISEASE">病害识别</el-radio-button>
      </el-radio-group>
    </div>

    <el-table v-loading="loading" :data="list" class="gt-mt-16" stripe>
      <el-table-column label="类型" width="110">
        <template #default="{ row }">
          <el-tag size="small" effect="plain" :type="DIAGNOSIS_TYPE[row.diagnosisType]?.type">
            {{ DIAGNOSIS_TYPE[row.diagnosisType]?.label }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="plantNickname" label="植物" width="120" />
      <el-table-column prop="summary" label="诊断结论" min-width="200" />

      <el-table-column label="置信度" width="110">
        <template #default="{ row }">
          <ConfidenceTag :confidence="row.confidence" />
        </template>
      </el-table-column>

      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="DIAGNOSIS_STATUS[row.status]?.type" effect="plain">
            {{ DIAGNOSIS_STATUS[row.status]?.label }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>

      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button text size="small" @click="viewDetail(row)">详情</el-button>
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
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import ConfidenceTag from '@/components/ConfidenceTag.vue'
import { getDiagnoses } from '@/api/diagnosis'
import { formatDateTime } from '@/utils/format'
import { DIAGNOSIS_TYPE, DIAGNOSIS_STATUS, DEFAULT_PAGE_SIZE } from '@/utils/constants'

const router = useRouter()
const loading = ref(false)
const list = ref([])
const total = ref(0)

const query = reactive({ diagnosisType: '', page: 1, size: DEFAULT_PAGE_SIZE })

async function load() {
  loading.value = true
  try {
    const data = await getDiagnoses({ ...query })
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function viewDetail(row) {
  // 病害结论跳到向导第 2 步，症状结论跳到第 3 步
  router.push(row.diagnosisType === 'DISEASE' ? '/diagnose?step=2' : '/diagnose?step=3')
}

onMounted(load)
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
