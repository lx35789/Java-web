# Houseplant Diseases Collection 获取指南

> 调研时间：2026-11-19
> 目标：拿到 11 病类 + 1 健康类、10.9 GB 的室内植物病害数据

---

## 1. 先说结论

**这个数据集不能直接下载，必须向作者申请。** 而且论文本身也是禁运状态，
两份材料都要单独申请。

| 材料 | 位置 | 状态 | 说明 |
|---|---|---|---|
| 数据集 | [Zenodo 17357719](https://zenodo.org/records/17357719) | **Restricted** | 10.9 GB，CC BY 4.0，API 连文件名都不返回（`files: []`） |
| 硕士论文 | [ULisboa 仓库](http://hdl.handle.net/10400.5/118936) | **Embargoed access** | 8.81 MB PDF，只能点 `request-a-copy` |

**作者联系方式（数据集页面直接公开）：`eduardo.tanqueiro@gmail.com`**

- 作者：Eduardo Tanqueiro Lopes Isidoro Carneiro
- 单位：Universidade de Lisboa，Faculdade de Ciências（FCUL）
- 导师：Nuno Cruz Garcia、João P. Matos Carvalho（LASIGE 研究中心）
- 采集时间：2025-02，发布 2025-10-15
- 许可：CC BY 4.0（Copyright: Eduardo Carneiro）
- 当前下载量：5 次 —— 说明已经有人获批过，流程是通的

### 为什么受限

论文摘要写明：**"developed in cooperation with Generosa Nature"** —— 一家葡萄牙
植物养护产品公司。数据集是校企合作产物，作者标了 CC BY 4.0（说明本意是开放），
但企业方可能对数据分发有约束，于是设成 Restricted 逐案审批。

这不是"忘了开放"，是有意为之。所以申请时要让对方能快速判断风险。

---

## 2. 三条路径，按推荐顺序

### 路径 A：Zenodo 提交 access request（正规、留痕）

1. 注册/登录 Zenodo（可用 GitHub、ORCID 直接登录）
2. 打开 https://zenodo.org/records/17357719
3. 在 **Files → Restricted** 区域找 **Request access** 按钮
4. 填表：
   - 登录用户：只需写 message to the owner
   - 未登录：要填邮箱 + 全名，再点邮件里的验证链接
5. 勾选同意把姓名邮箱共享给作者，提交

之后：作者收到通知 → 批准后你收到含**密钥链接**的邮件（链接有时效，
作者可设定有效期）→ 用该链接下载。

注意事项：

- **Zenodo 官方不介入审批**，作者不批就是拿不到，官方也查不到上传者信息
- 作者**不允许收费**，若被要钱可以举报
- 页面上如果**看不到 Request access 按钮**，说明作者没开启这个入口，
  只能走路径 B（受限记录默认不一定开放申请）
- 通过后可申请 API token 做程序化下载（`/api/records/17357719/files`）

### 路径 B：直接发邮件（更快，推荐和 A 并行）

作者留了 Gmail，比 Zenodo 审批链条短。10.9 GB 走 Zenodo 密钥链接下载本身
也不方便，作者更可能直接给网盘链接。

**一封邮件同时问三件事**：数据访问 + 论文全文 + 类目清单。
模板见第 3 节。

### 路径 C：只要类目清单，先不拿全量

如果只是想确认"这 12 个类能不能装进现有架构"，不必先要 10.9 GB。
可以先问作者要 **class list + 每类样本数**（论文里本来就有这张表），
确认结构后再决定要不要全量申请。**建议先走这条，成本最低。**

### 路径 D：自己聚合（兜底）

论文摘要明确写了 **"aggregating data from multiple online sources"** ——
这个数据集本身是从公开来源拼起来的，不是全新采集。

所以如果申请不下来，可以拿到论文的 **References / 数据来源章节**，
按同样来源自己拼一份。缺点是要重新对齐类目定义，工作量取决于来源分散程度。

> 拿论文在这里是双重价值：既是引用依据，也是"数据从哪来"的地图。

---

## 3. 邮件模板（可直接改）

```
Subject: Request for access to "Houseplant Diseases Collection" (Zenodo, DOI 10.5281/zenodo.17357719)

Dear Mr. Carneiro,

I am [你的姓名], [身份：e.g. a graduate student at XX University /
a developer at XX]. I came across your dataset "Houseplant Diseases
Collection" (Zenodo, DOI 10.5281/zenodo.17357719) and your MSc thesis
"Deep Learning for House Plants Disease Detection: Novel Dataset and
Benchmarks" (ULisboa, 2025).

We are building a plant-care application that diagnoses diseases of
indoor ornamental plants from user-uploaded photos. Public datasets for
houseplants are extremely scarce -- most existing collections cover
succulents, aloe or pothos only -- which is exactly the gap your work
addresses. We currently deploy a 4-class orchid model and would like to
extend coverage to the species in your collection.

I would like to request:

1. Access to the dataset files on Zenodo (11 diseased classes + 1 healthy
   class, 10.9 GB).
2. A copy of your thesis, if the embargo allows.
3. If possible, the class list with per-class image counts, so we can
   check compatibility with our current pipeline before downloading
   everything.

Intended use: [选一个，如实写]
  - academic / non-commercial research and coursework
  - internal evaluation of model performance; no redistribution
  - integration into a commercial product (we would credit the dataset
    as required by CC BY 4.0 and would not redistribute the raw images)

We will cite your dataset as:
Carneiro, E. (2025). Houseplant Diseases Collection (Version 1.0)
[Dataset]. Zenodo. https://doi.org/10.5281/zenodo.17357719

If there are conditions you would like us to agree to, or a preferred
way to share the files, please let me know.

Thank you for making this resource available.

Best regards,
[姓名]
[单位 / 项目]
[邮箱]
```

要点：

- **别写空话**，直接说清楚用途和不会再分发，作者最关心这个
- 如果用于商业产品，**必须如实说**。CC BY 4.0 允商用，但校企合作的数据
  可能有额外约束，被发现隐瞒会直接拒
- 问类目清单这一条能显著提高回复率 —— 对方回一行字比给你 10.9 GB 容易

---

## 4. 拿到之后先做什么

**第一件事不是训练，是确认类目结构。** 这决定架构能不能装下。

现有编码是两段式 `物种_病害`（如 `Orchid_Anthracnose`），后端靠前缀
`物种_` 过滤类别。如果这个数据集的 12 个类是**跨物种**的
（"白粉病"一个类同时覆盖月季、菊花、扶桑），就会出问题：

| 做法 | 后果 |
|---|---|
| 塞进某个物种前缀 | 其他物种永远识别不出来 |
| 新建 `General_xxx` 前缀 | `speciesMass` 算错，前端把它当第 11 个物种列进清单 |

真要接，得加一个 `scoped: "global"` 的通用病害层。**所以先确认数据形态再花时间下载。**

需要问清楚的三点：

1. 12 个类是按「物种+病害」划分，还是按「病害」跨物种划分？
2. 每类多少张？分布在哪些物种上？
3. 图片是整株照、叶片特写，还是棚拍黑底？（决定要不要做域适配 ——
   兰花那次 91.2% 误报的教训就是训练集只有黑底特写）

---

## 5. 两个风险提醒

### 5.1 聚合数据的版权是叠加的

数据集标 CC BY 4.0，但它是"从多个在线来源聚合"的。**上游每张图的许可
不会因为被聚合就自动变成 CC BY。** 如果上游含 CC BY-NC 或来源不明的图，
商用会有风险。

如果要做商业产品，建议在邮件里顺带问一句：**"Are all images in the
collection licensed for commercial reuse, or do some come from sources with
more restrictive terms?"** 这个问题作者一般会如实回答。

### 5.2 Zenodo 在这台机器上要绕一下

`curl` 直接访问 zenodo.org 返回 403（本机网络/UA 问题），但通过 WebFetch
可以正常读取页面和 API。所以：

- 查元数据：用 WebFetch 打 `https://zenodo.org/api/records/17357719`
- 下载文件：拿到密钥链接后，需要有浏览器或能改 UA 的下载工具

这不是账号问题，是本地网络环境问题，换网络能解决。

---

## 6. 附：已核实的元数据

```
DOI（全版本）   10.5281/zenodo.17357718
DOI（v1.0）     10.5281/zenodo.17357719
标题            Houseplant Diseases Collection
作者            Carneiro, Eduardo（Universidade de Lisboa）
发布            2025-10-15，v1.0，采集于 2025-02
许可            CC BY 4.0
访问            restricted（files 不公开）
规模            11 病类 + 1 健康类，10.9 GB
关键词          Plant Diseases / Indoor Plants / Disease Classification /
                Plant Images / Diseased Plants
索引            OpenAIRE
论文            10.5281 无关；hdl.handle.net/10400.5/118936（embargoed）
论文实验结果    DINOv2 91%，ViT 系架构表现最好
```
