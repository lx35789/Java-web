<template>
  <div class="care-center">
    <!-- 三个子页面各自带标题和操作按钮，这里只放一个轻量的 Tab 切换条，避免标题重复 -->
    <div class="care-tabbar">
      <el-radio-group v-model="tab" size="default" @change="onTabChange">
        <el-radio-button value="calendar">养护日历</el-radio-button>
        <el-radio-button value="plans">养护计划</el-radio-button>
        <el-radio-button value="logs">养护日志</el-radio-button>
      </el-radio-group>
    </div>

    <!-- lazy：只有切到该 Tab 才挂载，避免三个页面同时发请求 -->
    <CareCalendar v-if="loaded.calendar" v-show="tab === 'calendar'" />
    <CarePlans v-if="loaded.plans" v-show="tab === 'plans'" />
    <CareLogs v-if="loaded.logs" v-show="tab === 'logs'" />
  </div>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CareCalendar from './calendar.vue'
import CarePlans from './plans.vue'
import CareLogs from './logs.vue'

const route = useRoute()
const router = useRouter()

const VALID = ['calendar', 'plans', 'logs']
const tab = ref(VALID.includes(route.query.tab) ? route.query.tab : 'calendar')

/** 记录哪些 Tab 已挂载过，切走再回来不重新挂载（保留筛选条件与滚动位置） */
const loaded = reactive({ calendar: false, plans: false, logs: false })
loaded[tab.value] = true

function onTabChange(name) {
  loaded[name] = true
  // 当前 Tab 写进 URL：刷新后还停在原处，也方便从别处直接跳转
  if (route.query.tab !== name) {
    router.replace({ path: '/care', query: { ...route.query, tab: name } })
  }
}

// 支持从外部（首页提醒、植物详情等）跳转时指定 Tab
watch(
  () => route.query.tab,
  (v) => {
    if (VALID.includes(v) && v !== tab.value) {
      tab.value = v
      loaded[v] = true
    }
  }
)
</script>

<style scoped>
.care-center {
  padding-bottom: 24px;
}

.care-tabbar {
  display: flex;
  justify-content: flex-end;
  margin: 0 0 10px;
}
</style>
