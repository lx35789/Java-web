import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  /** 侧边栏折叠状态（桌面端） */
  const sidebarCollapsed = ref(false)
  /** 移动端抽屉开关 */
  const mobileMenuOpen = ref(false)
  /** 全局页面 loading */
  const pageLoading = ref(false)

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function toggleMobileMenu(open) {
    mobileMenuOpen.value = typeof open === 'boolean' ? open : !mobileMenuOpen.value
  }

  return { sidebarCollapsed, mobileMenuOpen, pageLoading, toggleSidebar, toggleMobileMenu }
})
