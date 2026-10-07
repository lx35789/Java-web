<template>
  <PageHeader title="个人中心" desc="账号信息与偏好设置" />

  <el-row :gutter="12">
    <el-col :xs="24" :md="8">
      <div class="gt-card profile-card">
        <el-avatar :size="72" class="big-avatar">{{ avatarText }}</el-avatar>
        <div class="profile-name">{{ userStore.displayName }}</div>
        <div class="profile-username">@{{ profile.username }}</div>
        <el-tag :type="userStore.isAdmin ? 'warning' : 'info'" effect="plain" class="gt-mt-8">
          {{ userStore.isAdmin ? '管理员' : '普通用户' }}
        </el-tag>

        <el-divider />

        <div class="info-row">
          <span class="gt-text-secondary">邮箱</span>
          <span>{{ profile.email || '未设置' }}</span>
        </div>
        <div class="info-row">
          <span class="gt-text-secondary">注册时间</span>
          <span>{{ formatDate(profile.createdAt) }}</span>
        </div>

        <el-button class="gt-mt-16" style="width: 100%" :icon="SwitchButton" @click="onLogout">
          退出登录
        </el-button>
      </div>
    </el-col>

    <el-col :xs="24" :md="16">
      <!-- 基本资料 -->
      <div class="gt-card">
        <h3 class="gt-section-title">基本资料</h3>
        <el-form :model="form" label-width="90px" style="max-width: 420px">
          <el-form-item label="昵称">
            <el-input v-model="form.nickname" maxlength="20" show-word-limit />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="form.email" placeholder="用于找回密码" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="save">保存</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 修改密码 -->
      <div class="gt-card gt-mt-16">
        <h3 class="gt-section-title">修改密码</h3>
        <el-form ref="pwdRef" :model="pwdForm" :rules="pwdRules" label-width="90px" style="max-width: 420px">
          <el-form-item label="当前密码" prop="oldPassword">
            <el-input v-model="pwdForm.oldPassword" type="password" show-password />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input v-model="pwdForm.newPassword" type="password" show-password />
          </el-form-item>
          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input v-model="pwdForm.confirmPassword" type="password" show-password />
          </el-form-item>
          <el-form-item>
            <el-button :loading="changing" @click="changePwd">更新密码</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 数据概览 -->
      <div class="gt-card gt-mt-16">
        <div class="gt-flex-between gt-mb-16">
          <h3 class="gt-section-title" style="margin: 0">我的数据</h3>
          <el-button text size="small" @click="router.push('/profile/environment')">
            环境设置<el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
        <el-row :gutter="12">
          <el-col :xs="12" :sm="6">
            <StatCard label="植物" :value="stats.plantCount" unit="盆" icon="House" color="#2F7D5D" />
          </el-col>
          <el-col :xs="12" :sm="6">
            <StatCard label="养护记录" :value="stats.careStats.waterCountTotal" unit="次" icon="Document" color="#409EFF" />
          </el-col>
          <el-col :xs="12" :sm="6">
            <StatCard label="诊断" :value="stats.diagnosisStats.total" unit="次" icon="FirstAidKit" color="#E6A23C" />
          </el-col>
          <el-col :xs="12" :sm="6">
            <StatCard label="提问" :value="stats.qaCount" unit="次" icon="ChatDotRound" color="#67C23A" />
          </el-col>
        </el-row>
      </div>
    </el-col>
  </el-row>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { SwitchButton, ArrowRight } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import { useUserStore } from '@/stores/user'
import { getStatsOverview } from '@/api/stats'
import { changePassword } from '@/api/auth'
import { formatDate } from '@/utils/format'

const router = useRouter()
const userStore = useUserStore()

const profile = computed(() => userStore.profile || {})
const avatarText = computed(() => (userStore.displayName || '?').slice(0, 1))

const saving = ref(false)
const changing = ref(false)
const form = reactive({ nickname: '', email: '' })
const pwdRef = ref(null)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const stats = ref({
  plantCount: 0,
  careStats: { waterCountTotal: 0 },
  diagnosisStats: { total: 0 },
  qaCount: 0
})

const validateConfirm = (rule, value, callback) => {
  if (value !== pwdForm.newPassword) callback(new Error('两次输入的密码不一致'))
  else callback()
}

const pwdRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '至少 6 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' }
  ]
}

async function save() {
  saving.value = true
  try {
    await userStore.updateProfile({ ...form })
    ElMessage.success('已保存')
  } finally {
    saving.value = false
  }
}

async function changePwd() {
  try {
    await pwdRef.value?.validate()
  } catch {
    return
  }
  changing.value = true
  try {
    await changePassword({ ...pwdForm })
    ElMessage.success('密码已更新')
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirmPassword = ''
  } finally {
    changing.value = false
  }
}

async function onLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' })
  } catch {
    return
  }
  await userStore.logout()
  router.push('/login')
}

onMounted(async () => {
  await userStore.fetchProfile().catch(() => {})
  Object.assign(form, { nickname: profile.value.nickname, email: profile.value.email })
  stats.value = await getStatsOverview()
})
</script>

<style scoped>
.profile-card {
  text-align: center;
}

.big-avatar {
  background: var(--gt-primary);
  color: #fff;
  font-size: 28px;
  margin-bottom: 12px;
}

.profile-name {
  font-size: 18px;
  font-weight: 600;
}

.profile-username {
  font-size: 13px;
  color: var(--gt-text-secondary);
  margin-top: 3px;
}

.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  padding: 6px 0;
}
</style>
