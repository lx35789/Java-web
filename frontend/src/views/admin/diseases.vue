<template>
  <PageHeader title="病害信息" desc="病害类别与处理建议。注意：处理建议来自这张表，不来自模型输出" />

  <el-alert type="warning" :closable="false" show-icon class="gt-mb-16">
    <template #title>
      <span class="gt-text-small">
        模型只负责分类，可能出现误判；但处理建议必须准确。所以建议内容全部人工维护、可审核、可追溯。
      </span>
    </template>
  </el-alert>

  <div class="gt-card">
    <div class="filter-bar">
      <el-radio-group v-model="query.category" @change="load">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button v-for="(v, k) in DISEASE_CATEGORY" :key="k" :value="k">
          {{ v.label }}
        </el-radio-button>
      </el-radio-group>
    </div>

    <el-table v-loading="loading" :data="list" class="gt-mt-16" stripe>
      <el-table-column prop="nameCn" label="病害名称" width="150" />

      <el-table-column label="类别" width="110">
        <template #default="{ row }">
          <el-tag
            size="small"
            effect="plain"
            :style="{ color: DISEASE_CATEGORY[row.category]?.color, borderColor: DISEASE_CATEGORY[row.category]?.color }"
          >
            {{ DISEASE_CATEGORY[row.category]?.label }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="code" label="类别码" width="180">
        <template #default="{ row }">
          <code class="code-cell">{{ row.code }}</code>
        </template>
      </el-table-column>

      <el-table-column prop="applicableSpecies" label="适用物种" min-width="160" />

      <el-table-column label="严重度" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.severity === 'HIGH' ? 'danger' : row.severity === 'MEDIUM' ? 'warning' : 'info'" effect="plain">
            {{ { HIGH: '高', MEDIUM: '中', LOW: '低' }[row.severity] }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="更新时间" width="120">
        <template #default="{ row }">{{ row.updatedAt }}</template>
      </el-table-column>

      <el-table-column label="操作" width="120" fixed="right">
        <template #default>
          <el-button text size="small">编辑建议</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { adminGetDiseases } from '@/api/admin'
import { DISEASE_CATEGORY, DEFAULT_PAGE_SIZE } from '@/utils/constants'

const loading = ref(false)
const list = ref([])
const query = reactive({ category: '', page: 1, size: DEFAULT_PAGE_SIZE })

async function load() {
  loading.value = true
  try {
    const data = await adminGetDiseases({ ...query })
    list.value = data.list || []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.filter-bar {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.code-cell {
  font-size: 12px;
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 4px;
  color: var(--gt-text-regular);
}
</style>
