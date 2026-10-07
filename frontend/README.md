# 植语助手 GreenThumb AI — 前端

Vue 3 + Vite + Element Plus + ECharts + Pinia 的**页面框架**。

按 `docs/02-项目重设计方案.md` 的功能清单搭建，**交互已接通（走 Mock），业务逻辑待后端**。

---

## 快速开始

### 方式一：双击脚本（Windows 推荐）

直接双击项目里的 **`start.cmd`**。它会自动检查 Node、首次运行时安装依赖、然后启动开发服务器。

> **⚠️ 修改 `start.cmd` 时请注意（很容易踩）**
>
> Windows 批处理文件有两个硬性要求，任一不满足都会导致 CMD 把中文/内容当成命令名执行，报一堆
> `'.' 不是内部或外部命令`：
>
> 1. **换行符必须是 CRLF**，不能是 LF。很多编辑器（VS Code 默认、Git 默认）会存成 LF。
>    VS Code 里看右下角的 `LF` → 点它 → 改成 `CRLF`。
> 2. **内容保持纯 ASCII**（所以这个脚本的提示是英文）。如果一定要写中文，必须同时在文件开头加
>    `chcp 65001 >nul`，并确保文件以 UTF-8 **无 BOM** 保存。
>
> 命令行方式（方式二）不受这些限制，出问题时可以直接手动执行。

### 方式二：命令行

```bash
cd greenthumb/frontend
npm install
npm run dev
```

浏览器打开 http://localhost:5173

> ### ⚠️ Windows PowerShell 常见坑
>
> 如果直接敲 `npm` 报下面这个错：
>
> ```
> 无法加载文件 C:\Program Files\nodejs\npm.ps1，因为在此系统上禁止运行脚本。
> ```
>
> 这是因为 Windows 默认执行策略是 `Restricted`。三种解法任选其一：
>
> 1. **用 `npm.cmd` 代替 `npm`**（最省事）
> 2. **改用 CMD 或 Git Bash 终端**（不受该策略限制）
> 3. **放开策略**（一次性，推荐）：
>    ```powershell
>    Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
>    ```
>
> `start.cmd` 内部已经使用 `npm.cmd`，所以直接双击它不会遇到这个问题。

### 依赖安装慢？

```powershell
npm config set registry https://registry.npmmirror.com
```

### 启动成功的标志

终端会出现：

```
  VITE v5.x.x  ready in 500 ms

  ➜  Local:   http://localhost:5173/
```

浏览器会自动打开（`vite.config.js` 里配了 `open: true`）。若出现 `Port 5173 is in use`，Vite 会自动换到 5174，以终端实际输出的地址为准。

### 登录

骨架阶段默认开启 Mock（`.env.development` 里 `VITE_USE_MOCK=true`），**任意用户名密码都能登录**：

| 账号 | 权限 |
|---|---|
| `demo` / 任意密码 | 普通用户 |
| `admin` / 任意密码 | 管理员（额外显示「管理端」菜单） |

> Mock 约定：用户名以 `admin` 开头即视为管理员。

---

## 目录结构

```
frontend/
├── index.html
├── vite.config.js            # @ 别名、/api 代理到 :8080、分包配置
├── .env.development          # VITE_USE_MOCK=true
├── .env.production
└── src/
    ├── main.js               # 挂载、Element Plus 中文、全局注册图标
    ├── App.vue
    │
    ├── api/                  # ★ 接口层，与 docs/04 一一对应
    │   ├── request.js        # axios 实例 + 统一响应体拦截 + Mock 开关
    │   ├── auth.js  species.js  myPlant.js  photo.js
    │   ├── recognition.js    # 物种识别
    │   ├── diagnosis.js      # 病害识别 + 症状诊断
    │   ├── carePlan.js  careTask.js  careLog.js
    │   ├── qa.js  stats.js  weather.js  admin.js
    │
    ├── mock/                 # ★ 骨架阶段的假数据
    │   ├── data.js           # 示例数据（与真实响应结构完全一致）
    │   └── index.js          # axios adapter，按 URL 路由到 handler
    │
    ├── router/index.js       # 全部路由 + 登录态/管理员守卫
    ├── config/menu.js        # 侧边栏菜单（分组）
    ├── stores/               # Pinia：user / app
    ├── utils/
    │   ├── constants.js      # ★ 全部枚举字典（与后端枚举一一对应）
    │   ├── format.js         # 日期、百分比、置信度分档
    │   └── auth.js           # token 存取
    ├── styles/index.css      # 设计令牌 + Element Plus 主题覆盖
    │
    ├── layouts/DefaultLayout.vue    # 侧边栏 + 顶栏 + 内容区（移动端转抽屉）
    │
    ├── components/
    │   ├── SideMenu.vue  PageHeader.vue  StatCard.vue  EmptyState.vue
    │   ├── ConfidenceTag.vue        # 置信度统一分档显示
    │   ├── CarePlanExplain.vue      # ★ 养护系数拆解表
    │   ├── SymptomSelector.vue      # ★ 症状勾选 + 叶片位置必选
    │   ├── DiagnosisEvidence.vue    # ★ 病因 + 判断依据 + 建议
    │   ├── CitationList.vue         # ★ 知识来源与许可
    │   ├── SpeciesCard.vue  PlantCard.vue
    │   ├── ImageUploader.vue        # 支持移动端调起摄像头
    │   ├── ChatMessage.vue          # 问答气泡 + 检索片段 + 引用
    │   └── ChartBox.vue             # ECharts 封装（自适应）
    │
    └── views/
        ├── auth/          Login.vue  Register.vue
        ├── dashboard/     统计看板
        ├── species/       植物库列表 / 详情
        ├── plants/        我的植物列表 / 新增 / 详情
        ├── recognize/     拍照识别
        ├── diagnose/      智能诊断三步向导 / 诊断记录
        ├── care/          养护日历 / 养护计划 / 养护日志
        ├── qa/            AI 问答
        ├── profile/       个人中心 / 环境设置
        ├── admin/         植物库维护 / 病害信息 / 症状规则 / 知识库 / AI 用量
        └── error/404.vue
```

---

## 页面清单（对照功能编号）

| 路由 | 页面 | 对应功能 | 状态 |
|---|---|---|---|
| `/login` `/register` | 登录 / 注册 | F1 | ✅ |
| `/dashboard` | 统计看板 | F9 | ✅ |
| `/species` `/species/:id` | 植物库 / 详情 | F2 | ✅ |
| `/plants` `/plants/new` `/plants/:id` | 我的植物 / 新增 / 详情 | F3 | ✅ |
| `/recognize` | 拍照识别 Top-3（用于加入我的植物） | F4 | ✅ |
| `/diagnose` | **智能诊断**：拍照识别 → 病害识别 → 症状诊断 | F10 + F11 | ✅ |
| `/diagnose/records` | 诊断记录 | — | ✅ |
| `/care/plans` | 养护计划（系数拆解） | F5 | ✅ |
| `/care/calendar` | 养护日历 | F6 | ✅ |
| `/care/logs` | 养护日志 | F7 | ✅ |
| `/qa` | AI 问答（RAG 展示位） | F8 | ✅ |
| `/profile/environment` | 环境设置（天气） | F12 | ✅ |
| `/plants/:id` 时间线区块 | 成长时间线 | F13 | ✅ |
| `/plants/new` 第二个 Tab | 自然语言添加 | F14 | ✅ |
| `/admin/*` | 管理端 5 个页面 | — | ✅ |

---

## 智能诊断（`/diagnose`）—— 三步向导

原来看独立的「病害识别」和「症状诊断」已合并成一条线性流程：

```
第 1 步 拍照识别    上传照片 → 识别 Top-3 种类 → 选一个（可跳过）
        ↓
第 2 步 病害识别    对同一张照片跑病害模型
        ├─ 命中高置信度病害 → 给出处理建议，可选「确认结束」或「继续排查养护问题」
        ├─ 该物种无病害数据 → 诚实跳过并说明原因
        └─ 没有照片        → 跳过
        ↓
第 3 步 症状诊断    勾选症状 + 说明叶片位置 → 规则判因 → 判断依据 + 建议 + 来源
```

设计要点：

- **第 2 步会先判断该物种是否有病害数据**。公开数据集只覆盖绿萝、虎皮兰、芦荟、吊兰、多肉、玉露；
  其余植物（发财树、白掌、龟背竹等）会明确提示「暂无数据」并跳过，而不是硬跑模型输出无意义结果。
  覆盖范围定义在 `src/utils/constants.js` 的 `DISEASE_COVERED_SPECIES`，
  生产环境应改为后端 `plant_species.disease_covered` 字段下发。
- **没有照片也能用**。第 1、2 步都可跳过，第 3 步的症状诊断不依赖图像。
- **病害和养护问题不互斥**。第 2 步给出病害结论后仍保留「继续排查养护问题」的出口——
  因为浇水过多导致的根系受损，本身也会让植株更容易被病菌侵染。
- 旧链接 `/diagnose/disease`、`/diagnose/symptom` 会自动重定向到向导的第 2 / 3 步，
  从「我的植物」详情页点「诊断」则会带上 `?plantId=` 预选那盆植物。

---

## 三个体现了设计意图的组件

这三个不是通用组件，是**价值主张「会解释为什么」的界面落地**：

### `CarePlanExplain.vue`
把 `基准 7 天 × 夏季 0.8 × 空调房 0.9 × 花盆 1.1 = 5 天` 逐项展开成表格，用户能看懂每个数字怎么来的。

### `SymptomSelector.vue`
症状勾选 + **「黄叶从下部老叶还是新叶开始」必选**。
这一项就能把候选病因从 10 个收缩到 2–3 个，所以做成必选，不选不让提交。

### `DiagnosisEvidence.vue`
病因 + 置信度 + **判断依据列表** + 处理步骤 + 命中的规则编号。
用户问「为什么你觉得是浇多了」，界面直接给出答案。

---

## Mock 机制（重点）

Mock 不是拦截器，而是**替换了 axios 的 adapter**：

```js
// api/request.js
if (USE_MOCK) service.defaults.adapter = createMockAdapter()
```

**好处**：`api/` 目录下的业务代码与真实环境**完全一致**，页面代码零改动。

### 对接后端时怎么做

1. 把 `.env.development` 里的 `VITE_USE_MOCK` 改成 `false`
2. 启动后端（默认 `http://localhost:8080`，已在 `vite.config.js` 配好代理）
3. 完成。**页面代码一行都不用改。**

### Mock 的约定

- 未实现的接口会返回空数据并在控制台 `console.warn`，不会满屏报错
- 每次请求有 120–320ms 随机延迟，方便观察 loading 状态
- 演示「症状诊断」的信息不足降级：**叶片位置选「说不清」**，会返回 `needMoreInfo` 引导补充信息

---

## 尚未接通的部分（后端接口就绪后替换）

| 项 | 现状 | 需要做 |
|---|---|---|
| AI 问答流式输出 | Mock 一次性返回完整消息 | 后端返回 SSE（`retrieval` → `delta` → `citations` → `done`），前端改用 `EventSource` / fetch 流读取 |
| Markdown 渲染 | 仅支持 `**加粗**` 和换行 | 换 `markdown-it` + 代码高亮 |
| 图片 | emoji 占位（避免断网破图） | 换成 `species.imageUrl` / `photo.thumbUrl` |
| 表单校验 | 只做了必填与长度 | 按后端约束补全 |
| 权限 | 仅前端菜单隐藏 | 后端仍需做完整的鉴权 |

---

## 下一步

```bash
npm run build     # 产物在 dist/
```

推荐顺序：先做 **Phase 1**（植物库种子数据 + 我的植物 + 养护计划规则引擎 + 提醒日历），
这条链路不依赖任何 AI，能最快跑出可演示闭环。

设计与接口文档见上一级目录：

- `../docs/02-项目重设计方案.md`
- `../docs/03-症状诊断规则库.md`
- `../docs/04-API接口设计.md`
- `../db/schema.sql`
