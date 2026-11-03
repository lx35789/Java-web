# 植语助手 GreenThumb AI — API 接口设计

> 配套 `02-项目重设计方案.md`。Base URL：`/api/v1`

---

## 1. 通用约定

### 1.1 认证

除注册/登录外所有接口需携带：

```
Authorization: Bearer <JWT>
```

Token 失效后通过 `POST /auth/refresh` 续期。登出时把 JWT 写入 Redis 黑名单（TTL = 剩余有效期）。

### 1.2 统一响应体

```json
{
  "code": 0,
  "message": "ok",
  "data": { },
  "traceId": "a1b2c3d4"
}
```

- `code = 0` 表示成功，非 0 为业务错误
- HTTP 状态码仍按语义返回（400/401/403/404/429/500）

### 1.3 错误码

| code | HTTP | 含义 |
|---|---|---|
| 0 | 200 | 成功 |
| 1001 | 400 | 参数校验失败 |
| 1002 | 401 | 未登录 / Token 失效 |
| 1003 | 403 | 无权限（非资源所有者） |
| 1004 | 404 | 资源不存在 |
| 2001 | 409 | 用户名已存在 |
| 3001 | 400 | 图片格式不支持（仅 jpg/png，≤10MB） |
| 3002 | 503 | 识别服务不可用（自研 + Pl@ntNet 均失败） |
| 3003 | 429 | **AI 接口配额已用尽**（每用户 20 次/天） |
| 4001 | 400 | 症状信息不足，无法判因（引导补充） |
| 5001 | 429 | 通用限流 |
| 9999 | 500 | 服务内部错误 |

### 1.4 分页

请求：`?page=1&size=20`（page 从 1 开始，size ≤ 100）

响应：

```json
{
  "code": 0,
  "data": {
    "list": [],
    "total": 137,
    "page": 1,
    "size": 20
  }
}
```

### 1.5 AI 接口限流响应头

所有会调用模型的接口返回：

```
X-RateLimit-Limit: 20
X-RateLimit-Remaining: 13
X-RateLimit-Reset: 1757990400
```

> ★ 必须有这个。Pl@ntNet 免费额度仅 **500 次/天**，后端需在 Redis 维护全局计数，接近上限时自动降级为"仅自研模型"。

---

## 2. 认证与用户

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/auth/register` | 注册 |
| POST | `/auth/login` | 登录，返回 JWT |
| POST | `/auth/refresh` | 刷新 Token |
| POST | `/auth/logout` | 登出 |
| GET | `/users/me` | 当前用户信息 |
| PUT | `/users/me` | 修改昵称/头像/邮箱 |
| PUT | `/users/me/password` | 改密码 |
| GET | `/users/me/environment` | 查看环境设置 |
| PUT | `/users/me/environment` | 修改环境设置 |

**PUT `/users/me/environment`**

```json
// 请求
{
  "city": "杭州",
  "hasAirConditioner": true,
  "hasHeating": false,
  "indoorHumidityLevel": "LOW",
  "weatherSyncEnabled": true
}

// 响应
{
  "code": 0,
  "data": {
    "city": "杭州",
    "latitude": 30.274084,
    "longitude": 120.155070,
    "timezone": "Asia/Shanghai",
    "hasAirConditioner": true,
    "hasHeating": false,
    "indoorHumidityLevel": "LOW",
    "weatherSyncEnabled": true,
    "weatherSyncedAt": "2026-09-15T10:30:00"
  }
}
```

> 后端首次接收到城市名时调用 Open-Meteo Geocoding 解析经纬度并**缓存到 `user_environment`**，后续不再重复解析。★ 注意 Open-Meteo 天气接口只接受经纬度，不接受城市名。

---

## 3. 植物库（F2）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/species` | 列表（搜索/筛选），Redis 缓存 |
| GET | `/species/{id}` | 详情（含养护规则） |

**GET `/species?keyword=绿萝&light=LOW&difficulty=EASY&toxic=false&page=1&size=20`**

```json
{
  "code": 0,
  "data": {
    "list": [
      {
        "id": 1,
        "commonName": "绿萝",
        "commonNameEn": "Pothos",
        "scientificName": "Epipremnum aureum",
        "aliases": ["魔鬼藤", "黄金葛"],
        "family": "天南星科",
        "difficulty": "EASY",
        "lightRequirement": "MEDIUM",
        "baseWaterIntervalDays": 7,
        "fertilizerIntervalDays": 30,
        "toxicity": "TOXIC",
        "toxicTo": [{"target": "cat", "level": "HIGH"}, {"target": "dog", "level": "MEDIUM"}],
        "imageUrl": "https://.../pothos.jpg",
        "modelClassIndex": 33
      }
    ],
    "total": 1, "page": 1, "size": 20
  }
}
```

---

## 4. 我的植物（F3）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/my-plants` | 我的植物列表 |
| POST | `/my-plants` | 添加 |
| GET | `/my-plants/{id}` | 详情（含最近日志、当前计划、健康状态） |
| PUT | `/my-plants/{id}` | 编辑 |
| DELETE | `/my-plants/{id}` | 删除（软删） |
| GET | `/my-plants/{id}/timeline` | 成长时间线（P2） |
| POST | `/my-plants/natural-language` | 自然语言添加（P2） |

**POST `/my-plants`**

```json
// 请求
{
  "speciesId": 1,
  "nickname": "小绿",
  "location": "客厅",
  "lightLevel": "MEDIUM",
  "potSize": "MEDIUM",
  "potMaterial": "CERAMIC",
  "soilType": "通用营养土",
  "acquiredDate": "2026-08-01",
  "note": "朋友送的"
}

// 响应
{
  "code": 0,
  "data": {
    "id": 12,
    "speciesId": 1,
    "nickname": "小绿",
    "healthStatus": "HEALTHY",
    "carePlanGenerated": true,
    "carePlan": {
      "planType": "WATER",
      "effectiveIntervalDays": 5,
      "nextDueDate": "2026-09-20"
    }
  }
}
```

> 添加植物后**自动生成初始养护计划**，这是 S1 场景的闭环。

**POST `/my-plants/natural-language`**（P2）

```json
// 请求
{ "text": "我新买了一盆龟背竹，放在客厅" }

// 响应
{
  "code": 0,
  "data": {
    "parsed": {
      "speciesName": "龟背竹",
      "speciesId": 7,
      "location": "客厅",
      "confidence": 0.94
    },
    "needConfirm": false
  }
}
```

> 用 LLM function calling / 结构化输出解析。解析不确定时 `needConfirm=true`，前端引导用户确认物种。

---

## 5. 照片（F3/F4）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/photos` | 上传（multipart/form-data） |
| GET | `/photos/{id}` | 获取元信息 |
| DELETE | `/photos/{id}` | 删除 |

**POST `/photos`**

```
Content-Type: multipart/form-data
file=<binary>
photoType=WHOLE|LEAF|DISEASE
userPlantId=<optional>
```

```json
{
  "code": 0,
  "data": {
    "id": 501,
    "url": "https://.../501.jpg",
    "thumbUrl": "https://.../501_thumb.jpg",
    "width": 1024,
    "height": 768,
    "fileSize": 245678,
    "photoType": "LEAF"
  }
}
```

> 服务端需做 **EXIF 旋转矫正**（手机竖拍照片若不处理会横过来），并生成缩略图。

---

## 6. 物种识别（F4）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/recognitions` | 识别（传 photoId） |
| GET | `/recognitions/{id}` | 查询结果 |
| POST | `/recognitions/{id}/confirm` | 采纳 / 纠正 |
| GET | `/recognitions/history` | 历史记录 |

**POST `/recognitions`**

```json
// 请求
{ "photoId": 501 }

// 响应
{
  "code": 0,
  "data": {
    "recordId": 8801,
    "provider": "SELF_MODEL",
    "modelVersion": "mobilenetv3-v1.0",
    "latencyMs": 340,
    "results": [
      {
        "rank": 1,
        "speciesId": 1,
        "commonName": "绿萝",
        "scientificName": "Epipremnum aureum",
        "confidence": 0.87,
        "imageUrl": "https://.../pothos.jpg",
        "careSummary": "耐阴，7天左右浇一次，忌积水"
      },
      {
        "rank": 2, "speciesId": 33, "commonName": "心叶蔓绿绒", "confidence": 0.08
      },
      {
        "rank": 3, "speciesId": 12, "commonName": "合果芋", "confidence": 0.03
      }
    ],
    "lowConfidence": false,
    "fallbackUsed": false
  }
}
```

**★ 兜底触发逻辑**（后端实现）：

```
result = self_model(photo)
if result.top1_confidence < 0.50 and plantnet_quota_available():
    pn = plantnet.identify(photo)
    result = merge(result, pn)      # 按学名去重，置信度排序，取 Top-3
    result.fallbackUsed = true
elif result.top1_confidence < 0.50:
    result.lowConfidence = true     # 配额耗尽，仅提示置信度低
```

**POST `/recognitions/{id}/confirm`**

```json
// 请求（采纳）
{ "speciesId": 1, "createPlant": true, "userPlantForm": { "location": "客厅", "lightLevel": "MEDIUM" } }

// 请求（纠正）
{ "speciesId": 33, "createPlant": true, ... }

// 响应
{
  "code": 0,
  "data": {
    "userPlantId": 12,
    "carePlanGenerated": true
  }
}
```

> ★ **用户纠正必须落库**（`recognition_record.confirmed` + 纠正后的 `species_id`）。这是真实场景准确率的统计来源，也是未来主动学习的标注种子。

---

## 7. 病害 / 症状诊断（F10 / F11）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/diagnoses/disease` | 病害识别（病理性） |
| POST | `/diagnoses/symptom` | 症状诊断（生理性） |
| GET | `/diagnoses` | 我的诊断记录 |
| GET | `/diagnoses/{id}` | 详情 |
| PUT | `/diagnoses/{id}/status` | 更新处理状态 |

### 7.1 POST `/diagnoses/disease`

```json
// 请求
{ "photoId": 502, "userPlantId": 12 }

// 响应
{
  "code": 0,
  "data": {
    "recordId": 9001,
    "diagnosisType": "DISEASE",
    "provider": "SELF_MODEL",
    "predicted": {
      "code": "bacterial_wilt",
      "nameCn": "细菌性萎蔫",
      "category": "BACTERIAL",
      "confidence": 0.82,
      "severity": "HIGH"
    },
    "topK": [
      { "code": "bacterial_wilt", "nameCn": "细菌性萎蔫", "confidence": 0.82 },
      { "code": "manganese_toxicity", "nameCn": "锰中毒", "confidence": 0.11 }
    ],
    "symptoms": "叶片快速萎蔫发黄，茎部出现水渍状褐斑…",
    "treatment": "1. 立即隔离…2. 剪除病叶…",
    "prevention": "避免叶面长期积水，保持通风"
  }
}
```

> ★ 处理建议来自 `disease_info` 表，**不来自模型**。

### 7.2 POST `/diagnoses/symptom` ★ 本次修正的核心接口

```json
// 请求
{
  "userPlantId": 12,
  "photoId": 503,
  "symptomCodes": ["YELLOW_LEAF"],
  "context": {
    "leafPosition": "OLD",
    "veinPattern": "NONE",
    "causeHint": null
  }
}
```

```json
// 响应
{
  "code": 0,
  "data": {
    "recordId": 9002,
    "diagnosisType": "SYMPTOM",
    "confidenceLevel": "HIGH",
    "candidates": [
      {
        "rank": 1,
        "causeCode": "OVERWATER",
        "nameCn": "浇水过多 / 烂根",
        "category": "WATER",
        "urgency": "HIGH",
        "confidence": 1.0,
        "evidence": [
          "黄叶从下部老叶开始 —— 这是积水烂根的典型特征（缺水通常从新叶或全株开始）",
          "你近 14 天浇水 5 次，而绿萝建议约 7 天一次（约 2 次），超出 2.5 倍",
          "花盆是釉面陶瓷盆，透气性差，水分不易蒸发"
        ],
        "actionPlan": [
          "立即停止浇水，把花盆移到通风处",
          "检查盆底排水孔是否堵塞",
          "如果土壤仍然潮湿且有异味，建议脱盆检查根系，剪除发黑腐烂的根",
          "恢复浇水后，改为「手指插入土中 2–3cm，干了再浇」"
        ],
        "hitRuleIds": [1, 2, 3]
      },
      {
        "rank": 2,
        "causeCode": "NORMAL_SENESCENCE",
        "nameCn": "正常代谢 / 老叶更新",
        "confidence": 0.0,
        "evidence": [],
        "actionPlan": ["若仅底部 1–2 片老叶发黄，其他叶片正常，可不处理"]
      }
    ],
    "suggestion": "综合来看，最可能是浇水过多导致根系缺氧。建议先停水观察 5–7 天…",
    "citations": [
      { "title": "绿萝·浇水", "source": "MANUAL", "url": null }
    ]
  }
}
```

**★ 不确定性降级**（重要）：

```json
// 置信度 < 0.4 时的响应
{
  "code": 0,
  "data": {
    "diagnosisType": "SYMPTOM",
    "confidenceLevel": "LOW",
    "candidates": [],
    "needMoreInfo": true,
    "followUpQuestions": [
      "黄叶是从下面的老叶开始，还是上面的新叶？",
      "最近两周大概浇了几次水？",
      "这盆植物摆放的位置光线怎么样？"
    ],
    "fallbackSuggestion": "信息还不够充分，你可以补充上面的信息，或者直接问我"
  }
}
```

> ★ **信息不足时绝不武断下结论。** 这是"会解释为什么"这一定位的底线。

**后端实现要点**：`context` 中的 `wateringRatio`、`lightLevel`、`humidity`、`temp`、`season`、`potMaterial`、`fertilizing` 全部由后端从 `care_log` / `user_plant` / `user_environment` / 天气 API **自动计算注入**，前端只需传用户可见的选择项（`leafPosition` 等）。

---

## 8. 养护计划（F5）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/care-plans?userPlantId=` | 某植物的计划 |
| POST | `/care-plans/generate` | 重新生成 |
| GET | `/care-plans/{id}` | 详情 |
| PUT | `/care-plans/{id}` | 手动覆盖间隔 |
| DELETE | `/care-plans/{id}` | 停用 |

**GET `/care-plans?userPlantId=12`**

```json
{
  "code": 0,
  "data": [
    {
      "id": 301,
      "planType": "WATER",
      "season": "SUMMER",
      "baseIntervalDays": 7,
      "effectiveIntervalDays": 5,
      "coefficientDetail": {
        "season": 0.8, "light": 1.0, "temp": 1.0,
        "humidity": 1.0, "pot": 1.1, "ac": 0.9
      },
      "calculation": "基准 7 天 × 夏季 0.8 × 空调房 0.9 × 釉面陶瓷盆 1.1 = 5.5 天 → 取 5 天",
      "llmExplain": "本周建议浇水 1–2 次。绿萝夏季生长旺盛需水偏多；但空调房空气干燥、釉面陶瓷盆透气性差…",
      "nextDueDate": "2026-09-20",
      "isActive": true,
      "generatedBy": "RULE"
    },
    {
      "id": 302,
      "planType": "FERTILIZE",
      "baseIntervalDays": 30,
      "effectiveIntervalDays": 21,
      "calculation": "基准 30 天 × 生长期 0.7 = 21 天",
      "nextDueDate": "2026-09-28",
      "isActive": true
    }
  ]
}
```

> ★ `coefficientDetail` + `calculation` 是"说得清"价值主张的 API 层落地，前端直接渲染成拆解表格。

---

## 9. 提醒任务（F6）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/care-tasks?from=&to=&status=` | 任务列表 |
| GET | `/care-tasks/calendar?month=2026-09` | 日历视图 |
| GET | `/care-tasks/today` | 今日待办 |
| POST | `/care-tasks/{id}/complete` | 标记完成 |
| POST | `/care-tasks/{id}/skip` | 跳过 |
| POST | `/care-tasks/{id}/snooze` | 延后 |

**GET `/care-tasks/calendar?month=2026-09`**

```json
{
  "code": 0,
  "data": {
    "month": "2026-09",
    "days": [
      {
        "date": "2026-09-20",
        "tasks": [
          { "id": 7001, "taskType": "WATER", "userPlantId": 12, "plantNickname": "小绿", "speciesName": "绿萝", "status": "PENDING" }
        ]
      },
      {
        "date": "2026-09-21",
        "tasks": [
          { "id": 7002, "taskType": "WATER", "userPlantId": 13, "plantNickname": "大叶", "speciesName": "龟背竹", "status": "PENDING" },
          { "id": 7003, "taskType": "FERTILIZE", "userPlantId": 12, "plantNickname": "小绿", "speciesName": "绿萝", "status": "PENDING" }
        ]
      }
    ],
    "summary": { "total": 18, "pending": 15, "done": 2, "overdue": 1 }
  }
}
```

**POST `/care-tasks/{id}/complete`**

```json
// 请求
{ "amount": 200, "unit": "ml", "note": "土有点干", "photoId": null }

// 响应
{
  "code": 0,
  "data": {
    "taskId": 7001,
    "status": "DONE",
    "completedAt": "2026-09-20T09:15:00",
    "careLogId": 4401,
    "nextTask": { "id": 7010, "dueDate": "2026-09-25" }
  }
}
```

> 标记完成时**自动生成 care_log 并回填关联**，同时计算下一个到期任务。这是 F6→F7 的闭环。

---

## 10. 养护日志（F7）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/care-logs?userPlantId=&type=&from=&to=` | 日志列表 |
| POST | `/care-logs` | 补录日志 |
| PUT | `/care-logs/{id}` | 修改 |
| DELETE | `/care-logs/{id}` | 删除 |

---

## 11. 知识库问答（F8）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/qa/sessions` | 会话列表 |
| POST | `/qa/sessions` | 新建会话 |
| GET | `/qa/sessions/{id}` | 会话消息 |
| POST | `/qa/sessions/{id}/messages` | 提问（**SSE 流式**） |
| POST | `/qa/messages/{id}/feedback` | 评价（有用/无用） |

**POST `/qa/sessions/{id}/messages`**

```json
// 请求
{
  "content": "绿萝叶子发黄怎么办",
  "userPlantId": 12
}
```

**响应：`text/event-stream`**

```
event: retrieval
data: {"chunks":[{"chunkId":101,"title":"绿萝·浇水","score":0.89},{"chunkId":145,"title":"黄叶的常见原因","score":0.84}]}

event: delta
data: {"text":"绿萝叶子发黄最常见的原因是"}

event: delta
data: {"text":"浇水过多。你可以先摸一下土壤："}

event: citations
data: {"list":[{"title":"绿萝·浇水","source":"MANUAL","url":null},{"title":"Houseplant yellowing leaves","source":"WIKIPEDIA","url":"https://en.wikipedia.org/..."}]}

event: done
data: {"messageId":5501,"latencyMs":3200,"tokens":{"prompt":1800,"completion":210}}
```

> ★ 先发 `retrieval` 事件，前端可以立即展示"正在检索到的资料"，用户能感知到**回答有依据**——这正是价值主张的可视化。

---

## 12. 统计看板（F9）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/stats/overview` | 总览 |
| GET | `/stats/watering-trend?weeks=8` | 浇水趋势 |
| GET | `/stats/health-distribution` | 健康状态分布 |

**GET `/stats/overview`**

```json
{
  "code": 0,
  "data": {
    "plantCount": 8,
    "speciesCount": 6,
    "healthDistribution": { "HEALTHY": 6, "WARNING": 1, "DISEASED": 1, "DEAD": 0 },
    "careStats": {
      "waterCountTotal": 42,
      "waterCountThisMonth": 9,
      "fertilizeCountThisMonth": 2,
      "onTimeRate": 0.86,
      "overdueCount": 2
    },
    "diagnosisStats": { "total": 3, "resolved": 2, "open": 1 },
    "qaCount": 17
  }
}
```

---

## 13. 天气（F12，P2）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/weather/current` | 当前天气（按用户城市） |
| POST | `/weather/sync` | 手动同步 |

```json
{
  "code": 0,
  "data": {
    "city": "杭州",
    "temperature": 31.5,
    "relativeHumidity": 48,
    "sunshineDuration": 28800,
    "precipitation": 0.0,
    "observedAt": "2026-09-15T10:00:00",
    "source": "OPEN_METEO",
    "impactOnCare": {
      "tempCoefficient": 0.85,
      "humidityCoefficient": 1.0,
      "note": "气温 31.5°C，高于 30°C，浇水间隔已缩短"
    }
  }
}
```

> ★ `impactOnCare` 让天气与养护计划的关系显式可见，而不是悄悄改个数字。

---

## 14. 管理端

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/POST/PUT/DELETE | `/admin/species` | 植物库维护 |
| GET/POST/PUT/DELETE | `/admin/diseases` | 病害信息维护 |
| GET/POST/PUT/DELETE | `/admin/symptom-rules` | **症状规则维护**（调权重） |
| GET/POST/PUT/DELETE | `/admin/knowledge/articles` | 知识库文章 |
| POST | `/admin/knowledge/articles/{id}/reindex` | 重新分块 + embedding |
| GET | `/admin/stats/ai-usage` | AI 调用统计与配额 |
| GET | `/admin/stats/recognition-accuracy` | **基于用户纠正的真实准确率** |

**GET `/admin/stats/recognition-accuracy`**

```json
{
  "code": 0,
  "data": {
    "totalRecognitions": 428,
    "confirmedCount": 301,
    "correctedCount": 47,
    "realWorldTop1Accuracy": 0.865,
    "bySpecies": [
      { "speciesId": 1, "name": "绿萝", "total": 62, "corrected": 3, "accuracy": 0.952 }
    ],
    "plantnetQuota": { "used": 213, "limit": 500, "date": "2026-09-15" }
  }
}
```

> ★ 这个接口是"数据闭环"的落地证明——**真实场景准确率**比测试集数字更有说服力，答辩时是硬材料。

---

## 15. AI 服务内部接口（backend → ai-service）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/v1/recognize/species` | 物种识别 |
| POST | `/v1/recognize/disease` | 病害识别 |
| POST | `/v1/embed` | 文本向量化 |
| GET | `/v1/models/info` | 模型版本与状态 |
| GET | `/health` | 健康检查 |

**POST `/v1/recognize/species`**

```
Content-Type: multipart/form-data
image=<binary>
topK=3
```

```json
{
  "modelVersion": "mobilenetv3-v1.0",
  "latencyMs": 120,
  "results": [
    { "classIndex": 33, "label": "Pothos", "scientificName": "Epipremnum aureum", "confidence": 0.87 },
    { "classIndex": 12, "label": "Philodendron", "confidence": 0.08 },
    { "classIndex": 5, "label": "Syngonium", "confidence": 0.03 }
  ]
}
```

> AI 服务返回**模型原始类别**，由 backend 通过 `plant_species.model_class_index` 映射为业务物种。**映射关系不放在 AI 服务里**——因为植物库会变，模型不会。

---

## 16. 接口清单速查

| 模块 | 接口数 |
|---|---|
| 认证与用户 | 9 |
| 植物库 | 2 |
| 我的植物 | 7 |
| 照片 | 3 |
| 物种识别 | 4 |
| 病害 / 症状诊断 | 5 |
| 养护计划 | 5 |
| 提醒任务 | 6 |
| 养护日志 | 4 |
| 知识库问答 | 5 |
| 统计看板 | 3 |
| 天气 | 2 |
| 管理端 | 9 |
| **用户端合计** | **约 55** |
| AI 服务内部 | 5 |
