<template>
  <div class="auth-page">
    <!-- 左侧品牌区 -->
    <div class="auth-brand">
      <div class="brand-inner">
        <div class="brand-logo">🌿</div>
        <h1 class="brand-name">植语助手</h1>
        <p class="brand-sub">GreenThumb AI · 室内绿植养护助手</p>

        <ul class="brand-points">
          <li>
            <b>认得准</b>
            <span>拍照给 Top-3 结果，承认不确定性，由你确认</span>
          </li>
          <li>
            <b>说得清</b>
            <span>每个浇水建议都能拆解出系数依据</span>
          </li>
          <li>
            <b>记得住</b>
            <span>计划 → 提醒 → 日志，沉淀你的养护历史</span>
          </li>
        </ul>
      </div>
    </div>

    <!-- 右侧表单 -->
    <div class="auth-form-wrap">
      <div class="auth-form">
        <h2 class="form-title">欢迎回来</h2>
        <p class="form-sub">登录后开始管理你的植物</p>

        <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent="onSubmit">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="用户名" clearable>
              <template #prefix><el-icon><User /></el-icon></template>
            </el-input>
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码"
              show-password
              @keyup.enter="onSubmit"
            >
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>

          <el-button type="primary" class="submit-btn" :loading="userStore.loading" @click="onSubmit">
            登录
          </el-button>
        </el-form>

        <div class="form-footer">
          <span>还没有账号？</span>
          <router-link to="/register" class="gt-link">立即注册</router-link>
        </div>

        <el-alert type="info" :closable="false" class="demo-tip">
          <template #title>
            <span class="gt-text-small">
              骨架阶段已开启 Mock：任意用户名密码可登录。<br />
              用户名以 <b>admin</b> 开头即可以管理员身份进入（试试 <b>admin</b> / <b>123456</b>）
            </span>
          </template>
        </el-alert>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref(null)
const form = reactive({ username: 'demo', password: '123456' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function onSubmit() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  try {
    await userStore.login({ ...form })
    ElMessage.success('登录成功')
    router.push(route.query.redirect || '/dashboard')
  } catch {
    /* 错误提示已在响应拦截器统一处理 */
  }
}
</script>

<style scoped>
.auth-page {
  display: flex;
  height: 100vh;
}

.auth-brand {
  flex: 1.1;
  background: linear-gradient(150deg, #2f7d5d 0%, #24614a 55%, #1c4e3c 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  position: relative;
  overflow: hidden;
}

.auth-brand::after {
  content: '';
  position: absolute;
  width: 460px;
  height: 460px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.05);
  right: -140px;
  bottom: -160px;
}

.brand-inner {
  max-width: 380px;
  position: relative;
  z-index: 1;
}

.brand-logo {
  font-size: 52px;
  line-height: 1;
}

.brand-name {
  font-size: 30px;
  margin: 14px 0 6px;
  letter-spacing: 2px;
}

.brand-sub {
  font-size: 13px;
  opacity: 0.75;
  margin: 0 0 32px;
}

.brand-points {
  list-style: none;
  padding: 0;
  margin: 0;
}

.brand-points li {
  padding: 12px 0 12px 16px;
  border-left: 2px solid rgba(255, 255, 255, 0.28);
  margin-bottom: 4px;
}

.brand-points b {
  display: block;
  font-size: 14px;
  margin-bottom: 3px;
}

.brand-points span {
  font-size: 12.5px;
  opacity: 0.78;
  line-height: 1.6;
}

.auth-form-wrap {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
  background: #fff;
}

.auth-form {
  width: 100%;
  max-width: 340px;
}

.form-title {
  font-size: 24px;
  margin: 0 0 6px;
}

.form-sub {
  font-size: 13px;
  color: var(--gt-text-secondary);
  margin: 0 0 26px;
}

.submit-btn {
  width: 100%;
  margin-top: 4px;
}

.form-footer {
  margin-top: 18px;
  text-align: center;
  font-size: 13px;
  color: var(--gt-text-secondary);
}

.demo-tip {
  margin-top: 24px;
}

@media (max-width: 900px) {
  .auth-brand {
    display: none;
  }
}
</style>
