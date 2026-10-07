<template>
  <el-tag :type="level.type" :effect="effect" size="small" round>
    {{ showLabel ? level.label + ' ' : '' }}{{ toPercent(confidence) }}
  </el-tag>
</template>

<script setup>
import { computed } from 'vue'
import { toPercent, confidenceLevel } from '@/utils/format'

const props = defineProps({
  confidence: { type: [Number, String], default: 0 },
  effect: { type: String, default: 'light' },
  showLabel: { type: Boolean, default: false }
})

const level = computed(() => confidenceLevel(props.confidence))
</script>
