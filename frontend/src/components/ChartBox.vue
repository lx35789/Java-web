<template>
  <div ref="elRef" class="chart-box" :style="{ height }" />
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  option: { type: Object, required: true },
  height: { type: String, default: '280px' }
})

const elRef = ref(null)
let chart = null
let observer = null

function render() {
  if (!elRef.value) return
  if (!chart) chart = echarts.init(elRef.value)
  chart.setOption(props.option, true)
}

onMounted(async () => {
  await nextTick()
  render()
  // 侧边栏折叠 / 窗口缩放时自适应
  if (typeof ResizeObserver !== 'undefined' && elRef.value) {
    observer = new ResizeObserver(() => chart && chart.resize())
    observer.observe(elRef.value)
  }
})

watch(() => props.option, render, { deep: true })

onBeforeUnmount(() => {
  observer?.disconnect()
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.chart-box {
  width: 100%;
}
</style>
