<template>
  <div class="care-nav">
    <nav class="care-nav-inner">
      <RouterLink
        v-for="item in items"
        :key="item.path"
        :to="item.path"
        class="care-nav-item"
        :class="{ active: isActive(item.path) }"
      >
        <span class="care-nav-icon" v-html="item.icon" />
        <span class="care-nav-text">{{ item.label }}</span>
      </RouterLink>
    </nav>
  </div>
</template>

<script setup>
/**
 * 养护模块的子导航。
 *
 * 从「一个页面内 Tab 切换」改成「三个独立路由」的原因：
 *   - 每个页面有自己的标题、操作按钮、筛选状态，塞进一个壳里越用越别扭
 *   - 独立路由才能被收藏、被分享、被浏览器前进/后退正确识别
 *   - 移动端返回键的行为才符合预期（Tab 切换不产生历史记录，返回会直接退出）
 *
 * 样式用 pills 而不是 el-radio-group：路由跳转用 <a> 语义更正确
 * （可中键新开标签页、可被爬虫识别），也避免和页面内的单选框混淆。
 */
import { useRoute } from 'vue-router'

const route = useRoute()

const items = [
  { path: '/care/calendar', label: '养护日历', icon: '📅' },
  { path: '/care/plans', label: '养护计划', icon: '📋' },
  { path: '/care/logs', label: '养护日志', icon: '📝' }
]

function isActive(path) {
  return route.path === path
}
</script>

<style scoped>
.care-nav {
  margin-bottom: 14px;
}

.care-nav-inner {
  display: inline-flex;
  gap: 6px;
  padding: 4px;
  background: #f3f6f4;
  border-radius: 10px;
}

.care-nav-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 14px;
  color: var(--gt-text-regular);
  text-decoration: none;
  transition: background 0.16s ease, color 0.16s ease;
  white-space: nowrap;
}

.care-nav-item:hover {
  background: #e7eeea;
  color: var(--gt-primary-dark);
}

.care-nav-item.active {
  background: #fff;
  color: var(--gt-primary-dark);
  font-weight: 600;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.care-nav-icon {
  font-size: 15px;
  line-height: 1;
}

@media (max-width: 768px) {
  .care-nav-inner {
    display: flex;
    width: 100%;
  }

  .care-nav-item {
    flex: 1;
    justify-content: center;
    padding: 9px 8px;
    font-size: 13px;
  }
}
</style>
