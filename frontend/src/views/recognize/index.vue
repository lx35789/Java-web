<template>
  <PageHeader title="拍照识别" desc="上传叶片或整株照片，返回最可能的 3 个种类。识别结果由你确认，你也可以纠正它">
    <template #actions>
      <el-button text :icon="Clock" @click="router.push('/diagnose/records')">识别历史</el-button>
    </template>
  </PageHeader>

  <el-row :gutter="12">
    <el-col :xs="24" :md="10">
      <div class="gt-card">
        <h3 class="gt-section-title">上传照片</h3>
        <ImageUploader
          v-model="file"
          capture="environment"
          hint="建议拍完整叶片或整株，光线充足、主体居中"
        />

        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="gt-mt-16"
          title="拍摄建议"
          description="避免逆光和阴影；一次只拍一株；叶片占画面 1/2 以上识别更准。"
        />

        <div class="gt-mt-16">
          <el-button
            type="primary"
            size="large"
            style="width: 100%"
            :loading="loading"
            :disabled="!file"
            @click="doRecognize"
          >
            {{ loading ? '识别中…' : '开始识别' }}
          </el-button>
        </div>

        <div class="gt-text-secondary gt-text-small gt-mt-8">
          骨架阶段为 Mock 数据，不会真正上传。接入后端后将调用自研模型，低置信度时自动兜底到 Pl@ntNet。
        </div>
      </div>
    </el-col>

    <el-col :xs="24" :md="14">
      <div class="gt-card">
        <div class="gt-flex-between gt-mb-16">
          <h3 class="gt-section-title" style="margin: 0">识别结果</h3>
          <div v-if="result" class="gt-flex-center gt-gap-8">
            <el-tag size="small" effect="plain" :type="providerMeta.type">{{ providerMeta.label }}</el-tag>
            <el-tag size="small" effect="plain" type="info">{{ result.modelVersion }}</el-tag>
            <el-tag size="small" effect="plain" type="info">{{ formatLatency(result.latencyMs) }}</el-tag>
          </div>
        </div>

        <!-- 未识别 -->
        <EmptyState
          v-if="!result"
          emoji="📷"
          title="还没有识别结果"
          desc="先在左侧上传一张照片，然后点击「开始识别」"
        />

        <!-- 低置信度提示 -->
        <el-alert
          v-else-if="result.lowConfidence"
          type="warning"
          :closable="false"
          show-icon
          class="gt-mb-16"
          title="识别置信度较低"
          description="模型没能给出足够确定的结果。你可以换一张更清晰的照片，或直接从植物库里手动选择。"
        />

        <template v-else>
          <div v-for="r in result.results" :key="r.rank" class="result-item" :class="{ top1: r.rank === 1 }">
            <div class="result-rank">
              <span class="rank-badge" :class="`rank-${r.rank}`">{{ r.rank }}</span>
            </div>
            <div class="result-cover">{{ r.emoji || '🌿' }}</div>
            <div class="result-main">
              <div class="result-name">
                {{ r.commonName }}
                <ConfidenceTag :confidence="r.confidence" class="gt-ml-8" />
              </div>
              <div class="result-sci">{{ r.scientificName }}</div>
              <div v-if="r.careSummary" class="result-care">{{ r.careSummary }}</div>
            </div>
            <div class="result-action">
              <el-button type="primary" :plain="r.rank !== 1" size="small" @click="adopt(r)">
                就是它
              </el-button>
            </div>
          </div>

          <div class="result-footer">
            <el-button text :icon="Refresh" @click="reRecognize">识别不准，再试一次</el-button>
            <el-button text :icon="Collection" @click="router.push('/species')">去植物库手动选择</el-button>
          </div>

          <!-- ★ 说明数据闭环 -->
          <el-alert type="success" :closable="false" show-icon class="gt-mt-16">
            <template #title>
              <span class="gt-text-small">
                你选择的种类会被记录。如果和模型判断不一致，这条数据会用于统计真实场景准确率
                （管理端「AI 用量统计」可查看），并作为模型迭代的标注来源。
              </span>
            </template>
          </el-alert>
        </template>
      </div>
    </el-col>
  </el-row>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Clock, Refresh, Collection } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import ImageUploader from '@/components/ImageUploader.vue'
import ConfidenceTag from '@/components/ConfidenceTag.vue'
import EmptyState from '@/components/EmptyState.vue'
import { uploadPhoto } from '@/api/photo'
import { recognizeSpecies, confirmRecognition } from '@/api/recognition'
import { formatLatency } from '@/utils/format'
import { RECOGNITION_PROVIDER } from '@/utils/constants'
import { USE_MOCK } from '@/api/request'

const router = useRouter()
const file = ref(null)
const loading = ref(false)
const result = ref(null)

const providerMeta = computed(
  () => RECOGNITION_PROVIDER[result.value?.provider] || { label: '未知来源', type: 'info' }
)

async function doRecognize() {
  if (!file.value) return
  loading.value = true
  try {
    // 真实流程：先上传拿 photoId，再请求识别
    const photo = await uploadPhoto(file.value, { photoType: 'LEAF' })
    result.value = await recognizeSpecies(photo.id)
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

function reRecognize() {
  result.value = null
  file.value = null
}

async function adopt(r) {
  try {
    await ElMessageBox.confirm(
      `将「${r.commonName}」加入我的植物，并自动生成养护计划？`,
      '确认添加',
      { confirmButtonText: '加入我的植物', cancelButtonText: '再看看' }
    )
  } catch {
    return
  }
  await confirmRecognition(result.value.recordId, {
    speciesId: r.speciesId,
    createPlant: true,
    userPlantForm: { location: '客厅', lightLevel: 'MEDIUM' }
  })
  ElMessage.success('已加入我的植物，养护计划已生成')
  router.push('/plants')
}

// 骨架阶段不提示未使用变量
void USE_MOCK
</script>

<style scoped>
.result-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px;
  border: 1px solid var(--gt-border);
  border-radius: var(--gt-radius);
  transition: all 0.18s ease;
}

.result-item + .result-item {
  margin-top: 10px;
}

.result-item.top1 {
  border-color: var(--gt-primary);
  background: #fbfdfc;
}

.result-cover {
  width: 56px;
  height: 56px;
  flex: 0 0 56px;
  border-radius: 10px;
  background: linear-gradient(135deg, #eef6f1 0%, #e2f0e8 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
}

.rank-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  font-size: 12px;
  font-weight: 600;
  background: var(--gt-border);
  color: var(--gt-text-secondary);
}

.rank-badge.rank-1 {
  background: var(--gt-primary);
  color: #fff;
}

.result-main {
  flex: 1;
  min-width: 0;
}

.result-name {
  font-size: 15px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}

.result-sci {
  font-size: 12px;
  color: var(--gt-text-secondary);
  margin-top: 2px;
}

.result-care {
  font-size: 12.5px;
  color: var(--gt-text-regular);
  margin-top: 6px;
}

.result-action {
  flex: 0 0 auto;
}

.result-footer {
  display: flex;
  justify-content: center;
  gap: 4px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px dashed var(--gt-border);
}

.gt-ml-8 {
  margin-left: 8px;
}

@media (max-width: 768px) {
  .result-item {
    flex-wrap: wrap;
  }
  .result-action {
    width: 100%;
  }
  .result-action .el-button {
    width: 100%;
  }
}
</style>
