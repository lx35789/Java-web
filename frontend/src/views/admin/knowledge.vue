<template>
  <PageHeader title="知识库" desc="RAG 检索的语料。每条都记录来源与许可协议，回答时会标注引用">
    <template #actions>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      <el-button type="primary" :icon="Plus">导入文章</el-button>
    </template>
  </PageHeader>

  <el-alert type="success" :closable="false" show-icon class="gt-mb-16">
    <template #title>
      <span class="gt-text-small">
        建议以<b>自撰养护卡片</b>为主：50 种植物每种 300–500 字，共约 2 万字。
        结构化程度和针对性远超爬来的网页，而且没有版权风险。RHS / Gardening Know How 等版权内容不整段入库。
      </span>
    </template>
  </el-alert>

  <el-row :gutter="12" class="gt-mb-16">
    <el-col :xs="12" :sm="6">
      <StatCard label="文章总数" :value="total" unit="篇" icon="Document" color="#2F7D5D" />
    </el-col>
    <el-col :xs="12" :sm="6">
      <StatCard label="已向量化" :value="embeddedCount" unit="篇" icon="CircleCheck" color="#67C23A" />
    </el-col>
    <el-col :xs="12" :sm="6">
      <StatCard label="待处理" :value="pendingCount" unit="篇" icon="Clock" :color="pendingCount ? '#E6A23C' : '#909399'" />
    </el-col>
    <el-col :xs="12" :sm="6">
      <StatCard label="分块总数" :value="chunkTotal" unit="块" icon="Grid" color="#409EFF" />
    </el-col>
  </el-row>

  <div class="gt-card">
    <div class="filter-bar">
      <el-select v-model="query.source" placeholder="全部来源" clearable style="width: 180px" @change="load">
        <el-option v-for="(v, k) in KNOWLEDGE_SOURCE" :key="k" :label="v.label" :value="k" />
      </el-select>

      <el-select v-model="query.embedStatus" placeholder="全部状态" clearable style="width: 150px" @change="load">
        <el-option label="已向量化" value="DONE" />
        <el-option label="待处理" value="PENDING" />
        <el-option label="失败" value="FAILED" />
      </el-select>
    </div>

    <el-table v-loading="loading" :data="list" class="gt-mt-16" stripe>
      <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />

      <el-table-column label="来源" width="140">
        <template #default="{ row }">
          <el-tag
            size="small"
            effect="plain"
            :style="{ color: KNOWLEDGE_SOURCE[row.source]?.color, borderColor: KNOWLEDGE_SOURCE[row.source]?.color }"
          >
            {{ KNOWLEDGE_SOURCE[row.source]?.label || row.source }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="许可协议" width="150">
        <template #default="{ row }">
          <span class="license-cell" :class="{ 'license-warn': row.license !== '自有' }">
            {{ row.license }}
          </span>
        </template>
      </el-table-column>

      <el-table-column prop="category" label="分类" width="100" />
      <el-table-column prop="language" label="语言" width="80" />

      <el-table-column label="分块" width="80" align="center">
        <template #default="{ row }">{{ row.chunkCount }}</template>
      </el-table-column>

      <el-table-column label="向量化" width="110">
        <template #default="{ row }">
          <el-tag
            size="small"
            effect="plain"
            :type="{ DONE: 'success', PENDING: 'warning', FAILED: 'danger' }[row.embedStatus]"
          >
            {{ { DONE: '已完成', PENDING: '待处理', FAILED: '失败' }[row.embedStatus] }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="更新时间" width="120">
        <template #default="{ row }">{{ row.updatedAt }}</template>
      </el-table-column>

      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button text size="small" @click="reindex(row)">重建索引</el-button>
          <el-button text size="small" type="danger">删除</el-button>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Plus } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import { adminGetArticles, adminReindexArticle } from '@/api/admin'
import { KNOWLEDGE_SOURCE, DEFAULT_PAGE_SIZE } from '@/utils/constants'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ source: '', embedStatus: '', page: 1, size: DEFAULT_PAGE_SIZE })

const embeddedCount = computed(() => list.value.filter((a) => a.embedStatus === 'DONE').length)
const pendingCount = computed(() => list.value.filter((a) => a.embedStatus === 'PENDING').length)
const chunkTotal = computed(() => list.value.reduce((s, a) => s + (a.chunkCount || 0), 0))

async function load() {
  loading.value = true
  try {
    const data = await adminGetArticles({ ...query })
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

async function reindex(row) {
  await adminReindexArticle(row.id)
  ElMessage.success(`已重新分块并向量化：${row.title}`)
  load()
}

onMounted(load)
</script>

<style scoped>
.filter-bar {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.license-cell {
  font-size: 12px;
  color: var(--gt-text-secondary);
}

.license-warn {
  color: var(--gt-warning);
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>
