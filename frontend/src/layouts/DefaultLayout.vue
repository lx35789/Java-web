<template>
  <el-container class="layout">
    <!-- ============ 侧边栏（桌面端） ============ -->
    <el-aside
      v-if="!isMobile"
      class="layout-aside"
      :width="appStore.sidebarCollapsed ? 'var(--gt-sidebar-collapsed-width)' : 'var(--gt-sidebar-width)'"
    >
      <div class="brand" :class="{ 'brand--collapsed': appStore.sidebarCollapsed }">
        <span class="brand-logo">🌿</span>
        <span v-show="!appStore.sidebarCollapsed" class="brand-text">
          <b>植语助手</b>
          <em>GreenThumb AI</em>
        </span>
      </div>
      <SideMenu :collapsed="appStore.sidebarCollapsed" />
    </el-aside>

    <!-- ============ 侧边栏（移动端抽屉） ============ -->
    <el-drawer
      v-if="isMobile"
      v-model="appStore.mobileMenuOpen"
      direction="ltr"
      :with-header="false"
      size="240px"
      body-class="drawer-body"
    >
      <div class="brand">
        <span class="brand-logo">🌿</span>
        <span class="brand-text"><b>植语助手</b><em>GreenThumb AI</em></span>
      </div>
      <SideMenu @navigate="appStore.toggleMobileMenu(false)" />
    </el-drawer>

    <el-container class="layout-body">
      <!-- ============ 顶栏 ============ -->
      <el-header class="layout-header">
        <div class="gt-flex-center gt-gap-12">
          <el-button text class="collapse-btn" @click="onToggle">
            <el-icon :size="18"><Fold v-if="!appStore.sidebarCollapsed" /><Expand v-else /></el-icon>
          </el-button>
          <div class="header-title">
            <span class="header-title-main">{{ route.meta.title || '植语助手' }}</span>
            <el-tag v-if="isMock" size="small" type="warning" effect="plain" class="gt-hide-mobile">Mock 模式</el-tag>
          </div>
        </div>

        <div class="gt-flex-center gt-gap-12">
          <el-tooltip content="去提问" placement="bottom">
            <el-button circle text @click="router.push('/qa')">
              <el-icon :size="18"><ChatDotRound /></el-icon>
            </el-button>
          </el-tooltip>

          <el-dropdown trigger="click" @command="onUserCommand">
            <span class="user-trigger">
              <el-avatar :size="30" class="user-avatar">{{ avatarText }}</el-avatar>
              <span class="user-name gt-hide-mobile">{{ userStore.displayName }}</span>
              <el-icon class="gt-hide-mobile"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon>个人中心
                </el-dropdown-item>
                <el-dropdown-item command="environment">
                  <el-icon><Setting /></el-icon>环境设置
                </el-dropdown-item>
                <el-dropdown-item v-if="userStore.isAdmin" command="admin" divided>
                  <el-icon><Tools /></el-icon>管理端
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- ============ 内容区 ============ -->
      <el-main class="layout-main">
        <!--
          ⚠️ 这里外层必须套一个带 key 的 div，不能直接包 <component>。
          原因：<transition> 要求子节点是「单个元素」，而页面组件普遍是多根节点
          （PageHeader + 若干 el-row/el-col）。直接包 <component> 时 Vue 无法
          解析过渡目标，配合 mode="out-in" 会导致新页面完全不渲染 —— 表现为白板。
          套一层 div 后，div 成为唯一子节点，过渡正常工作。
        -->
        <router-view v-slot="{ Component, route: currentRoute }">
          <transition name="fade-slide" mode="out-in">
            <div :key="currentRoute.path" class="route-view">
              <component :is="Component" />
            </div>
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { USE_MOCK } from '@/api/request'
import SideMenu from '@/components/SideMenu.vue'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

const isMock = USE_MOCK
const avatarText = computed(() => (userStore.displayName || '?').slice(0, 1))

/* ---------- 响应式：移动端用抽屉 ---------- */
const isMobile = ref(false)
const updateIsMobile = () => {
  isMobile.value = window.innerWidth < 768
  if (!isMobile.value) appStore.toggleMobileMenu(false)
}
onMounted(() => {
  updateIsMobile()
  window.addEventListener('resize', updateIsMobile)
})
onUnmounted(() => window.removeEventListener('resize', updateIsMobile))

function onToggle() {
  if (isMobile.value) appStore.toggleMobileMenu()
  else appStore.toggleSidebar()
}

async function onUserCommand(cmd) {
  if (cmd === 'profile') router.push('/profile')
  else if (cmd === 'environment') router.push('/profile/environment')
  else if (cmd === 'admin') router.push('/admin/species')
  else if (cmd === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' })
    } catch {
      return
    }
    await userStore.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  }
}
</script>

<style scoped>
.layout {
  height: 100vh;
}

/* ---------------- 侧边栏 ---------------- */
.layout-aside {
  background: #ffffff;
  border-right: 1px solid var(--gt-border);
  transition: width 0.22s ease;
  overflow-x: hidden;
}

.brand {
  height: var(--gt-header-height);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  border-bottom: 1px solid var(--gt-border);
  white-space: nowrap;
}

.brand--collapsed {
  padding: 0 0 0 18px;
}

.brand-logo {
  font-size: 22px;
  line-height: 1;
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.brand-text b {
  font-size: 15px;
  color: var(--gt-primary-dark);
}

.brand-text em {
  font-size: 11px;
  font-style: normal;
  color: var(--gt-text-secondary);
}

/* ---------------- 顶栏 ---------------- */
.layout-header {
  height: var(--gt-header-height);
  background: #ffffff;
  border-bottom: 1px solid var(--gt-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
}

.collapse-btn {
  padding: 6px;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-title-main {
  font-size: 16px;
  font-weight: 600;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  outline: none;
}

.user-avatar {
  background: var(--gt-primary);
  color: #fff;
  font-size: 13px;
}

.user-name {
  font-size: 14px;
  color: var(--gt-text-regular);
}

/* ---------------- 内容区 ---------------- */
.layout-main {
  background: var(--gt-bg);
  padding: 0;
  overflow-y: auto;
}

:deep(.drawer-body) {
  padding: 0;
}

/* 路由过渡 */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}
.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(6px);
}
.fade-slide-leave-to {
  opacity: 0;
}
</style>
