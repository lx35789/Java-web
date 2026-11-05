import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as authApi from '@/api/auth'
import { clearRequestCache } from '@/api/request'
import { getToken, setToken, removeToken, getCachedUser, setCachedUser } from '@/utils/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref(getToken())
  const profile = ref(getCachedUser() || {})
  const loading = ref(false)

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => profile.value?.role === 'ADMIN')
  const displayName = computed(() => profile.value?.nickname || profile.value?.username || '未登录')

  function applyAuth(data) {
    // 换用户后必须丢掉上一个账号的缓存数据
    clearRequestCache()
    token.value = data.token
    setToken(data.token)
    profile.value = data.user || {}
    setCachedUser(profile.value)
  }

  async function login(payload) {
    loading.value = true
    try {
      const data = await authApi.login(payload)
      applyAuth(data)
      return data
    } finally {
      loading.value = false
    }
  }

  /**
   * 登录注册一体化：没注册过的用户名会直接创建并登录。
   * 移动端登录页只调这一个方法，用户不用先判断自己有没有账号。
   */
  async function loginOrRegister(payload) {
    loading.value = true
    try {
      const data = await authApi.loginOrRegister(payload)
      applyAuth(data)
      return data
    } finally {
      loading.value = false
    }
  }

  async function register(payload) {
    loading.value = true
    try {
      const data = await authApi.register(payload)
      applyAuth(data)
      return data
    } finally {
      loading.value = false
    }
  }

  async function fetchProfile() {
    const data = await authApi.getProfile()
    profile.value = data
    setCachedUser(data)
    return data
  }

  async function updateProfile(payload) {
    const data = await authApi.updateProfile(payload)
    profile.value = { ...profile.value, ...data }
    setCachedUser(profile.value)
    return data
  }

  async function logout() {
    try {
      await authApi.logout()
    } catch {
      /* 登出失败不阻塞本地清理 */
    }
    clearRequestCache()
    token.value = ''
    profile.value = {}
    removeToken()
  }

  function reset() {
    clearRequestCache()
    token.value = ''
    profile.value = {}
    removeToken()
  }

  return {
    token,
    profile,
    loading,
    isLoggedIn,
    isAdmin,
    displayName,
    login,
    loginOrRegister,
    register,
    fetchProfile,
    updateProfile,
    logout,
    reset
  }
})
