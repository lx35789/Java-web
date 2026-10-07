/**
 * Mock 适配器
 *
 * 以 axios adapter 的形式接管请求，返回与后端一致的统一响应体
 * { code, message, data, traceId }。
 *
 * 这样做的价值：api/ 目录下的业务代码与真实环境完全一致，
 * 后端就绪后把 VITE_USE_MOCK 改成 false 即可，页面代码无需任何改动。
 */
import dayjs from 'dayjs'
import * as M from './data'

/* ---------------- 路由表 ---------------- */
const routes = []
const route = (method, pattern, handler) => routes.push({ method, pattern: pattern.split('/').filter(Boolean), handler })

function matchRoute(method, path) {
  const segs = path.split('/').filter(Boolean)
  for (const r of routes) {
    if (r.method !== method) continue
    if (r.pattern.length !== segs.length) continue
    const params = {}
    let ok = true
    for (let i = 0; i < r.pattern.length; i++) {
      const p = r.pattern[i]
      if (p.startsWith(':')) params[p.slice(1)] = decodeURIComponent(segs[i])
      else if (p !== segs[i]) {
        ok = false
        break
      }
    }
    if (ok) return { handler: r.handler, params }
  }
  return null
}

const ok = (data) => ({ code: 0, message: 'ok', data, traceId: Math.random().toString(36).slice(2, 10) })
const paged = (list, params = {}) => {
  const page = Number(params.page) || 1
  const size = Number(params.size) || 20
  return { list: list.slice((page - 1) * size, page * size), total: list.length, page, size }
}

/* ================= 认证与用户 ================= */
route('POST', '/auth/login', ({ body }) => {
  // Mock 约定：用户名以 admin 开头即管理员
  const isAdmin = String(body.username || '').startsWith('admin')
  return ok({
    token: 'mock-token-' + Date.now(),
    user: isAdmin ? M.mockAdminUser : { ...M.mockUser, username: body.username || 'demo' }
  })
})

route('POST', '/auth/register', ({ body }) =>
  ok({ token: 'mock-token-' + Date.now(), user: { ...M.mockUser, username: body.username, nickname: body.nickname || body.username } })
)

route('POST', '/auth/logout', () => ok(null))
route('POST', '/auth/refresh', () => ok({ token: 'mock-token-' + Date.now() }))

route('GET', '/users/me', () => ok(M.mockUser))
route('PUT', '/users/me', ({ body }) => ok({ ...M.mockUser, ...body }))
route('PUT', '/users/me/password', () => ok(null))
route('GET', '/users/me/environment', () => ok(M.mockEnvironment))
route('PUT', '/users/me/environment', ({ body }) =>
  ok({ ...M.mockEnvironment, ...body, latitude: 30.274084, longitude: 120.15507, weatherSyncedAt: dayjs().format('YYYY-MM-DDTHH:mm:ss') })
)

/* ================= 植物库 ================= */
route('GET', '/species', ({ params }) => {
  let list = M.mockSpecies
  const kw = (params.keyword || '').trim()
  if (kw) {
    list = list.filter(
      (s) =>
        s.commonName.includes(kw) ||
        (s.commonNameEn || '').toLowerCase().includes(kw.toLowerCase()) ||
        (s.scientificName || '').toLowerCase().includes(kw.toLowerCase()) ||
        (s.aliases || []).some((a) => a.includes(kw))
    )
  }
  if (params.light) list = list.filter((s) => s.lightRequirement === params.light)
  if (params.difficulty) list = list.filter((s) => s.difficulty === params.difficulty)
  if (params.toxic === 'true') list = list.filter((s) => s.toxicity === 'TOXIC')
  if (params.toxic === 'false') list = list.filter((s) => s.toxicity === 'NONE')
  return ok(paged(list, params))
})

route('GET', '/species/:id', ({ params }) => {
  const s = M.mockSpecies.find((i) => String(i.id) === String(params.id))
  if (!s) return { code: 1004, message: '植物不存在', data: null }
  return ok(s)
})

/* ================= 我的植物 ================= */
route('GET', '/my-plants', ({ params }) => {
  let list = M.mockPlants
  if (params.healthStatus) list = list.filter((p) => p.healthStatus === params.healthStatus)
  return ok(list)
})

route('GET', '/my-plants/:id', ({ params }) => {
  const p = M.mockPlants.find((i) => String(i.id) === String(params.id))
  if (!p) return { code: 1004, message: '植物不存在', data: null }
  return ok({
    ...p,
    carePlans: M.mockCarePlans.filter((c) => String(c.userPlantId) === String(p.id)),
    recentLogs: M.mockCareLogs.filter((l) => String(l.userPlantId) === String(p.id)).slice(0, 5),
    recentDiagnoses: M.mockDiagnosisRecords.filter((r) => String(r.userPlantId) === String(p.id))
  })
})

route('POST', '/my-plants', ({ body }) => {
  const species = M.mockSpecies.find((s) => String(s.id) === String(body.speciesId)) || M.mockSpecies[0]
  return ok({
    id: 999,
    ...body,
    speciesName: species.commonName,
    speciesEmoji: species.emoji,
    healthStatus: 'HEALTHY',
    carePlanGenerated: true,
    carePlan: { planType: 'WATER', effectiveIntervalDays: species.baseWaterIntervalDays, nextDueDate: dayjs().add(species.baseWaterIntervalDays, 'day').format('YYYY-MM-DD') }
  })
})

route('PUT', '/my-plants/:id', ({ params, body }) => ok({ ...M.mockPlants.find((i) => String(i.id) === String(params.id)), ...body }))
route('DELETE', '/my-plants/:id', () => ok(null))

route('GET', '/my-plants/:id/timeline', ({ params }) => {
  const photos = [
    { id: 1, date: dayjs().subtract(120, 'day').format('YYYY-MM-DD'), emoji: '🌱', note: '刚买回来，只有 4 片叶子' },
    { id: 2, date: dayjs().subtract(80, 'day').format('YYYY-MM-DD'), emoji: '🌿', note: '长了新叶' },
    { id: 3, date: dayjs().subtract(40, 'day').format('YYYY-MM-DD'), emoji: '🌿', note: '藤蔓开始垂下来' },
    { id: 4, date: dayjs().subtract(5, 'day').format('YYYY-MM-DD'), emoji: '🍂', note: '下部老叶发黄' }
  ]
  return ok({ userPlantId: Number(params.id), photos })
})

route('POST', '/my-plants/natural-language', ({ body }) => {
  const text = body.text || ''
  const hit = M.mockSpecies.find((s) => text.includes(s.commonName))
  const locationHit = ['客厅', '卧室', '阳台', '书房', '厨房', '卫生间'].find((l) => text.includes(l))
  if (!hit) {
    return ok({ parsed: null, needConfirm: true, message: '没能识别出植物种类，请手动选择' })
  }
  return ok({
    parsed: { speciesName: hit.commonName, speciesId: hit.id, emoji: hit.emoji, location: locationHit || null, lightLevel: 'MEDIUM', confidence: 0.94 },
    needConfirm: false
  })
})

/* ================= 照片 ================= */
route('POST', '/photos', ({ body, config }) => {
  const isForm = typeof FormData !== 'undefined' && config?.data instanceof FormData
  const photoType = isForm ? config.data.get('photoType') || 'WHOLE' : body.photoType || 'WHOLE'
  return ok({
    id: 501,
    url: 'mock://photo/501.jpg',
    thumbUrl: 'mock://photo/501_thumb.jpg',
    width: 1024,
    height: 768,
    fileSize: 245678,
    photoType
  })
})

route('DELETE', '/photos/:id', () => ok(null))

/* ================= 识别 ================= */
route('POST', '/recognitions', () => ok(M.mockRecognition))
// 注意：静态路径必须注册在 /:id 之前，否则会被参数路由抢先匹配
route('GET', '/recognitions/history', ({ params }) =>
  ok(
    paged(
      [
        { id: 8801, photoId: 501, provider: 'SELF_MODEL', top1Name: '绿萝', top1Confidence: 0.87, confirmed: true, createdAt: dayjs().subtract(3, 'day').format('YYYY-MM-DDTHH:mm:ss') },
        { id: 8800, photoId: 498, provider: 'PLANTNET', top1Name: '玉露', top1Confidence: 0.61, confirmed: true, createdAt: dayjs().subtract(9, 'day').format('YYYY-MM-DDTHH:mm:ss') },
        { id: 8799, photoId: 492, provider: 'SELF_MODEL', top1Name: '芦荟', top1Confidence: 0.44, confirmed: false, createdAt: dayjs().subtract(16, 'day').format('YYYY-MM-DDTHH:mm:ss') }
      ],
      params
    )
  )
)
route('GET', '/recognitions/:id', () => ok(M.mockRecognition))
route('POST', '/recognitions/:id/confirm', ({ body }) =>
  ok({ userPlantId: 999, carePlanGenerated: true, speciesId: body.speciesId })
)

/* ================= 诊断 ================= */
route('POST', '/diagnoses/disease', () => ok(M.mockDiseaseDiagnosis))

route('POST', '/diagnoses/symptom', ({ body }) => {
  // 演示降级逻辑：用户选了「说不清」→ 返回信息不足
  const pos = body?.context?.leafPosition
  if (pos === 'ANY') return ok(M.mockSymptomDiagnosisLowConfidence)
  const result = JSON.parse(JSON.stringify(M.mockSymptomDiagnosis))
  // 让响应随所选症状变化，便于验证 UI
  if ((body?.symptomCodes || []).includes('BASE_ROT')) {
    result.candidates[0].nameCn = '浇水过多 / 烂根（茎基已腐）'
    result.candidates[0].urgency = 'HIGH'
  }
  return ok(result)
})

route('GET', '/diagnoses', ({ params }) => {
  let list = M.mockDiagnosisRecords
  if (params.diagnosisType) list = list.filter((r) => r.diagnosisType === params.diagnosisType)
  return ok(paged(list, params))
})

route('GET', '/diagnoses/:id', ({ params }) => {
  const rec = M.mockDiagnosisRecords.find((r) => String(r.id) === String(params.id))
  if (!rec) return { code: 1004, message: '记录不存在', data: null }
  return ok({ ...rec, detail: rec.diagnosisType === 'DISEASE' ? M.mockDiseaseDiagnosis : M.mockSymptomDiagnosis })
})

route('PUT', '/diagnoses/:id/status', ({ params, body }) => ok({ id: Number(params.id), status: body.status }))

/* ================= 养护计划 ================= */
route('GET', '/care-plans', ({ params }) => {
  let list = M.mockCarePlans
  if (params.userPlantId) list = list.filter((c) => String(c.userPlantId) === String(params.userPlantId))
  return ok(list)
})

route('GET', '/care-plans/:id', ({ params }) => {
  const p = M.mockCarePlans.find((i) => String(i.id) === String(params.id))
  return p ? ok(p) : { code: 1004, message: '计划不存在', data: null }
})

route('POST', '/care-plans/generate', ({ body }) => {
  const list = M.mockCarePlans.filter((c) => String(c.userPlantId) === String(body.userPlantId))
  return ok(list.length ? list : M.mockCarePlans)
})

route('PUT', '/care-plans/:id', ({ params, body }) => ok({ id: Number(params.id), ...body }))
route('DELETE', '/care-plans/:id', () => ok(null))

/* ================= 提醒任务 ================= */
route('GET', '/care-tasks', ({ params }) => {
  let list = [...M.mockCareTasks]
  if (params.from) list = list.filter((t) => t.dueDate >= params.from)
  if (params.to) list = list.filter((t) => t.dueDate <= params.to)
  if (params.status) list = list.filter((t) => t.status === params.status)
  if (params.userPlantId) list = list.filter((t) => String(t.userPlantId) === String(params.userPlantId))
  return ok(list.sort((a, b) => a.dueDate.localeCompare(b.dueDate)))
})

route('GET', '/care-tasks/calendar', ({ params }) => {
  const month = params.month || dayjs().format('YYYY-MM')
  const list = M.mockCareTasks.filter((t) => t.dueDate.startsWith(month))
  const map = {}
  for (const t of list) {
    if (!map[t.dueDate]) map[t.dueDate] = []
    map[t.dueDate].push(t)
  }
  return ok({
    month,
    days: Object.keys(map)
      .sort()
      .map((date) => ({ date, tasks: map[date] })),
    summary: {
      total: list.length,
      pending: list.filter((t) => t.status === 'PENDING').length,
      done: list.filter((t) => t.status === 'DONE').length,
      overdue: list.filter((t) => t.status === 'OVERDUE').length
    }
  })
})

route('GET', '/care-tasks/today', () => {
  const t = dayjs().format('YYYY-MM-DD')
  return ok(M.mockCareTasks.filter((x) => x.dueDate === t || x.status === 'OVERDUE'))
})

route('POST', '/care-tasks/:id/complete', ({ params, body }) => {
  const task = M.mockCareTasks.find((t) => String(t.id) === String(params.id))
  return ok({
    taskId: Number(params.id),
    status: 'DONE',
    completedAt: dayjs().format('YYYY-MM-DDTHH:mm:ss'),
    careLogId: 4400 + Number(params.id),
    nextTask: { id: Number(params.id) + 100, dueDate: dayjs().add(task?.taskType === 'WATER' ? 5 : 21, 'day').format('YYYY-MM-DD') },
    amount: body?.amount
  })
})

route('POST', '/care-tasks/:id/skip', ({ params }) => ok({ taskId: Number(params.id), status: 'SKIPPED' }))
route('POST', '/care-tasks/:id/snooze', ({ params, body }) => ok({ taskId: Number(params.id), dueDate: body?.dueDate }))

/* ================= 养护日志 ================= */
route('GET', '/care-logs', ({ params }) => {
  let list = [...M.mockCareLogs]
  if (params.userPlantId) list = list.filter((l) => String(l.userPlantId) === String(params.userPlantId))
  if (params.logType) list = list.filter((l) => l.logType === params.logType)
  if (params.from) list = list.filter((l) => l.loggedAt >= params.from)
  if (params.to) list = list.filter((l) => l.loggedAt <= params.to)
  return ok(paged(list.sort((a, b) => b.loggedAt.localeCompare(a.loggedAt)), params))
})

route('POST', '/care-logs', ({ body }) => ok({ id: 9999, ...body, loggedAt: body.loggedAt || dayjs().format('YYYY-MM-DDTHH:mm:ss') }))
route('PUT', '/care-logs/:id', ({ params, body }) => ok({ id: Number(params.id), ...body }))
route('DELETE', '/care-logs/:id', () => ok(null))

/* ================= 问答 ================= */
route('GET', '/qa/sessions', () => ok(M.mockQaSessions))
route('POST', '/qa/sessions', ({ body }) => ok({ id: 99, title: (body.content || '新会话').slice(0, 20), messageCount: 0, updatedAt: dayjs().format('YYYY-MM-DDTHH:mm:ss') }))
route('GET', '/qa/sessions/:id', ({ params }) => ok(M.mockQaMessages.filter((m) => String(m.sessionId) === String(params.id))))

route('POST', '/qa/sessions/:id/messages', ({ body }) => {
  const q = body.content || ''
  return ok({
    userMessage: { id: 900, sessionId: Number(99), role: 'USER', content: q, createdAt: dayjs().format('YYYY-MM-DDTHH:mm:ss') },
    assistantMessage: {
      id: 901,
      sessionId: 99,
      role: 'ASSISTANT',
      content:
        '（Mock 回答）这个问题需要后端接入 RAG 后返回真实内容。\n\n接入后会走这套流程：\n1. 把你的问题结合当前植物补全\n2. 在知识库里检索最相关的 5 个片段\n3. 把片段和问题一起交给大模型生成回答\n4. 回答里标注每条结论的来源\n\n骨架阶段这里只展示交互位置，检索到的资料和引用来源会显示在回答上方。',
      createdAt: dayjs().format('YYYY-MM-DDTHH:mm:ss'),
      retrievedChunks: [
        { chunkId: 101, title: '绿萝·浇水与积水判断', score: 0.91 },
        { chunkId: 145, title: '室内观叶植物黄叶的常见原因', score: 0.87 }
      ],
      citations: [{ title: '绿萝·浇水与积水判断', source: 'MANUAL', url: null }],
      model: 'mock',
      latencyMs: 1200,
      feedback: null
    }
  })
})

route('POST', '/qa/messages/:id/feedback', ({ params, body }) => ok({ id: Number(params.id), feedback: body.feedback }))

/* ================= 统计 ================= */
route('GET', '/stats/overview', () => ok(M.mockStatsOverview))
route('GET', '/stats/watering-trend', () => ok(M.mockWateringTrend))
route('GET', '/stats/health-distribution', () => ok(M.mockStatsOverview.healthDistribution))

/* ================= 天气 ================= */
route('GET', '/weather/current', () => ok(M.mockWeather))
route('POST', '/weather/sync', () => ok(M.mockWeather))

/* ================= 管理端 ================= */
route('GET', '/admin/species', ({ params }) => ok(paged(M.mockSpecies, params)))
route('PUT', '/admin/species/:id', ({ params, body }) => ok({ id: Number(params.id), ...body }))

route('GET', '/admin/diseases', ({ params }) => {
  let list = M.mockDiseases
  if (params.category) list = list.filter((d) => d.category === params.category)
  return ok(paged(list, params))
})

route('GET', '/admin/symptom-rules', ({ params }) => {
  let list = M.mockSymptomRules
  if (params.symptomCode) list = list.filter((r) => r.symptomCode === params.symptomCode)
  if (params.causeCode) list = list.filter((r) => r.causeCode === params.causeCode)
  return ok(list)
})
route('PUT', '/admin/symptom-rules/:id', ({ params, body }) => ok({ id: Number(params.id), ...body }))
route('POST', '/admin/symptom-rules', ({ body }) => ok({ id: 999, ...body }))
route('DELETE', '/admin/symptom-rules/:id', () => ok(null))

route('GET', '/admin/knowledge/articles', ({ params }) => {
  let list = M.mockKnowledgeArticles
  if (params.source) list = list.filter((a) => a.source === params.source)
  if (params.embedStatus) list = list.filter((a) => a.embedStatus === params.embedStatus)
  return ok(paged(list, params))
})
route('POST', '/admin/knowledge/articles/:id/reindex', ({ params }) => ok({ id: Number(params.id), embedStatus: 'DONE', chunkCount: 4 }))

route('GET', '/admin/stats/ai-usage', () => ok(M.mockAdminStats))
route('GET', '/admin/stats/recognition-accuracy', () => ok(M.mockAdminStats))

/* ================= 适配器 ================= */
function parseQuery(qs) {
  const out = {}
  if (!qs) return out
  for (const pair of qs.split('&')) {
    if (!pair) continue
    const [k, v = ''] = pair.split('=')
    out[decodeURIComponent(k)] = decodeURIComponent(v)
  }
  return out
}

function safeParse(data) {
  if (data === null || data === undefined || data === '') return {}
  if (typeof data === 'string') {
    try {
      return JSON.parse(data)
    } catch {
      return {}
    }
  }
  if (typeof FormData !== 'undefined' && data instanceof FormData) {
    const obj = {}
    data.forEach((v, k) => {
      obj[k] = v
    })
    return obj
  }
  return data
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

export function createMockAdapter() {
  return async function mockAdapter(config) {
    const method = (config.method || 'get').toUpperCase()
    let url = config.url || ''
    const base = config.baseURL || ''
    if (base && url.startsWith(base)) url = url.slice(base.length)
    const [path, qs] = url.split('?')

    const params = { ...parseQuery(qs), ...(config.params || {}) }
    const body = safeParse(config.data)

    // 模拟网络延迟，让 loading 状态可见
    await sleep(120 + Math.random() * 200)

    const hit = matchRoute(method, path)
    let envelope
    if (hit) {
      envelope = hit.handler({ params, body, config, pathParams: hit.params, ...hit.params })
    } else {
      // 骨架阶段：未实现的接口返回空数据并在控制台提示，避免联调前满屏报错
      // eslint-disable-next-line no-console
      console.warn(`[Mock] 未实现的接口：${method} ${path}`)
      envelope = ok(null)
    }

    return {
      data: envelope,
      status: 200,
      statusText: 'OK',
      headers: {},
      config
    }
  }
}
