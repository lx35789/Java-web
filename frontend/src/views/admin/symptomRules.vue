<template>
  <PageHeader title="症状规则" desc="症状判因的核心。权重可调，命中哪几条规则会直接展示给用户" >
    <template #actions>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      <el-button type="primary" :icon="Plus">新增规则</el-button>
    </template>
  </PageHeader>

  <el-alert type="success" :closable="false" show-icon class="gt-mb-16">
    <template #title>
      <span class="gt-text-small">
        这套规则是「症状诊断」模块的判因依据，替代了让大模型直接看图猜原因的做法。
        好处是：<b>可解释</b>（能列出命中哪几条）、<b>可测试</b>（每条规则可写单元测试）、<b>零成本零延迟</b>。
      </span>
    </template>
  </el-alert>

  <div class="gt-card">
    <!-- 筛选 -->
    <div class="filter-bar">
      <el-select v-model="query.symptomCode" placeholder="全部症状" clearable style="width: 180px" @change="load">
        <el-option v-for="s in SYMPTOMS" :key="s.code" :label="s.label" :value="s.code" />
      </el-select>

      <el-select v-model="query.causeCode" placeholder="全部病因" clearable style="width: 200px" @change="load">
        <el-option
          v-for="c in causeOptions"
          :key="c.code"
          :label="`${c.name}（${c.code}）`"
          :value="c.code"
        />
      </el-select>

      <div class="gt-text-secondary gt-text-small">共 {{ list.length }} 条规则</div>
    </div>

    <el-table v-loading="loading" :data="list" class="gt-mt-16" stripe>
      <el-table-column label="症状" width="150">
        <template #default="{ row }">
          <el-tag size="small" effect="plain" type="primary">
            {{ symptomLabel(row.symptomCode) }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="病因" width="180">
        <template #default="{ row }">
          <div class="cause-cell">
            <span>{{ row.causeName }}</span>
            <code class="code-cell">{{ row.causeCode }}</code>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="触发条件" min-width="240">
        <template #default="{ row }">
          <div class="cond-list">
            <el-tag
              v-for="(v, k) in row.condition"
              :key="k"
              size="small"
              effect="plain"
              type="info"
              class="cond-tag"
            >
              {{ k }} {{ typeof v === 'object' ? `${v.op} ${v.value}` : `= ${v}` }}
            </el-tag>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="权重" width="130">
        <template #default="{ row }">
          <el-input-number
            v-model="row.weight"
            :min="0"
            :max="1"
            :step="0.05"
            :precision="2"
            size="small"
            controls-position="right"
            style="width: 100px"
            @change="() => markDirty(row)"
          />
        </template>
      </el-table-column>

      <el-table-column label="启用" width="80">
        <template #default="{ row }">
          <el-switch v-model="row.enabled" size="small" @change="() => markDirty(row)" />
        </template>
      </el-table-column>

      <el-table-column label="可解释模板" min-width="240" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="explain-cell">{{ row.explainTpl }}</span>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button text size="small" :disabled="!dirty.has(row.id)" @click="save(row)">保存</el-button>
          <el-button text size="small" type="danger">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>

  <!-- 算法说明 -->
  <div class="gt-card gt-mt-16">
    <h3 class="gt-section-title">判因算法</h3>
    <div class="algo">
      <div class="algo-step">
        <span class="step-num">1</span>
        <div>
          <b>匹配规则</b>
          <div class="gt-text-secondary">对用户勾选的每个症状，找出所有条件被满足的规则（多字段为 AND）</div>
        </div>
      </div>
      <div class="algo-step">
        <span class="step-num">2</span>
        <div>
          <b>权重累加</b>
          <div class="gt-text-secondary">指向同一病因的规则权重相加，得到该病因的原始得分</div>
        </div>
      </div>
      <div class="algo-step">
        <span class="step-num">3</span>
        <div>
          <b>归一化取 Top-3</b>
          <div class="gt-text-secondary">得分 ÷ 总得分 = 置信度，取前 3 且丢弃低于 0.15 的</div>
        </div>
      </div>
      <div class="algo-step">
        <span class="step-num">4</span>
        <div>
          <b>置信度降级</b>
          <div class="gt-text-secondary">
            最高置信度 &lt; 0.4 时不下结论，改为引导用户补充信息——避免武断
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Plus } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { adminGetSymptomRules, adminUpdateSymptomRule } from '@/api/admin'
import { SYMPTOMS } from '@/utils/constants'

const loading = ref(false)
const list = ref([])
const query = reactive({ symptomCode: '', causeCode: '' })
const dirty = ref(new Set())

const symptomLabel = (code) => SYMPTOMS.find((s) => s.code === code)?.label || code

const causeOptions = computed(() => {
  const map = new Map()
  for (const r of list.value) {
    if (!map.has(r.causeCode)) map.set(r.causeCode, { code: r.causeCode, name: r.causeName })
  }
  return [...map.values()]
})

async function load() {
  loading.value = true
  try {
    list.value = (await adminGetSymptomRules({ ...query })) || []
    dirty.value = new Set()
  } finally {
    loading.value = false
  }
}

function markDirty(row) {
  dirty.value = new Set(dirty.value).add(row.id)
}

async function save(row) {
  await adminUpdateSymptomRule(row.id, { weight: row.weight, enabled: row.enabled })
  ElMessage.success('已保存，下次判因立即生效')
  const next = new Set(dirty.value)
  next.delete(row.id)
  dirty.value = next
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

.cause-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.code-cell {
  font-size: 11px;
  color: var(--gt-text-secondary);
  background: #f5f7fa;
  padding: 1px 5px;
  border-radius: 3px;
  width: fit-content;
}

.cond-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.cond-tag {
  font-family: ui-monospace, Menlo, Consolas, monospace;
  font-size: 11px;
}

.explain-cell {
  font-size: 12.5px;
  color: var(--gt-text-regular);
}

.algo {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.algo-step {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  font-size: 13px;
}

.step-num {
  flex: 0 0 22px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--gt-primary-light);
  color: var(--gt-primary-dark);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
}
</style>
