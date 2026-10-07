/**
 * Mock 示例数据
 * 仅用于骨架阶段让页面有内容可渲染，字段结构与 docs/04-API接口设计.md 完全一致。
 * 后端就绪后把 .env.development 里的 VITE_USE_MOCK 改为 false 即可，页面代码零改动。
 *
 * 说明：骨架阶段不使用网络图片（避免断网时一片破图），
 *      统一用 emoji 作为占位视觉，正式接入后替换为 imageUrl。
 */
import dayjs from 'dayjs'

const today = dayjs()
const d = (offset) => today.add(offset, 'day').format('YYYY-MM-DD')
const dt = (offset, h = 9, m = 0) => today.add(offset, 'day').hour(h).minute(m).second(0).millisecond(0).format('YYYY-MM-DDTHH:mm:ss')

/* ================= 用户 ================= */
export const mockUser = {
  id: 1,
  username: 'demo',
  nickname: '小林',
  avatarUrl: null,
  email: 'demo@example.com',
  role: 'USER',
  createdAt: '2026-06-01T10:00:00'
}

export const mockAdminUser = {
  ...mockUser,
  id: 2,
  username: 'admin',
  nickname: '管理员',
  role: 'ADMIN'
}

export const mockEnvironment = {
  city: '杭州',
  latitude: 30.274084,
  longitude: 120.15507,
  timezone: 'Asia/Shanghai',
  climateZone: '亚热带',
  hasAirConditioner: true,
  hasHeating: false,
  indoorHumidityLevel: 'LOW',
  weatherSyncEnabled: true,
  weatherSyncedAt: dt(0, 8, 0)
}

/* ================= 植物库 ================= */
export const mockSpecies = [
  {
    id: 1, emoji: '🌿', commonName: '绿萝', commonNameEn: 'Pothos',
    scientificName: 'Epipremnum aureum', aliases: ['魔鬼藤', '黄金葛'], family: '天南星科',
    difficulty: 'EASY', lightRequirement: 'MEDIUM', humidityRequirement: 'MEDIUM',
    baseWaterIntervalDays: 7, waterAmountMl: 200, fertilizerIntervalDays: 30,
    fertilizerType: '氮肥为主的观叶肥', soilType: '疏松营养土', growthRate: 'FAST', maxHeightCm: 200,
    tempMinC: 15, tempMaxC: 30, toxicity: 'TOXIC',
    toxicTo: [{ target: 'cat', level: 'HIGH' }, { target: 'dog', level: 'MEDIUM' }],
    modelClassIndex: 33, recognitionProvider: 'SELF_MODEL',
    seasonCoefficients: { SPRING: 1.0, SUMMER: 0.8, AUTUMN: 1.0, WINTER: 1.5 },
    description: '最常见的室内观叶植物，耐阴、好养、生长快，能适应较弱的光照环境。',
    careTips: ['避免盆内积水，这是绿萝死亡的头号原因', '冬季减少浇水，等土干透再浇', '汁液有微毒，接触后洗手，避免宠物啃食']
  },
  {
    id: 2, emoji: '🍃', commonName: '龟背竹', commonNameEn: 'Monstera',
    scientificName: 'Monstera deliciosa', aliases: ['蓬莱蕉'], family: '天南星科',
    difficulty: 'EASY', lightRequirement: 'BRIGHT_INDIRECT', humidityRequirement: 'HIGH',
    baseWaterIntervalDays: 10, waterAmountMl: 300, fertilizerIntervalDays: 30,
    fertilizerType: '观叶肥', soilType: '疏松营养土', growthRate: 'MEDIUM', maxHeightCm: 250,
    tempMinC: 15, tempMaxC: 30, toxicity: 'TOXIC',
    toxicTo: [{ target: 'cat', level: 'HIGH' }, { target: 'dog', level: 'HIGH' }],
    modelClassIndex: 30, recognitionProvider: 'SELF_MODEL',
    seasonCoefficients: { SPRING: 1.0, SUMMER: 0.85, AUTUMN: 1.0, WINTER: 1.6 },
    description: '叶片有天然孔洞，喜明亮散射光和较高湿度，是典型的"颜值担当"。',
    careTips: ['喜欢高湿度，干燥时叶缘容易焦', '忌强光直射，会晒伤叶片', '叶片可用湿布擦拭除尘']
  },
  {
    id: 3, emoji: '🪴', commonName: '虎皮兰', commonNameEn: 'Snake Plant',
    scientificName: 'Dracaena trifasciata', aliases: ['虎尾兰', '千岁兰'], family: '天门冬科',
    difficulty: 'EASY', lightRequirement: 'LOW', humidityRequirement: 'LOW',
    baseWaterIntervalDays: 21, waterAmountMl: 150, fertilizerIntervalDays: 60,
    fertilizerType: '缓释肥', soilType: '沙质排水土', growthRate: 'SLOW', maxHeightCm: 120,
    tempMinC: 12, tempMaxC: 32, toxicity: 'TOXIC',
    toxicTo: [{ target: 'cat', level: 'MEDIUM' }, { target: 'dog', level: 'MEDIUM' }],
    modelClassIndex: 40, recognitionProvider: 'SELF_MODEL',
    seasonCoefficients: { SPRING: 1.0, SUMMER: 0.9, AUTUMN: 1.0, WINTER: 2.0 },
    description: '极度耐旱耐阴，几周不浇水也没事，是最适合"手残党"和忙人的植物。',
    careTips: ['宁干勿湿，浇水过多是唯一死因', '冬季可整月不浇水', '耐弱光，但明亮处斑纹更明显']
  },
  {
    id: 4, emoji: '🌳', commonName: '发财树', commonNameEn: 'Money Tree',
    scientificName: 'Pachira aquatica', aliases: ['马拉巴栗'], family: '锦葵科',
    difficulty: 'MEDIUM', lightRequirement: 'MEDIUM', humidityRequirement: 'MEDIUM',
    baseWaterIntervalDays: 14, waterAmountMl: 250, fertilizerIntervalDays: 45,
    fertilizerType: '均衡复合肥', soilType: '疏松营养土', growthRate: 'SLOW', maxHeightCm: 200,
    tempMinC: 15, tempMaxC: 30, toxicity: 'NONE', toxicTo: [],
    modelClassIndex: null, recognitionProvider: 'PLANTNET',
    seasonCoefficients: { SPRING: 1.0, SUMMER: 0.9, AUTUMN: 1.0, WINTER: 1.8 },
    description: '寓意招财，木质化的茎干能储水，因此非常怕涝。',
    careTips: ['极其怕积水，盆底不能有存水', '落叶多为浇水过多或通风不良', '光照不足会徒长、茎干变细']
  },
  {
    id: 5, emoji: '🌱', commonName: '吊兰', commonNameEn: 'Spider Plant',
    scientificName: 'Chlorophytum comosum', aliases: ['挂兰'], family: '天门冬科',
    difficulty: 'EASY', lightRequirement: 'MEDIUM', humidityRequirement: 'MEDIUM',
    baseWaterIntervalDays: 8, waterAmountMl: 180, fertilizerIntervalDays: 30,
    fertilizerType: '观叶肥', soilType: '通用营养土', growthRate: 'FAST', maxHeightCm: 60,
    tempMinC: 10, tempMaxC: 30, toxicity: 'NONE', toxicTo: [],
    modelClassIndex: 41, recognitionProvider: 'SELF_MODEL',
    seasonCoefficients: { SPRING: 1.0, SUMMER: 0.8, AUTUMN: 1.0, WINTER: 1.5 },
    description: '会抽出小吊兰，净化空气能力强，非常容易繁殖。',
    careTips: ['叶尖焦枯最常见原因是自来水中的氟和氯', '建议用静置过的水浇', '长满盆后要及时分株']
  },
  {
    id: 6, emoji: '🌵', commonName: '芦荟', commonNameEn: 'Aloe Vera',
    scientificName: 'Aloe vera', aliases: [], family: '阿福花科',
    difficulty: 'EASY', lightRequirement: 'DIRECT', humidityRequirement: 'LOW',
    baseWaterIntervalDays: 21, waterAmountMl: 200, fertilizerIntervalDays: 60,
    fertilizerType: '多肉专用肥', soilType: '颗粒沙质土', growthRate: 'SLOW', maxHeightCm: 60,
    tempMinC: 10, tempMaxC: 35, toxicity: 'TOXIC',
    toxicTo: [{ target: 'cat', level: 'MEDIUM' }, { target: 'dog', level: 'MEDIUM' }],
    modelClassIndex: 2, recognitionProvider: 'SELF_MODEL',
    seasonCoefficients: { SPRING: 1.0, SUMMER: 1.0, AUTUMN: 1.0, WINTER: 2.0 },
    description: '喜阳耐旱的多肉植物，叶片储水，可入药。',
    careTips: ['需要充足直射光，否则叶片变薄发软', '宁干勿湿，土壤完全干透再浇', '叶片发红是晒伤信号，需遮阴']
  },
  {
    id: 7, emoji: '💠', commonName: '玉露', commonNameEn: 'Haworthia',
    scientificName: 'Haworthia cooperi', aliases: [], family: '阿福花科',
    difficulty: 'MEDIUM', lightRequirement: 'BRIGHT_INDIRECT', humidityRequirement: 'MEDIUM',
    baseWaterIntervalDays: 14, waterAmountMl: 80, fertilizerIntervalDays: 60,
    fertilizerType: '多肉专用肥', soilType: '颗粒土', growthRate: 'SLOW', maxHeightCm: 10,
    tempMinC: 8, tempMaxC: 28, toxicity: 'NONE', toxicTo: [],
    modelClassIndex: null, recognitionProvider: 'PLANTNET',
    seasonCoefficients: { SPRING: 1.0, SUMMER: 1.4, AUTUMN: 1.0, WINTER: 1.6 },
    description: '叶片顶端透明如窗，喜散射光，夏季高温会休眠。',
    careTips: ['忌强光直射，会晒成褐色', '夏季休眠期要控水', '闷养可让叶片更饱满，但需通风']
  },
  {
    id: 8, emoji: '🤍', commonName: '白掌', commonNameEn: 'Peace Lily',
    scientificName: 'Spathiphyllum wallisii', aliases: ['一帆风顺'], family: '天南星科',
    difficulty: 'MEDIUM', lightRequirement: 'MEDIUM', humidityRequirement: 'HIGH',
    baseWaterIntervalDays: 7, waterAmountMl: 200, fertilizerIntervalDays: 30,
    fertilizerType: '观花肥', soilType: '疏松营养土', growthRate: 'MEDIUM', maxHeightCm: 80,
    tempMinC: 16, tempMaxC: 30, toxicity: 'TOXIC',
    toxicTo: [{ target: 'cat', level: 'HIGH' }, { target: 'dog', level: 'HIGH' }],
    modelClassIndex: null, recognitionProvider: 'PLANTNET',
    seasonCoefficients: { SPRING: 1.0, SUMMER: 0.85, AUTUMN: 1.0, WINTER: 1.5 },
    description: '会开白色佛焰苞，缺水时叶片明显下垂，是很"会表达"的植物。',
    careTips: ['叶片下垂是最直观的缺水信号', '喜高湿度，可喷雾增湿', '对自来水中的氯敏感，叶尖易焦']
  },
  {
    id: 9, emoji: '🌴', commonName: '散尾葵', commonNameEn: 'Areca Palm',
    scientificName: 'Dypsis lutescens', aliases: ['黄椰子'], family: '棕榈科',
    difficulty: 'HARD', lightRequirement: 'BRIGHT_INDIRECT', humidityRequirement: 'HIGH',
    baseWaterIntervalDays: 7, waterAmountMl: 400, fertilizerIntervalDays: 30,
    fertilizerType: '观叶肥', soilType: '疏松营养土', growthRate: 'MEDIUM', maxHeightCm: 250,
    tempMinC: 15, tempMaxC: 30, toxicity: 'NONE', toxicTo: [],
    modelClassIndex: 4, recognitionProvider: 'SELF_MODEL',
    seasonCoefficients: { SPRING: 1.0, SUMMER: 0.8, AUTUMN: 1.0, WINTER: 1.6 },
    description: '羽状叶片，加湿效果好，但对空气干燥非常敏感。',
    careTips: ['叶尖焦枯几乎都因空气太干', '需要较高湿度，建议配加湿器', '忌冷风直吹']
  },
  {
    id: 10, emoji: '🌾', commonName: '文竹', commonNameEn: 'Asparagus Fern',
    scientificName: 'Asparagus setaceus', aliases: ['云竹'], family: '天门冬科',
    difficulty: 'HARD', lightRequirement: 'BRIGHT_INDIRECT', humidityRequirement: 'HIGH',
    baseWaterIntervalDays: 8, waterAmountMl: 150, fertilizerIntervalDays: 30,
    fertilizerType: '稀薄观叶肥', soilType: '疏松营养土', growthRate: 'MEDIUM', maxHeightCm: 100,
    tempMinC: 12, tempMaxC: 28, toxicity: 'TOXIC',
    toxicTo: [{ target: 'cat', level: 'LOW' }, { target: 'dog', level: 'LOW' }],
    modelClassIndex: 7, recognitionProvider: 'SELF_MODEL',
    seasonCoefficients: { SPRING: 1.0, SUMMER: 0.9, AUTUMN: 1.0, WINTER: 1.5 },
    description: '形态清秀如云，但很娇气，怕干怕晒怕烟。',
    careTips: ['黄叶最常见原因是空气干燥', '忌阳光直射和空调/暖气风口', '对烟雾敏感，厨房附近易黄叶']
  },
  {
    id: 11, emoji: '🍀', commonName: '常春藤', commonNameEn: 'English Ivy',
    scientificName: 'Hedera helix', aliases: [], family: '五加科',
    difficulty: 'MEDIUM', lightRequirement: 'MEDIUM', humidityRequirement: 'MEDIUM',
    baseWaterIntervalDays: 7, waterAmountMl: 180, fertilizerIntervalDays: 30,
    fertilizerType: '观叶肥', soilType: '通用营养土', growthRate: 'FAST', maxHeightCm: 300,
    tempMinC: 5, tempMaxC: 28, toxicity: 'TOXIC',
    toxicTo: [{ target: 'cat', level: 'MEDIUM' }, { target: 'dog', level: 'MEDIUM' }],
    modelClassIndex: null, recognitionProvider: 'PLANTNET',
    seasonCoefficients: { SPRING: 1.0, SUMMER: 0.9, AUTUMN: 1.0, WINTER: 1.4 },
    description: '耐阴的藤本，适合垂吊，冬季相对耐寒。',
    careTips: ['怕热，夏季需通风降温', '红蜘蛛高发，注意叶背', '需要排水良好的土壤']
  },
  {
    id: 12, emoji: '🌰', commonName: '橡皮树', commonNameEn: 'Rubber Plant',
    scientificName: 'Ficus elastica', aliases: ['印度榕'], family: '桑科',
    difficulty: 'MEDIUM', lightRequirement: 'BRIGHT_INDIRECT', humidityRequirement: 'MEDIUM',
    baseWaterIntervalDays: 12, waterAmountMl: 300, fertilizerIntervalDays: 30,
    fertilizerType: '观叶肥', soilType: '疏松营养土', growthRate: 'MEDIUM', maxHeightCm: 300,
    tempMinC: 15, tempMaxC: 30, toxicity: 'TOXIC',
    toxicTo: [{ target: 'cat', level: 'MEDIUM' }, { target: 'dog', level: 'MEDIUM' }],
    modelClassIndex: null, recognitionProvider: 'PLANTNET',
    seasonCoefficients: { SPRING: 1.0, SUMMER: 0.9, AUTUMN: 1.0, WINTER: 1.6 },
    description: '叶片厚实油亮，耐阴但明亮处叶色更好。',
    careTips: ['不喜欢挪动，位置固定后别频繁搬', '汁液白色有毒，修剪戴手套', '浇水过多会大量落叶']
  }
]

/* ================= 我的植物 ================= */
export const mockPlants = [
  {
    id: 101, speciesId: 1, speciesName: '绿萝', speciesEmoji: '🌿',
    nickname: '小绿', location: '客厅', lightLevel: 'MEDIUM',
    potSize: 'MEDIUM', potMaterial: 'CERAMIC', soilType: '通用营养土',
    acquiredDate: '2026-05-20', healthStatus: 'WARNING', addSource: 'RECOGNITION',
    createdAt: '2026-05-20T10:00:00',
    lastWateredAt: dt(-3, 9, 0), nextWaterDueDate: d(2)
  },
  {
    id: 102, speciesId: 2, speciesName: '龟背竹', speciesEmoji: '🍃',
    nickname: '大叶', location: '客厅', lightLevel: 'BRIGHT_INDIRECT',
    potSize: 'LARGE', potMaterial: 'TERRACOTTA', soilType: '疏松营养土',
    acquiredDate: '2026-04-10', healthStatus: 'HEALTHY', addSource: 'MANUAL',
    createdAt: '2026-04-10T15:20:00',
    lastWateredAt: dt(-6, 9, 0), nextWaterDueDate: d(4)
  },
  {
    id: 103, speciesId: 6, speciesName: '芦荟', speciesEmoji: '🌵',
    nickname: '胖胖', location: '阳台', lightLevel: 'DIRECT',
    potSize: 'SMALL', potMaterial: 'TERRACOTTA', soilType: '颗粒沙质土',
    acquiredDate: '2026-03-02', healthStatus: 'HEALTHY', addSource: 'MANUAL',
    createdAt: '2026-03-02T11:00:00',
    lastWateredAt: dt(-9, 9, 0), nextWaterDueDate: d(12)
  },
  {
    id: 104, speciesId: 3, speciesName: '虎皮兰', speciesEmoji: '🪴',
    nickname: '虎妞', location: '卧室', lightLevel: 'LOW',
    potSize: 'MEDIUM', potMaterial: 'PLASTIC', soilType: '沙质排水土',
    acquiredDate: '2025-11-15', healthStatus: 'HEALTHY', addSource: 'MANUAL',
    createdAt: '2025-11-15T09:30:00',
    lastWateredAt: dt(-15, 9, 0), nextWaterDueDate: d(6)
  }
]

/* ================= 养护计划（含系数拆解） ================= */
export const mockCarePlans = [
  {
    id: 301, userPlantId: 101, planType: 'WATER', season: 'SUMMER',
    baseIntervalDays: 7, effectiveIntervalDays: 5,
    coefficientDetail: { season: 0.8, light: 1.15, temp: 0.85, humidity: 0.9, pot: 1.1, ac: 0.9 },
    coefficientLabels: {
      season: '夏季（生长季）', light: '中等光照', temp: '气温 31.5°C（>30）',
      humidity: '湿度 45%', pot: '釉面陶瓷盆（不透气）', ac: '空调房'
    },
    calculation: '基准 7 天 × 夏季 0.8 × 中等光照 1.15 × 高温 0.85 × 空调房 0.9 × 釉面陶瓷盆 1.1 ≈ 5.4 天 → 取 5 天',
    llmExplain:
      '本周建议浇水 1–2 次。绿萝在夏季生长旺盛、需水偏多，但你放在空调房里空气偏干，而且用的是釉面陶瓷盆、透气性比陶盆差。综合下来把间隔从基准的 7 天调整为 5 天左右。判断方法：手指插入土中 2–3cm，干了再浇，不要按固定日子浇。',
    nextDueDate: d(2), isActive: true, generatedBy: 'RULE', weatherSnapshot: { temperature: 31.5, relativeHumidity: 45 }
  },
  {
    id: 302, userPlantId: 101, planType: 'FERTILIZE', season: 'SUMMER',
    baseIntervalDays: 30, effectiveIntervalDays: 21,
    coefficientDetail: { season: 0.7 },
    coefficientLabels: { season: '夏季（生长期）' },
    calculation: '基准 30 天 × 生长期 0.7 = 21 天',
    llmExplain: '夏季是生长旺季，可以每 3 周施一次稀薄的观叶肥，浓度比说明书再稀一点更安全。',
    nextDueDate: d(8), isActive: true, generatedBy: 'RULE'
  },
  {
    id: 303, userPlantId: 102, planType: 'WATER', season: 'SUMMER',
    baseIntervalDays: 10, effectiveIntervalDays: 7,
    coefficientDetail: { season: 0.85, light: 1.0, temp: 0.85, humidity: 0.9, pot: 0.9, ac: 0.9 },
    coefficientLabels: {
      season: '夏季（生长季）', light: '明亮散射光', temp: '气温 31.5°C（>30）',
      humidity: '湿度 45%', pot: '陶盆（透气）', ac: '空调房'
    },
    calculation: '基准 10 天 × 夏季 0.85 × 高温 0.85 × 空调房 0.9 × 陶盆 0.9 ≈ 5.8 天 → 结合长势取 7 天',
    llmExplain: '龟背竹喜湿润但不耐涝，陶盆透气性好，夏季可 7 天左右浇一次，浇则浇透。',
    nextDueDate: d(4), isActive: true, generatedBy: 'RULE'
  },
  {
    id: 304, userPlantId: 103, planType: 'WATER', season: 'SUMMER',
    baseIntervalDays: 21, effectiveIntervalDays: 21,
    coefficientDetail: { season: 1.0, light: 0.85, temp: 1.0, humidity: 1.0, pot: 0.9, ac: 1.0 },
    coefficientLabels: {
      season: '夏季', light: '直射光', temp: '气温 31.5°C', humidity: '湿度 45%',
      pot: '陶盆（透气）', ac: '非空调房'
    },
    calculation: '基准 21 天 × 直射 0.85 × 陶盆 0.9 ≈ 16 天 → 保守取 21 天',
    llmExplain: '芦荟非常耐旱，宁干勿湿。放在阳台直射处加上陶盆透气，仍需等土壤完全干透再浇。',
    nextDueDate: d(12), isActive: true, generatedBy: 'RULE'
  }
]

/* ================= 养护任务（日历） ================= */
export const mockCareTasks = [
  { id: 7001, planId: 301, userPlantId: 101, taskType: 'WATER', dueDate: d(-3), status: 'DONE', completedAt: dt(-3, 9, 0), plantNickname: '小绿', speciesName: '绿萝' },
  { id: 7002, planId: 303, userPlantId: 102, taskType: 'WATER', dueDate: d(-6), status: 'DONE', completedAt: dt(-6, 9, 30), plantNickname: '大叶', speciesName: '龟背竹' },
  { id: 7003, planId: 301, userPlantId: 101, taskType: 'WATER', dueDate: d(2), status: 'PENDING', plantNickname: '小绿', speciesName: '绿萝' },
  { id: 7004, planId: 303, userPlantId: 102, taskType: 'WATER', dueDate: d(4), status: 'PENDING', plantNickname: '大叶', speciesName: '龟背竹' },
  { id: 7005, planId: 304, userPlantId: 103, taskType: 'WATER', dueDate: d(12), status: 'PENDING', plantNickname: '胖胖', speciesName: '芦荟' },
  { id: 7006, planId: 302, userPlantId: 101, taskType: 'FERTILIZE', dueDate: d(8), status: 'PENDING', plantNickname: '小绿', speciesName: '绿萝' },
  { id: 7007, planId: 305, userPlantId: 104, taskType: 'WATER', dueDate: d(6), status: 'PENDING', plantNickname: '虎妞', speciesName: '虎皮兰' },
  { id: 7008, planId: 301, userPlantId: 101, taskType: 'WATER', dueDate: d(-8), status: 'DONE', completedAt: dt(-8, 10, 0), plantNickname: '小绿', speciesName: '绿萝' },
  { id: 7009, planId: 303, userPlantId: 102, taskType: 'WATER', dueDate: d(-13), status: 'DONE', completedAt: dt(-13, 9, 0), plantNickname: '大叶', speciesName: '龟背竹' },
  { id: 7010, planId: 306, userPlantId: 101, taskType: 'PRUNE', dueDate: d(-1), status: 'OVERDUE', plantNickname: '小绿', speciesName: '绿萝' },
  { id: 7011, planId: 301, userPlantId: 101, taskType: 'WATER', dueDate: d(7), status: 'PENDING', plantNickname: '小绿', speciesName: '绿萝' },
  { id: 7012, planId: 303, userPlantId: 102, taskType: 'WATER', dueDate: d(11), status: 'PENDING', plantNickname: '大叶', speciesName: '龟背竹' }
]

/* ================= 养护日志 ================= */
export const mockCareLogs = [
  { id: 4401, userPlantId: 101, taskId: 7001, logType: 'WATER', amount: 200, unit: 'ml', detail: '土表干了约 2cm', loggedAt: dt(-3, 9, 0), plantNickname: '小绿', speciesName: '绿萝' },
  { id: 4402, userPlantId: 102, taskId: 7002, logType: 'WATER', amount: 400, unit: 'ml', detail: '浇透，盆底有出水', loggedAt: dt(-6, 9, 30), plantNickname: '大叶', speciesName: '龟背竹' },
  { id: 4403, userPlantId: 101, taskId: 7008, logType: 'WATER', amount: 200, unit: 'ml', detail: '', loggedAt: dt(-8, 10, 0), plantNickname: '小绿', speciesName: '绿萝' },
  { id: 4404, userPlantId: 103, taskId: null, logType: 'WATER', amount: 150, unit: 'ml', detail: '土壤完全干透', loggedAt: dt(-9, 11, 0), plantNickname: '胖胖', speciesName: '芦荟' },
  { id: 4405, userPlantId: 102, taskId: 7009, logType: 'WATER', amount: 400, unit: 'ml', detail: '', loggedAt: dt(-13, 9, 0), plantNickname: '大叶', speciesName: '龟背竹' },
  { id: 4406, userPlantId: 101, taskId: null, logType: 'FERTILIZE', amount: 3, unit: 'g', detail: '观叶肥，兑水 500ml', loggedAt: dt(-14, 10, 0), plantNickname: '小绿', speciesName: '绿萝' },
  { id: 4407, userPlantId: 104, taskId: null, logType: 'WATER', amount: 150, unit: 'ml', detail: '冬季减少水量', loggedAt: dt(-15, 9, 0), plantNickname: '虎妞', speciesName: '虎皮兰' },
  { id: 4408, userPlantId: 101, taskId: null, logType: 'PRUNE', amount: null, unit: null, detail: '剪掉两根过长的枝条', loggedAt: dt(-20, 16, 0), plantNickname: '小绿', speciesName: '绿萝' },
  { id: 4409, userPlantId: 102, taskId: null, logType: 'REPOT', amount: null, unit: null, detail: '换到更大的陶盆', loggedAt: dt(-45, 14, 0), plantNickname: '大叶', speciesName: '龟背竹' }
]

/* ================= 拍照识别结果 ================= */
export const mockRecognition = {
  recordId: 8801,
  provider: 'SELF_MODEL',
  modelVersion: 'mobilenetv3-v1.0',
  latencyMs: 340,
  lowConfidence: false,
  fallbackUsed: false,
  results: [
    { rank: 1, speciesId: 1, commonName: '绿萝', scientificName: 'Epipremnum aureum', emoji: '🌿', confidence: 0.87, careSummary: '耐阴，7 天左右浇一次，忌积水' },
    { rank: 2, speciesId: 8, commonName: '白掌', scientificName: 'Spathiphyllum wallisii', emoji: '🤍', confidence: 0.08, careSummary: '喜高湿度，缺水时叶片下垂' },
    { rank: 3, speciesId: 12, commonName: '橡皮树', scientificName: 'Ficus elastica', emoji: '🌰', confidence: 0.03, careSummary: '耐阴，忌频繁挪动' }
  ]
}

/* ================= 症状诊断结果（★ 核心示例） =================
   这份数据是【按判因算法真实算出来的】，不是随手编的，
   全文推导过程见 docs/08-诊断流程详解.md 第 3 节。

   案例：绿萝「小绿」，客厅，釉面陶瓷盆，空调房
   上下文：leaf_position=OLD, watering_ratio=1.79, pot=CERAMIC,
          humidity=28, temp=31.5, season=SUMMER, fertilizing=NONE

   命中规则： #1 浇水过多 0.95（老叶 + 浇水≥1.5倍）
             #2 浇水过多 0.30（老叶 + 釉面陶瓷盆）
             #8 空气干燥 0.55（湿度<30%）
             #12 虫害    0.20（无条件规则）
   累加：     浇水过多 1.25 · 空气干燥 0.55 · 虫害 0.20 · 合计 2.00
   归一化：   62.5% / 27.5% / 10.0%
   过滤：     虫害 10.0% < 15% 阈值 → 丢弃
   =============================================================== */
export const mockSymptomDiagnosis = {
  recordId: 9002,
  diagnosisType: 'SYMPTOM',
  confidenceLevel: 'HIGH',
  candidates: [
    {
      rank: 1,
      causeCode: 'OVERWATER',
      nameCn: '浇水过多 / 烂根',
      category: 'WATER',
      urgency: 'HIGH',
      confidence: 0.625,
      evidence: [
        '黄叶从下部老叶开始，且你近 14 天浇水 5 次，是建议值（约 2.8 次）的 1.8 倍 —— 积水导致根系缺氧',
        '釉面陶瓷盆透气性差，水分不易蒸发'
      ],
      actionPlan: [
        '立即停止浇水，把花盆移到通风处',
        '检查盆底排水孔是否堵塞',
        '如果土壤仍然潮湿且有异味，建议脱盆检查根系，剪除发黑腐烂的根',
        '恢复浇水后，改为「手指插入土中 2–3cm，干了再浇」'
      ],
      hitRuleIds: [1, 2]
    },
    {
      rank: 2,
      causeCode: 'LOW_HUMIDITY',
      nameCn: '空气过于干燥',
      category: 'ENV',
      urgency: 'LOW',
      confidence: 0.275,
      evidence: ['空调房内湿度约 28%，低于 30%，蒸腾过强会加重叶色变差'],
      actionPlan: ['在花盆附近放一盆水，或使用加湿器', '避免放在空调出风口正对的位置'],
      hitRuleIds: [8]
    }
  ],
  suggestion:
    '综合来看，最可能是浇水过多导致根系缺氧。空气干燥会加重这个问题。建议先停水观察 5–7 天，并把花盆移到通风处；同时适当提高环境湿度。如果土壤持续潮湿、有异味，需要脱盆检查根系。',
  citations: [
    { title: '绿萝·浇水与积水判断', source: 'MANUAL', url: null },
    { title: '花盆材质对水分蒸发的影响', source: 'MANUAL', url: null }
  ]
}

/** 信息不足时的降级响应（用于演示"不武断下结论"） */
export const mockSymptomDiagnosisLowConfidence = {
  recordId: 9003,
  diagnosisType: 'SYMPTOM',
  confidenceLevel: 'LOW',
  candidates: [],
  needMoreInfo: true,
  followUpQuestions: [
    '黄叶是从下面的老叶开始，还是上面的新叶先黄？',
    '最近两周大概浇了几次水？',
    '这盆植物摆放的位置光线怎么样？'
  ],
  fallbackSuggestion: '目前的信息还不足以判断原因。补充上面的信息会更准确，你也可以直接去 AI 问答里描述情况。'
}

/* ================= 病害识别结果 ================= */
export const mockDiseaseDiagnosis = {
  recordId: 9001,
  diagnosisType: 'DISEASE',
  provider: 'SELF_MODEL',
  predicted: {
    code: 'bacterial_wilt',
    nameCn: '细菌性萎蔫',
    category: 'BACTERIAL',
    confidence: 0.82,
    severity: 'HIGH'
  },
  topK: [
    { code: 'bacterial_wilt', nameCn: '细菌性萎蔫', confidence: 0.82 },
    { code: 'manganese_toxicity', nameCn: '锰中毒', confidence: 0.11 },
    { code: 'fungal_leaf_spot', nameCn: '真菌性叶斑病', confidence: 0.05 }
  ],
  symptoms: '叶片快速萎蔫发黄，茎部出现水渍状褐斑，严重时整株倒伏。',
  treatment:
    '1. 立即与其他植物隔离，避免通过工具和手部接触传播\n2. 剪除已明显发病的叶片和茎段，剪刀每次使用后消毒\n3. 停止叶面喷水，保持叶面干燥\n4. 改善通风，避免高温高湿\n5. 若病情持续恶化，考虑脱盆检查并销毁严重感染的植株',
  prevention: '避免叶面长期积水，浇水时尽量浇在土面；保持良好通风；新购入的植物先隔离观察两周。',
  note: '以上为园艺级处理建议，不构成农药使用指导。'
}

/* ================= 诊断记录 ================= */
export const mockDiagnosisRecords = [
  { id: 9002, userPlantId: 101, plantNickname: '小绿', diagnosisType: 'SYMPTOM', createdAt: dt(-3, 20, 15), summary: '叶片发黄 → 浇水过多', confidence: 1.0, status: 'TREATING' },
  { id: 9001, userPlantId: 102, plantNickname: '大叶', diagnosisType: 'DISEASE', createdAt: dt(-12, 18, 40), summary: '细菌性萎蔫', confidence: 0.82, status: 'OPEN' },
  { id: 9000, userPlantId: 101, plantNickname: '小绿', diagnosisType: 'SYMPTOM', createdAt: dt(-30, 21, 5), summary: '叶尖焦枯 → 空气干燥', confidence: 0.9, status: 'RESOLVED' }
]

/* ================= 统计看板 ================= */
export const mockStatsOverview = {
  plantCount: 4,
  speciesCount: 4,
  healthDistribution: { HEALTHY: 3, WARNING: 1, DISEASED: 0, DEAD: 0 },
  careStats: {
    waterCountTotal: 42,
    waterCountThisMonth: 9,
    fertilizeCountThisMonth: 2,
    onTimeRate: 0.86,
    overdueCount: 1
  },
  diagnosisStats: { total: 3, resolved: 1, open: 2 },
  qaCount: 17
}

export const mockWateringTrend = {
  weeks: [
    { week: 'W-7', planned: 6, actual: 6 },
    { week: 'W-6', planned: 6, actual: 5 },
    { week: 'W-5', planned: 7, actual: 7 },
    { week: 'W-4', planned: 6, actual: 4 },
    { week: 'W-3', planned: 6, actual: 6 },
    { week: 'W-2', planned: 7, actual: 7 },
    { week: 'W-1', planned: 6, actual: 5 },
    { week: '本周', planned: 6, actual: 4 }
  ]
}

/* ================= AI 问答 ================= */
export const mockQaSessions = [
  { id: 1, title: '绿萝叶子发黄怎么办', userPlantId: 101, messageCount: 4, updatedAt: dt(-3, 20, 20) },
  { id: 2, title: '龟背竹叶片边缘变褐', userPlantId: 102, messageCount: 2, updatedAt: dt(-12, 19, 0) },
  { id: 3, title: '冬天要不要给虎皮兰浇水', userPlantId: 104, messageCount: 6, updatedAt: dt(-25, 21, 30) }
]

export const mockQaMessages = [
  {
    id: 1, sessionId: 1, role: 'USER', content: '绿萝叶子发黄怎么办', createdAt: dt(-3, 20, 15)
  },
  {
    id: 2,
    sessionId: 1,
    role: 'ASSISTANT',
    content:
      '绿萝叶子发黄最常见的原因是**浇水过多**。绿萝的根系需要呼吸，盆土长期潮湿会导致根系缺氧腐烂，最先表现出来的就是下部老叶发黄。\n\n你可以按这三步排查：\n\n1. **摸土壤**：手指插入土中 2–3cm，如果还是湿的，说明水浇多了\n2. **看位置**：如果是从下面的老叶先黄，基本可以确认是积水问题\n3. **看花盆**：釉面陶瓷盆透气性比陶盆差很多，更容易积水\n\n处理办法是先停水，把花盆移到通风处，等土壤干透再浇。如果土壤有异味、茎基发软，建议脱盆检查根系。\n\n如果是**上面的新叶先黄**，那更可能是光照不足或长期没施肥，处理方式完全不同。',
    createdAt: dt(-3, 20, 18),
    retrievedChunks: [
      { chunkId: 101, title: '绿萝·浇水与积水判断', score: 0.91 },
      { chunkId: 145, title: '室内观叶植物黄叶的常见原因', score: 0.87 },
      { chunkId: 88, title: '花盆材质对水分蒸发的影响', score: 0.72 }
    ],
    citations: [
      { title: '绿萝·浇水与积水判断', source: 'MANUAL', url: null },
      { title: 'Houseplant yellowing leaves', source: 'WIKIPEDIA', url: 'https://en.wikipedia.org/wiki/Houseplant' }
    ],
    model: 'gpt-4o-mini',
    latencyMs: 3200,
    feedback: 1
  },
  { id: 3, sessionId: 1, role: 'USER', content: '那我要怎么判断土壤是不是太湿？', createdAt: dt(-3, 20, 20) },
  {
    id: 4,
    sessionId: 1, role: 'ASSISTANT',
    content: '三个不用工具的判断方法：\n\n1. **手指法**（最实用）：食指插入土中约 2–3cm，感觉潮湿就再等等\n2. **掂盆法**：浇透水后掂一下重量，记住这个手感；明显变轻了就该浇了\n3. **竹签法**：插一根竹签到盆底，拔出后如果带出的土是湿的、且竹签发暗，说明还没干\n\n注意不要只看土表——土表干了但底下可能还是湿的，这是新手最容易踩的坑。',
    createdAt: dt(-3, 20, 21),
    retrievedChunks: [{ chunkId: 102, title: '如何判断是否需要浇水', score: 0.93 }],
    citations: [{ title: '如何判断是否需要浇水', source: 'MANUAL', url: null }],
    model: 'gpt-4o-mini',
    latencyMs: 2800,
    feedback: null
  }
]

/* ================= 天气 ================= */
export const mockWeather = {
  city: '杭州',
  temperature: 31.5,
  relativeHumidity: 45,
  sunshineDuration: 28800,
  precipitation: 0,
  observedAt: dt(0, 10, 0),
  source: 'OPEN_METEO',
  impactOnCare: {
    tempCoefficient: 0.85,
    humidityCoefficient: 0.9,
    note: '气温 31.5°C 高于 30°C，且空调房内空气偏干，浇水间隔已相应缩短'
  }
}

/* ================= 管理端 ================= */
export const mockSymptomRules = [
  { id: 1, symptomCode: 'YELLOW_LEAF', causeCode: 'OVERWATER', causeName: '浇水过多 / 烂根', condition: { leafPosition: 'OLD', 'watering_ratio': { op: '>=', value: 1.5 } }, weight: 0.95, explainTpl: '黄叶从下部老叶开始，且近14天浇水次数是建议值的 {ratio} 倍——积水导致根系缺氧', enabled: true },
  { id: 2, symptomCode: 'YELLOW_LEAF', causeCode: 'OVERWATER', causeName: '浇水过多 / 烂根', condition: { leafPosition: 'OLD', potMaterial: 'CERAMIC' }, weight: 0.3, explainTpl: '釉面陶瓷盆透气性差，水分不易蒸发', enabled: true },
  { id: 3, symptomCode: 'YELLOW_LEAF', causeCode: 'OVERWATER', causeName: '浇水过多 / 烂根', condition: { leafPosition: 'ANY', 'watering_ratio': { op: '>=', value: 2.0 } }, weight: 0.4, explainTpl: '浇水频率远超建议值', enabled: true },
  { id: 4, symptomCode: 'YELLOW_LEAF', causeCode: 'LOW_LIGHT', causeName: '光照不足', condition: { leafPosition: 'NEW', lightLevel: 'LOW' }, weight: 0.85, explainTpl: '新叶先黄且摆放位置光照不足——光合作用不够', enabled: true },
  { id: 6, symptomCode: 'YELLOW_LEAF', causeCode: 'NUTRIENT_DEF', causeName: '缺肥（缺氮/缺铁）', condition: { veinPattern: 'GREEN_VEIN', fertilizing: 'NONE' }, weight: 0.8, explainTpl: '叶脉仍绿而叶肉发黄，且长期未施肥——典型缺铁/缺氮', enabled: true },
  { id: 7, symptomCode: 'YELLOW_LEAF', causeCode: 'NUTRIENT_BURN', causeName: '肥害（施肥过量）', condition: { leafPosition: 'NEW', fertilizing: 'RECENT' }, weight: 0.75, explainTpl: '新叶先黄且近期施过肥——可能是肥害烧根', enabled: true },
  { id: 9, symptomCode: 'YELLOW_LEAF', causeCode: 'NORMAL_SENESCENCE', causeName: '正常代谢 / 老叶更新', condition: { leafPosition: 'OLD', season: 'AUTUMN', 'watering_ratio': { op: '<', value: 1.2 } }, weight: 0.55, explainTpl: '秋季底部少量老叶自然发黄脱落，属正常代谢', enabled: true },
  { id: 14, symptomCode: 'LEAF_TIP_BURN', causeCode: 'LOW_HUMIDITY', causeName: '空气过于干燥', condition: { 'humidity': { op: '<', value: 40 } }, weight: 0.9, explainTpl: '空气干燥是叶尖焦枯最常见的原因，湿度仅 {humidity}%', enabled: true },
  { id: 15, symptomCode: 'LEAF_TIP_BURN', causeCode: 'NUTRIENT_BURN', causeName: '肥害（施肥过量）', condition: { fertilizing: 'HEAVY' }, weight: 0.7, explainTpl: '施肥浓度过高，盐分在叶尖积累', enabled: true },
  { id: 28, symptomCode: 'LEGGY', causeCode: 'LOW_LIGHT', causeName: '光照不足', condition: { lightLevel: 'LOW' }, weight: 0.95, explainTpl: '徒长是缺光最典型的表现', enabled: true }
]

export const mockDiseases = [
  { id: 1, code: 'bacterial_wilt', nameCn: '细菌性萎蔫', category: 'BACTERIAL', eppoCode: null, severity: 'HIGH', applicableSpecies: '绿萝', updatedAt: dt(-30) },
  { id: 2, code: 'manganese_toxicity', nameCn: '锰中毒', category: 'PHYSIOLOGICAL', eppoCode: null, severity: 'MEDIUM', applicableSpecies: '绿萝', updatedAt: dt(-30) },
  { id: 3, code: 'fungal_leaf_spot', nameCn: '真菌性叶斑病', category: 'FUNGAL', eppoCode: null, severity: 'MEDIUM', applicableSpecies: '吊兰', updatedAt: dt(-28) },
  { id: 4, code: 'aloe_rust', nameCn: '芦荟锈病', category: 'FUNGAL', eppoCode: null, severity: 'MEDIUM', applicableSpecies: '芦荟', updatedAt: dt(-28) },
  { id: 5, code: 'anthracnose', nameCn: '炭疽病', category: 'FUNGAL', eppoCode: null, severity: 'MEDIUM', applicableSpecies: '芦荟、虎皮兰', updatedAt: dt(-20) },
  { id: 6, code: 'leaf_withering', nameCn: '叶片枯萎', category: 'PHYSIOLOGICAL', eppoCode: null, severity: 'LOW', applicableSpecies: '虎皮兰', updatedAt: dt(-20) },
  { id: 7, code: 'leaf_tip_necrosis', nameCn: '叶尖坏死', category: 'PHYSIOLOGICAL', eppoCode: null, severity: 'LOW', applicableSpecies: '吊兰', updatedAt: dt(-20) },
  { id: 8, code: 'cochineal_infection', nameCn: '胭脂虫感染', category: 'PEST', eppoCode: null, severity: 'MEDIUM', applicableSpecies: '仙人掌', updatedAt: dt(-15) },
  { id: 9, code: 'sunburn', nameCn: '晒伤', category: 'PHYSIOLOGICAL', eppoCode: null, severity: 'LOW', applicableSpecies: '芦荟', updatedAt: dt(-15) },
  { id: 10, code: 'healthy', nameCn: '健康', category: 'PHYSIOLOGICAL', eppoCode: null, severity: 'LOW', applicableSpecies: '全部', updatedAt: dt(-15) }
]

export const mockKnowledgeArticles = [
  { id: 1, title: '绿萝·浇水与积水判断', source: 'MANUAL', license: '自有', category: '浇水', language: 'zh', chunkCount: 4, embedStatus: 'DONE', updatedAt: dt(-10) },
  { id: 2, title: '绿萝·光照与摆放位置', source: 'MANUAL', license: '自有', category: '光照', language: 'zh', chunkCount: 3, embedStatus: 'DONE', updatedAt: dt(-10) },
  { id: 3, title: '龟背竹·养护卡片', source: 'MANUAL', license: '自有', category: '综合', language: 'zh', chunkCount: 5, embedStatus: 'DONE', updatedAt: dt(-9) },
  { id: 4, title: '室内观叶植物黄叶的常见原因', source: 'MANUAL', license: '自有', category: '病害', language: 'zh', chunkCount: 6, embedStatus: 'DONE', updatedAt: dt(-8) },
  { id: 5, title: '如何判断是否需要浇水', source: 'MANUAL', license: '自有', category: '浇水', language: 'zh', chunkCount: 3, embedStatus: 'DONE', updatedAt: dt(-8) },
  { id: 6, title: 'Houseplant', source: 'WIKIPEDIA', license: 'CC BY-SA 4.0', category: '综合', language: 'en', chunkCount: 8, embedStatus: 'DONE', updatedAt: dt(-7), sourceUrl: 'https://en.wikipedia.org/wiki/Houseplant' },
  { id: 7, title: 'Houseplant care', source: 'WIKIPEDIA', license: 'CC BY-SA 4.0', category: '综合', language: 'en', chunkCount: 7, embedStatus: 'PENDING', updatedAt: dt(-2), sourceUrl: 'https://en.wikipedia.org/wiki/Houseplant_care' },
  { id: 8, title: '花盆材质对水分蒸发的影响', source: 'MANUAL', license: '自有', category: '浇水', language: 'zh', chunkCount: 2, embedStatus: 'DONE', updatedAt: dt(-6) }
]

export const mockAdminStats = {
  totalRecognitions: 428,
  confirmedCount: 301,
  correctedCount: 47,
  realWorldTop1Accuracy: 0.865,
  plantnetQuota: { used: 213, limit: 500, date: d(0) },
  modelUsage: [
    { name: '物种识别（自研）', calls: 380, avgLatencyMs: 340 },
    { name: '物种识别（Pl@ntNet）', calls: 213, avgLatencyMs: 980 },
    { name: '病害识别', calls: 96, avgLatencyMs: 310 },
    { name: '症状判因（规则）', calls: 152, avgLatencyMs: 12 },
    { name: 'RAG 问答', calls: 264, avgLatencyMs: 3200 },
    { name: '文本向量化', calls: 1180, avgLatencyMs: 60 }
  ],
  bySpecies: [
    { speciesId: 1, name: '绿萝', total: 62, corrected: 3, accuracy: 0.952 },
    { speciesId: 2, name: '龟背竹', total: 48, corrected: 2, accuracy: 0.958 },
    { speciesId: 3, name: '虎皮兰', total: 35, corrected: 4, accuracy: 0.886 },
    { speciesId: 6, name: '芦荟', total: 41, corrected: 12, accuracy: 0.707 },
    { speciesId: 7, name: '玉露', total: 22, corrected: 9, accuracy: 0.591 }
  ]
}
