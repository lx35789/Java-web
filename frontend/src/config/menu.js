/**
 * 侧边栏菜单配置
 * 与 router 的 path 严格对应；icon 为已全局注册的 Element Plus 图标名
 */

export const MENU_GROUPS = [
  {
    title: '概览',
    items: [{ path: '/dashboard', title: '统计看板', icon: 'Odometer' }]
  },
  {
    title: '我的植物',
    items: [
      { path: '/plants', title: '我的植物', icon: 'House' },
      { path: '/species', title: '植物库', icon: 'Collection' },
      { path: '/plants/new', title: '添加植物', icon: 'Plus' }
    ]
  },
  {
    title: 'AI 能力',
    items: [
      { path: '/recognize', title: '拍照识别', icon: 'Camera' },
      { path: '/diagnose', title: '智能诊断', icon: 'FirstAidKit', badge: '核心' },
      { path: '/qa', title: 'AI 问答', icon: 'ChatDotRound' }
    ]
  },
  {
    title: '养护',
    items: [
      { path: '/care/calendar', title: '养护日历', icon: 'Calendar' },
      { path: '/care/plans', title: '养护计划', icon: 'Clock' },
      { path: '/care/logs', title: '养护日志', icon: 'Document' }
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
