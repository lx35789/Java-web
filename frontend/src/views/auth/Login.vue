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
          <span>首次使用？直接点「登录」，会自动为你创建账号</span>
        </div>

        <!-- 服务器设置入口：移动端 App 里后端地址可运行时修改，
             避免内网穿透域名变化后必须重新打包 -->
        <div class="server-entry">
          <el-button link size="small" @click="openServerDialog">
            <el-icon><Setting /></el-icon>
            <span style="margin-left: 4px">服务器设置</span>
          </el-button>
          <span class="server-current">{{ apiBaseLabel }}</span>
        </div>

        <el-alert v-if="isMock" type="info" :closable="false" class="demo-tip">
          <template #title>
            <span class="gt-text-small">
              当前为 Mock 模式：任意用户名密码可登录。<br />
              用户名以 <b>admin</b> 开头即可以管理员身份进入
            </span>
          </template>
        </el-alert>
      </div>

      <!-- 服务器地址设置 -->
      <el-dialog v-model="serverDialog" title="服务器设置" width="min(92vw, 460px)">
        <el-form label-position="top">
          <el-form-item label="后端地址（需包含 /api/v1）">
            <el-input
              v-model="serverInput"
              placeholder="http://192.168.1.10:8080/api/v1"
              clearable
            />
          </el-form-item>
        </el-form>
        <div class="server-tip">
          支持两种写法：<br />
          · 局域网：<code>http://192.168.x.x:8080</code><br />
          · 公网穿透：<code>https://xxxx.ngrok-free.app</code><br />
          只填主机时会自动补全 <code>/api/v1</code>。
        </div>
        <template #footer>
          <el-button size="small" @click="serverDialog = false">取消</el-button>
          <el-button size="small" @click="onRestoreServer">恢复默认</el-button>
          <el-button size="small" type="primary" @click="onSaveServer">保存并重载</el-button>
        </template>
      </el-dialog>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { DEFAULT_API_BASE, getApiBase, setApiBase } from '@/utils/serverConfig'
import { USE_MOCK } from '@/api/request'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isMock = USE_MOCK

const formRef = ref(null)
const form = reactive({ username: 'demo', password: 'demo1234' })

/* ---------------- 服务器地址设置 ---------------- */
const serverDialog = ref(false)
const serverInput = ref('')
const apiBaseLabel = ref(getApiBase())

function openServerDialog() {
  serverInput.value = getApiBase()
  serverDialog.value = true
}

function onSaveServer() {
  apiBaseLabel.value = setApiBase(serverInput.value)
  serverDialog.value = false
  ElMessage.success('已保存，正在重载…')
  setTimeout(() => window.location.reload(), 400)
}

function onRestoreServer() {
  setApiBase('')
  apiBaseLabel.value = DEFAULT_API_BASE
  ElMessage.success('已恢复默认地址')
  setTimeout(() => window.location.reload(), 400)
}

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
    // 登录 / 注册一体化：用户名不存在时后端会自动创建账号
    await userStore.loginOrRegister({ ...form })
    ElMessage.success('登录成功')
    router.push(route.query.redirect || '/plants')
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

/* ---------------- 服务器设置 ---------------- */
.server-entry {
  margin-top: 14px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.server-current {
  font-size: 11px;
  color: var(--gt-text-secondary);
  word-break: break-all;
  text-align: center;
  line-height: 1.5;
}

.server-tip {
  font-size: 12px;
  line-height: 1.9;
  color: var(--gt-text-secondary);
  background: #fafbfc;
  border: 1px solid var(--gt-border);
  border-radius: 6px;
  padding: 10px 12px;
}

.server-tip code {
  background: #eef2f0;
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 11.5px;
}

@media (max-width: 900px) {
  .auth-brand {
    display: none;
  }
}
</style>
