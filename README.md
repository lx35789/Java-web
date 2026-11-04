# 植语助手 GreenThumb AI

室内绿植养护与病害识别助手。三段式架构：**Vue 3 前端 + Spring Boot 后端 + FastAPI 推理服务**。

---

## 一、快速上手

### 1. 环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 17 | 后端 |
| Maven | 3.8+ | 或用 IDEA 内置的 |
| Node.js | 18+ | 前端 |
| Python | 3.9+ | AI 服务 |
| MySQL | 8.0+ | 数据库 |

### 2. 初始化数据库

```bash
mysql -u root -p -e "CREATE DATABASE greenthumb DEFAULT CHARSET utf8mb4;"
mysql -u root -p greenthumb < db/schema.sql
mysql -u root -p greenthumb < db/seed.sql          # 14 个物种
mysql -u root -p greenthumb < db/seed_disease.sql  # 29 个病害知识
mysql -u root -p greenthumb < db/seed_symptom.sql  # 症状规则
```

### 3. 配置数据库与密钥

数据库有**默认值**（`localhost:3306/greenthumb`，用户 `root`，空密码），
所以你大概率只需要设置一个密码环境变量就能跑起来：

```powershell
# Windows PowerShell（当前窗口有效）
$env:DB_PASSWORD = "你的MySQL密码"

# 或永久生效
setx DB_PASSWORD "你的MySQL密码"
```

> 三个可用环境变量：`DB_URL` / `DB_USER` / `DB_PASSWORD`，都已在 `application.yml` 里给了默认值。

**第三方 key（可选）**：不配也能用，只是对应功能降级。

| key | 不配的后果 |
|---|---|
| 百度 / Pl@ntNet | 物种识别不可用，前端引导手动选择物种 |
| 大模型（qwen） | AI 讲解不可用，只返回知识库原文 |

要配置的话，二选一：

**方式 A：环境变量**（推荐，不落地任何文件）
```powershell
setx GREENTHUMB_LLM_API_KEY "sk-xxx"
setx GREENTHUMB_LLM_BASE_URL "https://dashscope.aliyuncs.com/compatible-mode/v1"
```

**方式 B：配置文件**
```bash
cp backend/src/main/resources/application-dev.yml.example \
   backend/src/main/resources/application-dev.yml
```
然后填入 `spring.datasource.password` 与 `greenthumb.llm.api-key`。
该文件已被 `.gitignore` 排除，不会进仓库。

### 4. 启动三个服务

```bash
# ① AI 推理服务（端口 8000）
cd ai-service
pip install -r requirements.txt
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000

# ② 后端（端口 8080）
cd backend
mvn spring-boot:run

# ③ 前端（端口 5173）
cd frontend
npm install
npm run dev
```

打开 <http://localhost:5173>，注册账号即可使用。

---

## 二、模型文件说明

`ai-service/models/` **不进仓库**（每个 17MB）。首次运行前二选一：

**方式 A：自己训练**（需要数据集，见 `ai-service/train/README`）
```bash
python ai-service/train/train_indoor.py \
  --data <数据集目录> --out ai-service/models \
  --arch mobilenetv3_large_100 --img-size 160 --epochs 29 --lr 3e-3
```

**方式 B：向项目负责人索要**已训练好的 `disease.onnx` + `labels.json` + `meta.json`，放到：

```
ai-service/models/
├── disease.onnx                 通用模型（29 类，6 物种）
└── specialized/
    └── aloe/disease.onnx        芦荟专用模型（5 类）
```

模型缺失时服务**不会崩溃**，`/health` 返回 `available: false`，前端提示"识别暂不可用"。

---

## 三、目录结构

```
greenthumb/
├── frontend/                 Vue 3 + Vite + Element Plus
│   └── src/
│       ├── views/            页面（plants / diagnose / care / profile）
│       ├── config/menu.js    侧边栏菜单配置
│       └── router/index.js   路由
├── backend/                  Spring Boot 3.3.5 + MyBatis-Plus
│   └── src/main/java/com/greenthumb/
│       ├── controller/       REST 接口
│       ├── service/          业务编排（DiagnosisService / AiDiagnosisService）
│       ├── service/llm/      大模型客户端（OpenAI 兼容，换厂商只改配置）
│       └── recognition/      物种识别降级链（百度 → Pl@ntNet）
├── ai-service/               FastAPI + ONNX Runtime
│   ├── app/disease.py        多模型注册表 + 按物种路由
│   └── train/                训练与导出脚本
├── db/                       建表与种子数据
└── experiments/              可行性验证实验（历史）
```

---

## 四、核心设计

### 识别分工

```
用户拍照
  ├─ 物种确认 → 植物档案优先，百度/Pl@ntNet 兜底
  └─ 病害识别 → 自训模型（大模型不参与识别）
       ├─ 该物种有专用模型 → 用专用模型
       └─ 无专用模型       → 用通用模型
```

**为什么大模型不做识别**：实测在 25 张芦荟留出图上，CNN 单独 68%、Top-3 命中 96%，
而把 Top-3 交给多模态大模型复核只对 24%（比三选一瞎猜还差）。
大模型只负责"把已确定的结论讲清楚 + 生成处置建议"。

### 模型路由

请求带 `species` 参数时优先使用专用模型；没有则自动回退通用模型，返回里带
`routedTo` / `fallback` 便于排查。新增物种只需把模型放进
`ai-service/models/specialized/<物种>/`，**不用改代码**。

---

## 五、接口一览

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/auth/register`、`/login` | 注册 / 登录（JWT） |
| GET | `/api/v1/plants` | 我的植物 |
| POST | `/api/v1/photos` | 上传照片 |
| POST | `/api/v1/recognitions` | 物种识别 |
| POST | `/api/v1/diagnoses/disease` | 病害识别（走自训模型） |
| POST | `/api/v1/diagnoses/ai` | AI 辅助诊断（模型 + 大模型讲解） |
| POST | `/api/v1/diagnoses/symptom` | 症状判因（规则库） |
| GET | `/api/v1/diagnoses` | 诊断记录 |
| GET | `/api/v1/care/plans`、`/calendar`、`/logs` | 养护计划 / 日历 / 日志 |

AI 服务（内部，端口 8000）：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/health` | 模型状态（含专用模型清单） |
| POST | `/v1/disease/predict?top_k=3&species=Aloe` | 病害分类 |

---

## 六、注意事项

- **不要提交** `application-dev.yml`、`*.onnx`、数据集、`node_modules`
- 训练数据统一预处理成 **448px** 落盘，否则训练会慢 5 倍以上（4000px 原图现场解码是瓶颈）
- Windows 上训练用 `--workers 0`，多进程 DataLoader 反而更慢
- 数据集放进 `experiments/data/`（已在 .gitignore 中）

---

## 七、当前模型指标

| 模型 | 骨干 | 输入 | 测试准确率 | Top-3 |
|---|---|---|---|---|
| 通用 29 类 | MobileNetV3-Large | 160 | **93.69%** | 98.11% |
| 芦荟专用 5 类 | MobileNetV3-Large | 224 | **74.81%** | 95.05% |

> 以上为同分布留出测试集成绩。真实拍照场景（换背景/构图/光照）会明显下降，
> 这是本领域公认的域偏移问题，详见 `docs/` 下的实验记录。
