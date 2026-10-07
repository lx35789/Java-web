<template>
  <PageHeader title="我的植物" :desc="`共 ${plants.length} 盆，其中 ${warningCount} 盆需要关注`">
    <template #actions>
      <el-button :icon="Camera" @click="router.push('/recognize')">拍照添加</el-button>
      <el-button type="primary" :icon="Plus" @click="router.push('/plants/new')">添加植物</el-button>
    </template>
  </PageHeader>

  <div class="gt-card filter-bar">
    <el-radio-group v-model="filter" size="default" @change="applyFilter">
      <el-radio-button value="">全部</el-radio-button>
      <el-radio-button v-for="(v, k) in HEALTH_STATUS" :key="k" :value="k">
        {{ v.label }}
      </el-radio-button>
    </el-radio-group>
  </div>

  <div v-loading="loading" class="gt-mt-16">
    <el-row v-if="filtered.length" :gutter="12">
      <el-col v-for="p in filtered" :key="p.id" :xs="12" :sm="8" :md="6" class="card-col">
        <PlantCard :plant="p" @click="goDetail" />
      </el-col>
    </el-row>
    <EmptyState
      v-else
      emoji="🪴"
      title="还没有添加植物"
      desc="拍照识别或手动添加，系统会自动生成养护计划"
    >
      <el-button type="primary" :icon="Plus" @click="router.push('/plants/new')">添加第一盆植物</el-button>
    </EmptyState>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Camera } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import PlantCard from '@/components/PlantCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { getMyPlants } from '@/api/myPlant'
import { HEALTH_STATUS } from '@/utils/constants'

const router = useRouter()
const loading = ref(false)
const plants = ref([])
const filter = ref('')

const filtered = computed(() => (filter.value ? plants.value.filter((p) => p.healthStatus === filter.value) : plants.value))

const warningCount = computed(
  () => plants.value.filter((p) => p.healthStatus === 'WARNING' || p.healthStatus === 'DISEASED').length
)

async function load() {
  loading.value = true
  try {
    plants.value = (await getMyPlants()) || []
  } finally {
    loading.value = false
  }
}

const applyFilter = () => {}

const goDetail = (p) => router.push(`/plants/${p.id}`)

onMounted(load)
</script>

<style scoped>
.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.card-col {
  margin-bottom: 12px;
}
</style>
