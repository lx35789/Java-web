# 识别服务：模型放置与 API 说明

> 给只需要「后端识别功能」的成员。看完这篇就能把模型接上、把接口调通。

---

## 一、放置模型文件

拿到 `greenthumb-models-v1.zip`（29.9 MB），解压后按下图放置：

```
ai-service/models/
├── disease.onnx                    通用模型（29 类，覆盖 6 物种，93.69%）
├── labels.json
├── meta.json
└── specialized/
    └── aloe/
        ├── disease.onnx            芦荟专用模型（5 类，74.81%）
        ├── labels.json
        └── meta.json
```

**就是 zip 里的结构，直接解压到 `ai-service/models/` 即可。**

三个文件缺一不可：
- `disease.onnx` —— 模型本体
- `labels.json` —— 类别映射（`code` / 中文名 / 物种），**顺序必须和模型输出一致**
- `meta.json` —— 输入尺寸与骨干，推理时按它做预处理

> `meta.json` 里的 `input_size` 很关键：通用模型是 160，芦荟专用是 224。
> 用错尺寸准确率会明显掉。

---

## 二、启动 AI 服务

```bash
cd ai-service
pip install -r requirements.txt
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

验证是否加载成功：

```bash
curl http://127.0.0.1:8000/health
```

期望返回：

```json
{
  "status": "UP",
  "diseaseModel": {
    "available": true,
    "reason": null,
    "numClasses": 29,
    "meta": { "input_size": 160, "arch": "mobilenetv3_large_100", "num_classes": 29 }
  },
  "specialized": {
    "aloe": { "available": true, "numClasses": 5, "species": ["Aloe"] }
  }
}
```

`available: false` 说明模型没放对位置，`reason` 字段会写明原因（通常是路径不对）。

---

## 三、AI 服务 API（端口 8000）

### `POST /v1/disease/predict`

| 参数 | 位置 | 必填 | 说明 |
|---|---|---|---|
| `file` | multipart | ✅ | 图片文件（jpg/png） |
| `top_k` | query | ❌ | 返回候选数，默认 3 |
| `species` | query | ❌ | 物种 key，有专用模型时优先用专用模型 |

**物种 key 对照表**（必须与模型 labels 里的 `species` 一致）：

| 中文名 | species key |
|---|---|
| 芦荟 | `Aloe` |
| 绿萝 | `Money_Plant` |
| 月季 | `Rose` |
| 姜黄 | `Turmeric` |
| 扶桑 | `Hibiscus` |
| 菊花 | `Chrysanthemum` |

**调用示例：**

```bash
# 不指定物种 → 走通用模型
curl -X POST "http://127.0.0.1:8000/v1/disease/predict?top_k=3" \
     -F "file=@leaf.jpg"

# 指定芦荟 → 走芦荟专用模型（准确率更高）
curl -X POST "http://127.0.0.1:8000/v1/disease/predict?top_k=3&species=Aloe" \
     -F "file=@leaf.jpg"
```

**响应示例（实测）：**

```json
{
  "available": true,
  "reason": null,
  "topK": [
    { "code": "Aloe_Rust",        "nameCn": "芦荟·锈病", "species": "Aloe", "disease": "Rust",        "confidence": 0.9802 },
    { "code": "Aloe_Healthy",     "nameCn": "芦荟·健康", "species": "Aloe", "disease": "Healthy",     "confidence": 0.0021 },
    { "code": "Aloe_Anthracnose", "nameCn": "芦荟·炭疽病", "species": "Aloe", "disease": "Anthracnose", "confidence": 0.0015 }
  ],
  "model": "aloe",
  "usedSpecies": "Aloe",
  "routedTo": "aloe",
  "fallback": false
}
```

**字段说明：**

| 字段 | 含义 |
|---|---|
| `topK` | 按置信度降序的候选，`code` 可直接关联 `disease_info` 表查处置建议 |
| `routedTo` | 实际用的模型：`general` / `aloe` / … |
| `fallback` | `true` 表示传入的 species 没有专用模型，已退回通用模型 |
| `available` | `false` 时 `topK` 为空数组，看 `reason` |

### `GET /health`
模型状态，见上文。

### `GET /v1/disease/labels`
返回全部类别映射，可用于前端展示或核对。

---

## 四、后端 API（端口 8080）

识别功能在后端包了两层，按需选：

### 方式 A：只要识别结果 → `/api/v1/diagnoses/disease`

```http
POST /api/v1/diagnoses/disease
Authorization: Bearer <token>
Content-Type: application/json

{ "photoId": 139, "userPlantId": 45 }
```

返回（节选）：

```json
{
  "code": 0,
  "data": {
    "recordId": 67,
    "diagnosisType": "DISEASE",
    "predicted": {
      "code": "Money_Plant_Chlorosis",
      "nameCn": "绿萝·黄化病",
      "category": "PHYSIOLOGICAL",
      "confidence": 0.946,
      "severity": "MEDIUM"
    },
    "topK": [ ... ],
    "symptoms": "叶片整体或脉间发黄，叶脉常保持绿色…",
    "treatment": "1. 先判断是新叶黄…",
    "prevention": "规律施肥；使用透气基质…",
    "available": true,
    "latencyMs": 28
  }
}
```

**这一步只走自训模型，不调大模型**，28ms 左右。

### 方式 B：要识别 + AI 讲解 → `/api/v1/diagnoses/ai`

```http
POST /api/v1/diagnoses/ai
Authorization: Bearer <token>
Content-Type: application/json

{ "photoId": 139, "userPlantId": 45, "speciesId": 1, "question": "叶边发黄两周了" }
```

返回多了 `source` / `evidence` / `needMoreInfo` 等字段：

```json
{
  "code": 0,
  "data": {
    "source": "MODEL",
    "diseaseName": "芦荟·锈病",
    "confidence": 0.9802,
    "inKnowledgeBase": true,
    "evidence": ["叶背出现橙黄色至锈褐色粉末状孢子堆…"],
    "treatment": ["立即剪掉叶背有橙黄色粉末状斑点的叶片…", "…"],
    "prevention": ["保持通风；避免叶片长期潮湿"],
    "needMoreInfo": false,
    "disclaimer": "AI 结论仅供参考，不构成农药使用指导。"
  }
}
```

| `source` | 含义 |
|---|---|
| `MODEL` | 模型置信度够，直接给结论 |
| `MODEL_TOP3` | 置信度不足 60%，给最可能的答案并标注「待确认」 |
| `VLM_CATEGORY` | 该物种没被模型覆盖，只给病害大类 |
| `UNAVAILABLE` | 模型或大模型不可用，`modelNote` 说明原因 |

> 需要配 `GREENTHUMB_LLM_API_KEY` 才会有 AI 讲解；不配时方式 B 会降级，但方式 A 不受影响。

### 前置步骤：先上传图片

两个诊断接口都要 `photoId`，先调上传：

```http
POST /api/v1/photos?photoType=DISEASE
Authorization: Bearer <token>
Content-Type: multipart/form-data

file=@leaf.jpg
```

返回 `{ "code": 0, "data": { "id": 139, "url": "/files/...", ... } }`，取 `id` 即可。

### 鉴权

```http
POST /api/v1/auth/register   { "username": "xxx", "password": "Test1234", "nickname": "张三" }
POST /api/v1/auth/login      { "username": "xxx", "password": "Test1234" }
```

两者都返回 `{ "data": { "token": "<jwt>" } }`，之后放进 `Authorization: Bearer <jwt>`。

---

## 五、完整调用链（最小可用）

```bash
TOKEN="刚拿到的jwt"
PHOTO_ID=$(curl -s -X POST "http://127.0.0.1:8080/api/v1/photos?photoType=DISEASE" \
  -H "Authorization: Bearer $TOKEN" -F "file=@leaf.jpg" | jq -r '.data.id')

curl -s -X POST "http://127.0.0.1:8080/api/v1/diagnoses/disease" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"photoId\": $PHOTO_ID}" | jq '.data.predicted'
```

---

## 六、常见问题

**Q：`available: false`，`reason` 说"模型文件不存在"**
检查路径。默认找 `ai-service/models/disease.onnx`，也可以用环境变量 `GT_MODEL_DIR` 指定其他目录。

**Q：准确率明显偏低**
先用 `/health` 确认加载的是哪个模型、`input_size` 是多少。
如果是芦荟的图但没传 `species=Aloe`，会走通用模型，准确率会从 ~98% 掉到 ~8%。

**Q：`/diagnoses/ai` 返回 `UNAVAILABLE`**
看 `modelNote`：如果写"未配置大模型 API Key"，是没配 `GREENTHUMB_LLM_API_KEY`；
如果写"AI 服务调用失败"，是 8000 端口没起。

**Q：换自己的模型怎么办**
重新训练后把三个文件覆盖到对应目录，重启 AI 服务即可，**不用改代码**。
新增物种只要在 `models/specialized/<物种key>/` 放一套，注册表会自动扫描。
