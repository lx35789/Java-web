<template>
  <PageHeader title="养护计划" desc="每个数字都能追溯到来源：种类基准 × 季节 × 光照 × 温度 × 湿度 × 花盆">
    <template #actions>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      <el-button type="primary" :icon="MagicStick" @click="regenerateAll">按当前环境全部重算</el-button>
    </template>
  </PageHeader>

  <div v-loading="loading">
    <template v-if="grouped.length">
      <div v-for="g in grouped" :key="g.userPlantId" class="gt-card plan-group">
        <div class="group-head">
          <span class="group-emoji">{{ g.emoji }}</span>
          <div class="group-info">
            <div class="group-name">{{ g.nickname }}</div>
            <div class="group-sub">{{ g.speciesName }} · {{ g.location || '未设置位置' }}</div>
          </div>
          <el-button text size="small" @click="router.push(`/plants/${g.userPlantId}`)">
            查看植物<el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>

        <el-tabs v-if="g.plans.length" v-model="activeTabs[g.userPlantId]">
          <el-tab-pane
            v-for="p in g.plans"
            :key="p.id"
            :name="String(p.id)"
            :label="`${PLAN_TYPES[p.planType]?.label} ${p.effectiveIntervalDays} 天`"
          >
            <CarePlanExplain :plan="p" />
          </el-tab-pane>
        </el-tabs>
        <EmptyState v-else emoji="📋" title="该植物还没有计划" />
      </div>
    </template>

    <EmptyState v-else emoji="🪴" title="还没有养护计划" desc="先添加一盆植物吧">
      <el-button type="primary" @click="router.push('/plants/new')">添加植物</el-button>
    </EmptyState>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, MagicStick } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import CarePlanExplain from '@/components/CarePlanExplain.vue'
import EmptyState from '@/components/EmptyState.vue'
import { getMyPlants } from '@/api/myPlant'
import { getCarePlans, generateCarePlans } from '@/api/carePlan'
import { PLAN_TYPES } from '@/utils/constants'

const router = useRouter()
const loading = ref(false)
const plants = ref([])
const plans = ref([])
const activeTabs = reactive({})

/** 按植物分组 */
const grouped = computed(() =>
  plants.value
    .map((p) => {
      const list = plans.value.filter((c) => String(c.userPlantId) === String(p.id))
      if (list.length && !activeTabs[p.id]) activeTabs[p.id] = String(list[0].id)
      return {
        userPlantId: p.id,
        nickname: p.nickname || p.speciesName,
        speciesName: p.speciesName,
        emoji: p.speciesEmoji,
        location: p.location,
        plans: list
      }
    })
    .filter((g) => g.plans.length)
)

async function load() {
  loading.value = true
  try {
    const [ps, cs] = await Promise.all([getMyPlants(), getCarePlans()])
    plants.value = ps || []
    plans.value = cs || []
  } finally {
    loading.value = false
  }
}

async function regenerateAll() {
  for (const p of plants.value) {
    await generateCarePlans(p.id)
  }
  ElMessage.success('已按当前季节、天气与环境重新计算')
  load()
}

onMounted(load)
</script>

<style scoped>
.plan-group + .plan-group {
  margin-top: 12px;
}

.group-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px dashed var(--gt-border);
  margin-bottom: 8px;
}

.group-emoji {
  font-size: 28px;
}

.group-info {
  flex: 1;
  min-width: 0;
}

.group-name {
  font-size: 15px;
  font-weight: 600;
}

.group-sub {
  font-size: 12px;
  color: var(--gt-text-secondary);
  margin-top: 2px;
}
</style>
