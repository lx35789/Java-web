# 实验执行清单

> 照着从上往下做。每步都有**预期输出**和**失败怎么办**。
> 总耗时约 **2 天**（不含下载等待）。

---

## ✅ 总进度表

```
☑ 阶段 0  装依赖                已完成
☑ 阶段 1  流水线自检            已完成（22/22 + 6/6）
☑ 阶段 2  下载数据集            已完成（Chili 89.9 MB，4102 张全部解压）
☑ 阶段 3  ★ 确认图片内容         已完成 → 叶片特写；日期→类别 1:1
☑ 阶段 4  跑四层实验            已完成 → results\validation_report_v2.txt
☑ 阶段 5  整理结果写论文        初稿 → ..\docs\16-真实数据验证结果.md
```

> ## ⚠️ 真实数据结果已出，先读这个再往下做
>
> **`..\docs\16-真实数据验证结果.md`**
>
> - **横截面判健康：失败且可解释。** 6 个拍摄场次每个只含 1 个类别，类别与场次完全混杂；
>   同一组「健康 vs 涝害」对比，换场次后 AUC 从 **0.82 翻转为 0.42**。
> - **配对连续追踪：成功。** 同一植株同一视角跨两场的 82 对样本中，**77 对（93.9%）**叶绿素代理
>   指标同向变化，Wilcoxon **p = 5.7×10⁻¹⁴**。
> - **验收标准要改**：废弃「横截面 AUC>0.7」，改用「**配对方向一致率 ≥70%**」。
> - **判别主力要改**：`health_index` 降为展示用（`green_ratio` 在真实叶片上饱和于 0.89–0.93），
>   判别改用 CIELAB 的 `chlorophyll = −a*` 与 `ExG`。
> - **本清单阶段 4 原本设计的"四层实验"已被 `run_validation.py` / `run_validation2.py` 取代**，
>   下面的旧步骤保留作参考。

---

# 阶段 0 · 装依赖（约 15 分钟）

## 0.1 你的环境现状（已探测）

| 项 | 状态 |
|---|---|
| Python | ✅ `C:\Users\lenovo\miniconda3\python.exe`（3.9.25） |
| numpy / opencv / pandas / scipy / matplotlib | ❌ **全都没装** |

## 0.2 装库

打开 **CMD**（不是 PowerShell，避免脚本策略问题），粘贴：

```cmd
C:\Users\lenovo\miniconda3\python.exe -m pip install -U pip
C:\Users\lenovo\miniconda3\python.exe -m pip install -i https://pypi.tuna.tsinghua.edu.cn/simple numpy opencv-python-headless pandas scipy matplotlib
```

> **为什么用 `opencv-python-headless`**：不带 GUI 依赖，体积小、装得快。我们只用来读图算颜色，不需要窗口。

**预期**：刷一堆 `Downloading ...` / `Successfully installed ...`

**失败怎么办**：

| 报错 | 处理 |
|---|---|
| `pip 不是内部或外部命令` | 用完整的 `C:\Users\lenovo\miniconda3\python.exe -m pip ...` |
| 下载超时 | 换镜像：把 `pypi.tuna.tsinghua.edu.cn` 换成 `mirrors.aliyun.com/pypi/simple/` |
| SSL 证书错误 | 命令末尾加 `--trusted-host pypi.tuna.tsinghua.edu.cn` |
| 权限错误 | 命令末尾加 `--user` |

## 0.3 自检

```cmd
cd greenthumb\experiments
C:\Users\lenovo\miniconda3\python.exe common\color_metrics.py
```

**预期输出**：

```
  [PASS] health_index(1.0, 0.0, 0.0) = 100  (expect 100)
  [PASS] health_index(0.0, 1.0, 0.0) = 0  (expect 0)
  [PASS] health_index(0.0, 0.0, 1.0) = 0  (expect 0)
  [PASS] health_index(0.7, 0.3, 0.0) = 46  (expect 46)
  [PASS] health_index(0.9, 0.1, 0.0) = 82  (expect 82)
  [PASS] health_index(0.8, 0.1, 0.1) = 60  (expect 60)

6/6 passed  —— 应与 Node 端 _selftest.js 结果一致
```

> 这 6 个数字和 Node 端 `_selftest.js` 完全一致，说明两套实现（JS/Python）
> 用的是同一套阈值和公式，结果可以交叉验证。

**☑ 这一步过了才往下走。**

---

# 阶段 1 · 流水线自检（约 10 分钟）

用**合成数据**先把四个脚本跑一遍。**这一步的结果没有科学意义**，只用来确认代码和环境都正常。

```cmd
cd greenthumb\experiments

REM 1. 生成合成数据
C:\Users\lenovo\miniconda3\python.exe make_synthetic.py --out synthetic

REM 2. 依次跑四层
C:\Users\lenovo\miniconda3\python.exe layer1_stress_discrimination.py --data synthetic --out results_syn
C:\Users\lenovo\miniconda3\python.exe layer2_observation_pair.py --pairs synthetic\pairs.csv --out results_syn
C:\Users\lenovo\miniconda3\python.exe layer3_time_series.py --data synthetic\ts --out results_syn
C:\Users\lenovo\miniconda3\python.exe layer4_symptom_vs_disease.py --data synthetic --out results_syn
```

**预期**：`results_syn\` 目录里出现

```
layer1_metrics.csv     layer1_boxplot.png     layer1_summary.txt
layer2_pairs.csv       layer2_delta.png       layer2_summary.txt
layer3_timeseries.csv  layer3_curve.png       layer3_summary.txt
layer4_auc.csv         layer4_auc.png         layer4_summary.txt
```

打开 `layer1_boxplot.png` 看一眼——**三组箱子应该明显分开**。这就说明流水线通了。

**失败怎么办**：

| 现象 | 原因 | 处理 |
|---|---|---|
| `ModuleNotFoundError` | 阶段 0 没装全 | 重跑 0.2 |
| 中文变成方块 | matplotlib 字体 | 脚本已用英文标签，若仍有问题忽略（不影响数据） |
| `脚本路径找不到` | 没在 experiments 目录 | 先 `cd greenthumb\experiments` |
| `--pairs 文件不存在` | 合成没生成 | 重跑第 1 步 `make_synthetic.py` |

**☑ 这一步全过了，说明可以上真实数据。**

---

# 阶段 2 · 下载数据集（约 30 分钟）

## 2.1 ★ 主数据集：辣椒过浇胁迫（90 MB）

**地址**：https://zenodo.org/records/17901489

1. 打开页面 → 找到 `Chili_Pepper_BBWV2_Flooding.zip` → 点 Download
2. 解压到 `greenthumb\experiments\data\chili\`

**检查解压结果**：

```cmd
dir /s /b data\chili | findstr /i "healthy control overwater flood bbwv virus"
```

应该能看到含这些关键词的目录。

## 2.2 辅助数据集：火龙果仙人掌生长时序（345 MB）

**地址**：https://www.kaggle.com/datasets/mdahshanhabib/dragon-fruit-cactus-groth-proccess

1. 需要 Kaggle 账号，点 Download
2. 解压到 `greenthumb\experiments\data\dragonfruit\`

**检查**：目录名应形如 `Day1`、`Day2-5`、`Day8-11`、`Day15-30`…

## 2.3 下载不了怎么办

| 情况 | 备选 |
|---|---|
| Zenodo 打不开 | 用 Mendeley 的 `Tracking Plant Growth`（CC BY 4.0）：https://data.mendeley.com/datasets/z2fp5kbgbh |
| Kaggle 需要登录 | 先只做辣椒数据集（第 1、2、4 层），第 3 层延后 |
| 全都下载不了 | **只做阶段 1（合成数据）+ 专家评审**，论文里说明数据获取受限 |

---

# 阶段 3 · ★ 确认图片内容（约 10 分钟）

## 这是整个清单里最关键的一步

**打开 `data\chili\` 里的 5–10 张图，回答一个问题：**

> **照片里是「整株植物」还是「一片叶子的特写」？**

## 为什么这一步决定后面怎么做

| 你看到的 | 含义 | 怎么调整 |
|---|---|---|
| **整株照**（能看到花盆、整棵植株） | ✅ 理想情况 | **原样跑，不用改** |
| **叶片特写**（一片叶子占满画面） | ⚠️ 场景有差距 | 原样能跑，但**论文里必须写明**："公开数据集为叶尺度图像，与本文整株尺度的应用场景存在差异" |
| **两种都有** | 可能是分目录存放的 | 用 `--groups` 只指定整株照那部分 |

## 记录一下你的观察

```
我看到的图片是：□ 整株照   □ 叶片特写   □ 都有
图片数量大致：
分辨率大致：
背景是：        □ 纯色   □ 实验台   □ 复杂
是否存在 w6 / w7 的时间点区分：□ 是   □ 否   □ 看不出
```

**最后一行很重要** —— 第 2 层需要"配对观测"，如果不区分 w6/w7，第 2 层就做不了。

---

# 阶段 4 · 跑四层实验（约半天）

## 4.1 第 1 层 —— 能否区分健康 vs 过浇 ★ 最重要

```cmd
cd greenthumb\experiments
C:\Users\lenovo\miniconda3\python.exe layer1_stress_discrimination.py --data data\chili --out results
```

**看 `results\layer1_summary.txt`**：

```
健康组 vs 过浇组：健康指数 mean 78.3 vs 41.2；AUC=0.912；p=3.14e-08
判据（AUC>0.7 且 p<0.05）：✅ 验证通过
```

**判据**：AUC > 0.7 **且** p < 0.05

**产物**：`layer1_metrics.csv`（明细）、`layer1_boxplot.png`（**论文可直接用**）

**分组识别失败时手动指定**：

```cmd
C:\Users\lenovo\miniconda3\python.exe layer1_stress_discrimination.py --data data\chili --out results ^
  --groups healthy=data\chili\healthy overwatered=data\chili\flooding bbwv2=data\chili\bbwv2
```

> **★ 这一层是关键**：它验证的是整个方案的**前提假设**。
> 如果不通过，说明"颜色统计反映胁迫状态"这个假设不成立，后面都不用做了——
> **这本身也是有价值的结论**，要如实写。

## 4.2 第 2 层 —— 观测对能否判别变化方向

**第一步：造 `pairs.csv`**

先看 `results\layer1_metrics.csv` 的 `path` 列，在 Excel 里拼出配对：

```csv
plant_id,group,t1_path,t2_path
p001,healthy,data/chili/healthy/w6_01.jpg,data/chili/healthy/w7_01.jpg
p002,healthy,data/chili/healthy/w6_02.jpg,data/chili/healthy/w7_02.jpg
p003,overwatered,data/chili/flooding/w6_01.jpg,data/chili/flooding/w7_01.jpg
```

> **每组拼 20–30 对就够了**，手工做半小时。
> 表头四个列名必须完全一致。

**第二步：跑**

```cmd
C:\Users\lenovo\miniconda3\python.exe layer2_observation_pair.py --pairs pairs.csv --out results
```

**看 `layer2_summary.txt`**：

```
[healthy     ] n= 25  Δ mean=  -1.20  median=  -0.00  恶化比例=12/25=48%
[overwatered ] n= 25  Δ mean= -28.40  median= -30.00  恶化比例=24/25=96%
健康组 Δ vs 过浇组 Δ：-1.20 vs -28.40，p=1.07e-09
判据（健康组 Δ > 过浇组 Δ 且 p<0.05）：✅ 验证通过
```

**判据**：健康组的 Δ 显著大于过浇组（p < 0.05）

> 如果数据集**没有区分 w6/w7**（阶段 3 记的"否"），这一层跳过，在论文里说明。

## 4.3 第 3 层 —— 时序曲线平滑性

```cmd
C:\Users\lenovo\miniconda3\python.exe layer3_time_series.py --data data\dragonfruit --out results
```

**看 `layer3_curve.png`** —— 这条曲线就是**你产品里"植物状态趋势图"的原型**。

**判据**：**信噪比 SNR ≥ 2**

```
时间点内噪声 = 同一时间点下多张图片健康指数的 std 均值   （光照/角度的干扰）
时间点间信号 = 相邻时间点健康指数中位数之差的 |Δ| 均值   （植株真实变化）
SNR = 信号 / 噪声
```

> **★ 这条判据在实测中修过一次**：
> 原来的判据是「相邻跳变中位数 < 15」，**逻辑上是错的**——
> 植株真实快速恶化时跳变本来就大，那不是"对拍摄条件敏感"。
> 旧判据会在数据质量最好的时候误判为失败。
> 现在改成把「真实变化」和「测量噪声」分开比较。

**SNR < 2 怎么办** → 说明噪声和信号相当，需要加强**标准化拍摄**（固定角度/时段/背景/参照物）。这本身也是个有价值的结论，可以写进论文。

## 4.4 第 4 层 —— 反向验证（★ 预期是负面结果）

```cmd
C:\Users\lenovo\miniconda3\python.exe layer4_symptom_vs_disease.py --data data\chili --out results
```

**看 `layer4_summary.txt`**：

```
最佳单一指标：brown_ratio  AUC=0.583
结论：✅ 支持分流设计：颜色指标难以区分生理性与病理性异常，
      必须依靠症状追问与独立的病害识别模块
```

**怎么解读**：

| AUC | 含义 | 论文里怎么写 |
|---|---|---|
| **< 0.7** | 区分力弱 | ✅ **支持"症状/病害分流"的设计决策**（预期结果） |
| 0.7–0.85 | 中等 | 颜色有参考价值但不充分，仍需分流 |
| > 0.85 | 强 | ⚠️ 如实报告，说明分流必要性下降，别硬掰 |

> **★ 这一层是论文亮点**：用一个负面结果论证设计决策的合理性，
> 比硬凑正面结果有说服力得多。

---

# 阶段 5 · 整理结果写论文（约半天）

## 5.1 收集产物

```
results\layer1_boxplot.png     → 图 1：三组健康指数分布
results\layer1_summary.txt     → 表 1：AUC / p 值
results\layer2_delta.png       → 图 2：观测对变化量对比
results\layer3_curve.png       → 图 3：时序曲线
results\layer4_auc.png         → 图 4：各指标判别力（反向验证）
```

## 5.2 套模板

`../docs/14-公开数据验证方案.md` 第 5 节有现成的：

- **结果描述模板**（4 条，直接改数字）
- **局限性说明模板**（3 条）

## 5.3 ★ 必须写的三条局限

```
① 物种不同：辣椒、仙人掌 ≠ 常见室内观叶植物（但都是盆栽，
   比小麦田间、花椰菜机器人拍摄近得多）
② 拍摄条件不同：受控实验光照 ≠ 用户手机手持随手拍
③ 本文验证的是「方法的可迁移性」，不是「产品在真实场景的最终效果」
```

> **验证的是「方法可行」，不是「产品有效」—— 这两件事必须分清，否则答辩会被问住。**

## 5.4 一个可以写进"未来工作"的点

健康指数在胁迫极端时会被 clamp 到 0，**丧失低端区分度**（阶段 1 的合成数据里过浇组全是 0）。改进方向：不 clamp、事后 min-max 归一化，或改用连续回归模型。

---

# 🆘 卡住时的排查表

| 症状 | 先查什么 |
|---|---|
| `ModuleNotFoundError: cv2` | 阶段 0 没装完，重跑 pip |
| `[错误] 没能识别出任何分组` | 用 `--groups healthy=<路径> overwatered=<路径>` 手动指定 |
| `全部无效，跳过` | 图的背景太杂导致前景像素不足 500，调 `common/color_metrics.py` 里的 `MIN_PLANT_PIXELS` |
| 箱线图三组混在一起 | ① 确认图片内容（阶段 3）② 调 HSV 阈值 ③ 如实报告"该数据集上区分度不足" |
| `pairs.csv` 报缺列 | 表头必须是 `plant_id,group,t1_path,t2_path` |
| 图表中文是方块 | 忽略，脚本用英文标签，不影响数据 |
| 整体都不顺 | **降级方案**：只做阶段 1（合成，证明流水线）+ 专家评审 + 论文里说明数据获取受限 |

---

# 📌 最小可行版本（时间不够就只做这个）

如果只剩 1 天，做这三件事就够交差：

```
1. 装依赖 + 跑 common/color_metrics.py 自检        (30 分钟)
2. 下载辣椒数据集 + 跑 layer1                       (2 小时)
3. 套用 papers 模板写结果与局限                     (2 小时)
```

**其余（layer2/3/4）都是加分项。**

---

# 参考

- 数据集清单与核实结果：`../docs/14-公开数据验证方案.md`
- 实验设计与算法说明：`../docs/13-状态追踪与个性化验证.md`
- 本包使用说明：`README.md`
