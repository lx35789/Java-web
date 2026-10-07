<template>
  <div class="auth-page">
    <div class="auth-brand">
      <div class="brand-inner">
        <div class="brand-logo">🌿</div>
        <h1 class="brand-name">植语助手</h1>
        <p class="brand-sub">GreenThumb AI · 室内绿植养护助手</p>
        <ul class="brand-points">
          <li><b>50 种常见室内绿植</b><span>绿萝、龟背竹、多肉、虎皮兰、发财树…</span></li>
          <li><b>个性化养护计划</b><span>按种类、季节、光照、花盆、天气生成浇水频率</span></li>
          <li><b>AI 诊断与问答</b><span>叶子黄了怎么办？给出依据而不只是结论</span></li>
        </ul>
      </div>
    </div>

    <div class="auth-form-wrap">
      <div class="auth-form">
        <h2 class="form-title">创建账号</h2>
        <p class="form-sub">开始记录你的第一盆植物</p>

        <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent="onSubmit">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="用户名" clearable>
              <template #prefix><el-icon><User /></el-icon></template>
            </el-input>
          </el-form-item>

          <el-form-item prop="nickname">
            <el-input v-model="form.nickname" placeholder="昵称（可选）" clearable>
              <template #prefix><el-icon><Avatar /></el-icon></template>
            </el-input>
          </el-form-item>

          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" placeholder="密码" show-password>
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>

          <el-form-item prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              placeholder="确认密码"
              show-password
              @keyup.enter="onSubmit"
            >
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>

          <el-button type="primary" class="submit-btn" :loading="userStore.loading" @click="onSubmit">
            注册
          </el-button>
        </el-form>

        <div class="form-footer">
          <span>已有账号？</span>
          <router-link to="/login" class="gt-link">返回登录</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref(null)

const form = reactive({ username: '', nickname: '', password: '', confirmPassword: '' })

const validateConfirm = (rule, value, callback) => {
  if (value !== form.password) callback(new Error('两次输入的密码不一致'))
  else callback()
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '长度 3–20 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '至少 6 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' }
  ]
}

async function onSubmit() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  try {
    await userStore.register({ username: form.username, nickname: form.nickname, password: form.password })
    ElMessage.success('注册成功')
    router.push('/dashboard')
  } catch {
    /* 拦截器已提示 */
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
}

.brand-inner {
  max-width: 380px;
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

@media (max-width: 900px) {
  .auth-brand {
    display: none;
  }
}
</style>
