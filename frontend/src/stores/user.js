import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as authApi from '@/api/auth'
import { getToken, setToken, removeToken, getCachedUser, setCachedUser } from '@/utils/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref(getToken())
  const profile = ref(getCachedUser() || {})
  const loading = ref(false)

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => profile.value?.role === 'ADMIN')
  const displayName = computed(() => profile.value?.nickname || profile.value?.username || '未登录')

  function applyAuth(data) {
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
    token.value = ''
    profile.value = {}
    removeToken()
  }

  function reset() {
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
    register,
    fetchProfile,
    updateProfile,
    logout,
    reset
  }
})
