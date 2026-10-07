<template>
  <PageHeader title="植物库" desc="50 种常见室内绿植的养护要点，点击查看详细养护规则">
    <template #actions>
      <el-button :icon="Search" @click="load">刷新</el-button>
    </template>
  </PageHeader>

  <!-- 筛选 -->
  <div class="gt-card filter-bar">
    <el-input
      v-model="query.keyword"
      placeholder="搜索中文名 / 英文名 / 学名 / 别名"
      clearable
      style="max-width: 280px"
      @keyup.enter="load"
      @clear="load"
    >
      <template #prefix><el-icon><Search /></el-icon></template>
    </el-input>

    <el-select v-model="query.light" placeholder="光照需求" clearable style="width: 140px" @change="load">
      <el-option v-for="(v, k) in LIGHT_LEVELS" :key="k" :label="v.label" :value="k" />
    </el-select>

    <el-select v-model="query.difficulty" placeholder="养护难度" clearable style="width: 130px" @change="load">
      <el-option v-for="(v, k) in DIFFICULTY" :key="k" :label="v.label" :value="k" />
    </el-select>

    <el-select v-model="query.toxic" placeholder="宠物安全" clearable style="width: 130px" @change="load">
      <el-option label="对宠物有毒" value="true" />
      <el-option label="对宠物无毒" value="false" />
    </el-select>

    <div class="filter-result gt-text-secondary gt-text-small">
      共 {{ total }} 种
    </div>
  </div>

  <div v-loading="loading" class="gt-mt-16">
    <el-row v-if="list.length" :gutter="12">
      <el-col v-for="s in list" :key="s.id" :xs="12" :sm="8" :md="6" :lg="4" class="card-col">
        <SpeciesCard :species="s" @click="goDetail" />
      </el-col>
    </el-row>
    <EmptyState v-else emoji="🔍" title="没有找到匹配的植物" desc="换个关键词，或清空筛选条件试试" />
  </div>

  <div v-if="total > query.size" class="pager">
    <el-pagination
      v-model:current-page="query.page"
      :page-size="query.size"
      :total="total"
      layout="prev, pager, next"
      background
      @current-change="load"
    />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import SpeciesCard from '@/components/SpeciesCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { getSpeciesList } from '@/api/species'
import { DIFFICULTY, LIGHT_LEVELS, DEFAULT_PAGE_SIZE } from '@/utils/constants'

const router = useRouter()
const loading = ref(false)
const list = ref([])
const total = ref(0)

const query = reactive({
  keyword: '',
  light: '',
  difficulty: '',
  toxic: '',
  page: 1,
  size: DEFAULT_PAGE_SIZE
})

async function load() {
  loading.value = true
  try {
    const data = await getSpeciesList({ ...query })
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function goDetail(s) {
  router.push(`/species/${s.id}`)
}

onMounted(load)
</script>

<style scoped>
.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.filter-result {
  margin-left: auto;
}

.card-col {
  margin-bottom: 12px;
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>
