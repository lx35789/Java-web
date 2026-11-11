/**
 * 侧边栏菜单配置
 * 与 router 的 path 严格对应；icon 为已全局注册的 Element Plus 图标名
 *
 * 第七周调整：
 *   - 去掉「统计看板」「植物库」「AI 问答」——用户不需要这么繁琐的入口
 *   - 「添加植物」并入「我的植物」页面内的按钮，不再单独占菜单
 *   - 「养护日历 / 计划 / 日志」合并为一个「养护中心」（页内 Tab 切换）
 */

export const MENU_GROUPS = [
  {
    title: '我的植物',
    items: [{ path: '/plants', title: '我的植物', icon: 'House' }]
  },
  {
    title: 'AI 能力',
    items: [
      // 「拍照识别」已并入「智能诊断」的第 1 步，不再单独占一个菜单入口。
      { path: '/diagnose', title: '智能诊断', icon: 'FirstAidKit', badge: '核心' }
    ]
  },
  {
    title: '养护',
    items: [
      // 日历 / 计划 / 日志 三个页面合并为一个入口，页内用 Tab 切换
      { path: '/care/calendar', title: '养护中心', icon: 'Calendar' }
    ]
  },
  {
    title: '我的',
    items: [
      { path: '/profile', title: '个人中心', icon: 'User' },
      { path: '/profile/environment', title: '环境设置', icon: 'Setting' }
    ]
  }
]

export const ADMIN_MENU_GROUP = {
  title: '管理端',
  items: [
    { path: '/admin/species', title: '植物库维护', icon: 'Collection' },
    { path: '/admin/diseases', title: '病害信息', icon: 'FirstAidKit' },
    { path: '/admin/symptom-rules', title: '症状规则', icon: 'SetUp', badge: '核心' },
    { path: '/admin/knowledge', title: '知识库', icon: 'Reading' },
    { path: '/admin/stats', title: 'AI 用量统计', icon: 'DataAnalysis' }
  ]
}
