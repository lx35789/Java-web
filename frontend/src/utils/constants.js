/**
 * 全局枚举与字典
 * 与后端 API / 数据库枚举一一对应，禁止在页面里硬编码字符串
 * 参考：docs/02-项目重设计方案.md、docs/03-症状诊断规则库.md、docs/04-API接口设计.md
 */

/* ---------------- 通用 ---------------- */
export const SEASONS = {
  SPRING: { label: '春', color: '#67C23A' },
  SUMMER: { label: '夏', color: '#E6A23C' },
  AUTUMN: { label: '秋', color: '#B88230' },
  WINTER: { label: '冬', color: '#409EFF' }
}

/* ---------------- 植物属性 ---------------- */
export const DIFFICULTY = {
  EASY: { label: '新手友好', color: '#67C23A' },
  MEDIUM: { label: '一般', color: '#E6A23C' },
  HARD: { label: '需要经验', color: '#F56C6C' }
}

export const LIGHT_LEVELS = {
  DIRECT: { label: '直射光', desc: '南向窗台、无遮挡', coef: 0.85 },
  BRIGHT_INDIRECT: { label: '明亮散射光', desc: '靠窗但不直晒', coef: 1.0 },
  MEDIUM: { label: '中等光照', desc: '离窗 1–2 米', coef: 1.15 },
  LOW: { label: '弱光', desc: '远离窗户、北向房间', coef: 1.4 }
}

export const HUMIDITY_LEVELS = {
  LOW: { label: '干燥' },
  MEDIUM: { label: '适中' },
  HIGH: { label: '潮湿' }
}

export const TOXICITY = {
  NONE: { label: '无毒', type: 'success' },
  TOXIC: { label: '有毒', type: 'danger' }
}

export const TOXIC_TARGETS = {
  cat: '猫',
  dog: '狗',
  human: '人'
}

export const POT_MATERIALS = {
  TERRACOTTA: { label: '陶盆', desc: '透气，水分蒸发快', coef: 0.9 },
  PLASTIC: { label: '塑料盆', desc: '保水中等', coef: 1.0 },
  CERAMIC: { label: '陶瓷盆', desc: '釉面不透气，易积水', coef: 1.1 }
}

export const POT_SIZES = {
  SMALL: { label: '小盆', desc: '口径 < 15cm' },
  MEDIUM: { label: '中盆', desc: '口径 15–25cm' },
  LARGE: { label: '大盆', desc: '口径 > 25cm' }
}

/* ---------------- 我的植物 ---------------- */
export const HEALTH_STATUS = {
  HEALTHY: { label: '健康', color: '#67C23A', type: 'success' },
  WARNING: { label: '需关注', color: '#E6A23C', type: 'warning' },
  DISEASED: { label: '有病状', color: '#F56C6C', type: 'danger' },
  DEAD: { label: '已枯萎', color: '#909399', type: 'info' }
}

export const ADD_SOURCE = {
  MANUAL: { label: '手动添加' },
  RECOGNITION: { label: '拍照识别' },
  NATURAL_LANGUAGE: { label: '自然语言' }
}

/* ---------------- 养护计划 / 任务 / 日志 ---------------- */
export const PLAN_TYPES = {
  WATER: { label: '浇水', color: '#409EFF', unit: 'ml' },
  FERTILIZE: { label: '施肥', color: '#67C23A', unit: 'g' },
  REPOT: { label: '换盆', color: '#E6A23C', unit: null },
  PRUNE: { label: '修剪', color: '#909399', unit: null }
}

export const TASK_STATUS = {
  PENDING: { label: '待完成', type: 'primary' },
  DONE: { label: '已完成', type: 'success' },
  SKIPPED: { label: '已跳过', type: 'info' },
  OVERDUE: { label: '已逾期', type: 'danger' }
}

export const LOG_TYPES = {
  WATER: { label: '浇水', color: '#409EFF' },
  FERTILIZE: { label: '施肥', color: '#67C23A' },
  REPOT: { label: '换盆', color: '#E6A23C' },
  PRUNE: { label: '修剪', color: '#909399' },
  PEST: { label: '除虫', color: '#F56C6C' }
}

export const GENERATED_BY = {
  RULE: { label: '规则引擎', type: 'info' },
  LLM: { label: 'AI 生成', type: 'success' },
  MANUAL: { label: '手动设置', type: 'warning' }
}

/* ---------------- 识别 ---------------- */
/**
 * 识别供应商
 * 后端通过 RecognitionOrchestrator 做降级链（百度 → Pl@ntNet → 自研模型），
 * 具体用哪家由后端决定，前端只负责展示 provider 标签。
 * 详见 docs/05-识别服务接入方案.md
 */
export const RECOGNITION_PROVIDER = {
  BAIDU: { label: '百度植物识别', type: 'primary' },
  PLANTNET: { label: 'Pl@ntNet', type: 'warning' },
  SELF_MODEL: { label: '自研模型', type: 'success' },
  KINDWISE: { label: 'Plant.id', type: 'info' }
}

/** 置信度分档 —— 用于统一的颜色/文案表达 */
export const CONFIDENCE_LEVELS = [
  { min: 0.8, label: '高', type: 'success' },
  { min: 0.5, label: '中', type: 'warning' },
  { min: 0, label: '低', type: 'danger' }
]

/* ---------------- 病害 / 症状 ---------------- */
export const DIAGNOSIS_TYPE = {
  DISEASE: { label: '病害', desc: '病理性（真菌/细菌/虫害）', type: 'danger' },
  SYMPTOM: { label: '症状', desc: '生理性（养护问题）', type: 'warning' }
}

export const DISEASE_CATEGORY = {
  FUNGAL: { label: '真菌性', color: '#B88230' },
  BACTERIAL: { label: '细菌性', color: '#F56C6C' },
  PEST: { label: '虫害', color: '#E6A23C' },
  PHYSIOLOGICAL: { label: '生理性', color: '#909399' }
}

export const DIAGNOSIS_STATUS = {
  OPEN: { label: '待处理', type: 'danger' },
  TREATING: { label: '处理中', type: 'warning' },
  RESOLVED: { label: '已恢复', type: 'success' }
}

/**
 * 症状标签 —— 用户勾选项
 * leafPositionRequired 为 true 的症状，UI 必须让用户选择"从老叶还是新叶开始"，
 * 这一项能把候选病因从 10 个收缩到 2–3 个（见 docs/03）
 */
export const SYMPTOMS = [
  { code: 'YELLOW_LEAF', label: '叶片发黄', hint: '叶子变黄了', leafPositionRequired: true, priority: 1 },
  { code: 'LEAF_TIP_BURN', label: '叶尖/叶缘焦枯', hint: '叶子边缘发黄发焦', leafPositionRequired: false, priority: 2 },
  { code: 'LEAF_DROP', label: '落叶', hint: '叶子老是掉', leafPositionRequired: false, priority: 3 },
  { code: 'WILT', label: '萎蔫发软', hint: '叶子耷拉、软塌塌', leafPositionRequired: false, priority: 4 },
  { code: 'SPOT', label: '叶片斑点', hint: '叶子上有斑块', leafPositionRequired: false, priority: 5, preferDiseaseModel: true },
  { code: 'LEGGY', label: '徒长', hint: '茎又细又长、叶子稀疏', leafPositionRequired: false, priority: 6 },
  { code: 'PALE', label: '叶色变淡/发白', hint: '叶子颜色变浅了', leafPositionRequired: false, priority: 7 },
  { code: 'CURL', label: '叶片卷曲', hint: '叶子卷起来了', leafPositionRequired: false, priority: 8 },
  { code: 'NO_GROWTH', label: '长期不长', hint: '几个月没变化', leafPositionRequired: false, priority: 9 },
  { code: 'BASE_ROT', label: '茎基发黑发软', hint: '根茎部变黑腐烂', leafPositionRequired: false, priority: 10, urgencyHint: 'HIGH' }
]

/** 叶片位置 —— YELLOW_LEAF 必选项 */
export const LEAF_POSITIONS = [
  { value: 'OLD', label: '下部老叶先黄', desc: '多指向浇水过多、正常代谢' },
  { value: 'NEW', label: '上部新叶先黄', desc: '多指向光照不足、缺肥、肥害' },
  { value: 'UNIFORM', label: '全株均匀发黄', desc: '多指向光照不足、缺肥' },
  { value: 'ANY', label: '说不清', desc: '判因置信度会降低' }
]

/** 叶脉特征 —— 可选，缺铁/缺氮的强特征 */
export const VEIN_PATTERNS = [
  { value: 'GREEN_VEIN', label: '叶脉仍是绿的，只有叶肉发黄' },
  { value: 'NONE', label: '没有这个特征 / 不确定' }
]

export const CAUSE_CATEGORIES = {
  WATER: { label: '水分', color: '#409EFF' },
  LIGHT: { label: '光照', color: '#E6A23C' },
  NUTRIENT: { label: '养分', color: '#67C23A' },
  ENV: { label: '环境', color: '#909399' },
  PEST: { label: '虫害', color: '#F56C6C' },
  NORMAL: { label: '正常现象', color: '#B1B3B8' }
}

/**
 * 病害识别覆盖的物种
 *
 * 公开的室内植物病害数据集只覆盖这几类（见 docs/02 第 3.2 节）。
 * 不在列表里的植物，病害模型没有对应训练数据，硬跑只会输出无意义结果，
 * 因此智能诊断的第 2 步会直接跳过并说明原因。
 *
 * 生产环境应改为 plant_species 表上的 disease_covered 字段，由后端下发，
 * 这样新增物种时不用改前端。
 */
export const DISEASE_COVERED_SPECIES = ['绿萝', '虎皮兰', '芦荟', '吊兰', '仙人掌', '多肉', '玉露']

export const URGENCY = {
  LOW: { label: '不急', type: 'info' },
  MEDIUM: { label: '建议处理', type: 'warning' },
  HIGH: { label: '尽快处理', type: 'danger' }
}

/** 判因置信度分档 —— 决定 UI 是给结论还是引导补充信息 */
export const DIAGNOSIS_CONFIDENCE = {
  HIGH: { label: '较明确', type: 'success', min: 0.6 },
  MEDIUM: { label: '可能', type: 'warning', min: 0.4 },
  LOW: { label: '信息不足', type: 'info', min: 0 }
}

/* ---------------- 知识库 / 问答 ---------------- */
export const KNOWLEDGE_SOURCE = {
  WIKIPEDIA: { label: '维基百科', license: 'CC BY-SA 4.0', color: '#409EFF' },
  MANUAL: { label: '自撰养护知识', license: '自有', color: '#67C23A' },
  PAPER: { label: '公开论文', license: '视情况', color: '#909399' }
}

export const QA_ROLE = {
  USER: 'USER',
  ASSISTANT: 'ASSISTANT'
}

/* ---------------- 天气 ---------------- */
export const WEATHER_SOURCE = {
  OPEN_METEO: { label: 'Open-Meteo', url: 'https://open-meteo.com/' }
}

/* ---------------- 业务错误码（与 docs/04 对齐） ---------------- */
export const ERROR_CODES = {
  0: '成功',
  1001: '参数校验失败',
  1002: '未登录或登录已过期',
  1003: '无权限访问该资源',
  1004: '资源不存在',
  2001: '用户名已存在',
  3001: '图片格式不支持（仅 jpg/png，且不超过 10MB）',
  3002: '识别服务暂时不可用',
  3003: '今日 AI 识别次数已用完',
  4001: '症状信息不足，无法判断',
  5001: '操作过于频繁，请稍后再试',
  9999: '服务内部错误'
}

/* ---------------- 分页默认值 ---------------- */
export const DEFAULT_PAGE_SIZE = 20
export const PAGE_SIZES = [10, 20, 50, 100]
