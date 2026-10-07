<template>
  <el-menu
    :default-active="activePath"
    :collapse="collapsed"
    :collapse-transition="false"
    router
    class="side-menu"
    @select="$emit('navigate')"
  >
    <template v-for="group in groups" :key="group.title">
      <div v-if="!collapsed" class="menu-group-title">{{ group.title }}</div>
      <el-divider v-else class="menu-group-divider" />
      <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path">
        <el-icon><component :is="item.icon" /></el-icon>
        <template #title>
          <span class="menu-item-title">{{ item.title }}</span>
          <el-tag v-if="item.badge" size="small" type="success" effect="dark" class="menu-badge">
            {{ item.badge }}
          </el-tag>
        </template>
      </el-menu-item>
    </template>
  </el-menu>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { MENU_GROUPS, ADMIN_MENU_GROUP } from '@/config/menu'
import { useUserStore } from '@/stores/user'

defineProps({
  collapsed: { type: Boolean, default: false }
})
defineEmits(['navigate'])

const route = useRoute()
const userStore = useUserStore()

/** 详情页高亮到它的列表页（通过 meta.activeMenu 指定） */
const activePath = computed(() => route.meta.activeMenu || route.path)

const groups = computed(() => {
  const list = MENU_GROUPS.map((g) => ({ ...g }))
  if (userStore.isAdmin) list.push(ADMIN_MENU_GROUP)
  return list
})
</script>

<style scoped>
.side-menu {
  border-right: none;
  padding-bottom: 24px;
}

.side-menu:not(.el-menu--collapse) {
  width: 100%;
}

.menu-group-title {
  padding: 14px 20px 6px;
  font-size: 11px;
  letter-spacing: 0.5px;
  color: var(--gt-text-secondary);
  font-weight: 600;
}

.menu-group-divider {
  margin: 8px 12px;
}

.menu-item-title {
  flex: 1;
}

.menu-badge {
  transform: scale(0.8);
  transform-origin: right center;
}

:deep(.el-menu-item) {
  height: 42px;
  line-height: 42px;
  margin: 2px 8px;
  border-radius: 6px;
}

:deep(.el-menu-item.is-active) {
  background: var(--gt-primary-light);
  color: var(--gt-primary-dark);
  font-weight: 600;
}

:deep(.el-menu-item:hover) {
  background: #f2f6f4;
}
</style>
