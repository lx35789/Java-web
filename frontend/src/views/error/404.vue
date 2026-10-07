<template>
  <div class="not-found">
    <div class="nf-emoji">🌵</div>
    <h1 class="nf-code">404</h1>
    <p class="nf-title">这盆植物找不到了</p>
    <p class="nf-desc">页面可能已被移走，或者链接不太对</p>
    <div class="nf-actions">
      <el-button type="primary" @click="router.push('/dashboard')">回到看板</el-button>
      <el-button @click="router.back()">返回上一页</el-button>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<style scoped>
.not-found {
  height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 24px;
}

.nf-emoji {
  font-size: 64px;
  line-height: 1;
  margin-bottom: 8px;
}

.nf-code {
  font-size: 52px;
  margin: 0;
  color: var(--gt-primary);
  letter-spacing: 2px;
}

.nf-title {
  font-size: 17px;
  font-weight: 600;
  margin: 8px 0 4px;
}

.nf-desc {
  font-size: 13px;
  color: var(--gt-text-secondary);
  margin: 0 0 24px;
}

.nf-actions {
  display: flex;
  gap: 10px;
}
</style>
