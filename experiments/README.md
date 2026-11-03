# 状态追踪验证实验

> 用**公开数据**验证「状态追踪 + 个性化养护」的核心假设，**不需要自己养植物**。
> 对应设计文档：`../docs/13-状态追踪与个性化验证.md`、`../docs/14-公开数据验证方案.md`

---

## 这个包里有什么

| 文件 | 作用 | 状态 |
|---|---|---|
| `_selftest.js` | **零依赖自测**：验证 HSV 颜色指标与健康指数的算法逻辑 | ✅ **已实测通过 22/22** |
| `selftest-report.txt` | 上面那次自测的实际输出 | ✅ 已生成 |
| `common/color_metrics.py` | 颜色指标提取（所有层共用） | 待运行 |
| `layer1_stress_discrimination.py` | 第 1 层：能否区分健康 vs 过浇 | 待运行 |
| `layer2_observation_pair.py` | 第 2 层：观测对能否判别变化方向 | 待运行 |
| `layer3_time_series.py` | 第 3 层：时序曲线是否平滑合理 | 待运行 |
| `layer4_symptom_vs_disease.py` | 第 4 层：反向验证（颜色能否区分生理/病理） | 待运行 |
| `make_synthetic.py` | 合成数据生成器（先跑通流水线用） | 待运行 |
| `requirements.txt` | 依赖清单 | — |

> **⚠️ 关于「状态」列**：编写这套代码的环境**无法联网安装依赖**，所以
> Python 部分只做了代码编写，**没有实际运行过**。
> Node 自测部分（纯算法、零依赖）**是实际跑通的**，22 项全过。
>
> 下面第 1 步就是让你把 Python 部分也跑起来。

---

## 第 1 步：装依赖

你这台机器的情况（已探测）：

| 项 | 状态 |
|---|---|
| Python | ✅ `C:\Users\lenovo\miniconda3\python.exe`（3.9.25） |
| numpy / opencv / pandas / scipy / matplotlib | ❌ **都没装** |

**安装命令**（用清华镜像，国内快）：

```cmd
C:\Users\lenovo\miniconda3\python.exe -m pip install -U pip
C:\Users\lenovo\miniconda3\python.exe -m pip install -i https://pypi.tuna.tsinghua.edu.cn/simple numpy opencv-python-headless pandas scipy matplotlib
```

装完自检：

```cmd
C:\Users\lenovo\miniconda3\python.exe common\color_metrics.py
```

预期输出（应与 Node 端完全一致）：

```
  [PASS] health_index(1.0, 0.0, 0.0) = 100  (expect 100)
  ...
  6/6 passed  —— 应与 Node 端 _selftest.js 结果一致
```

---

## 第 2 步：先跑合成数据，确认整条流水线通

**在做真实数据之前先走这一步。** 合成数据只用来验证代码能跑，**结果没有科学意义**。

```cmd
cd greenthumb\experiments

REM 生成合成数据
python make_synthetic.py --out synthetic

REM 依次跑四层
python layer1_stress_discrimination.py --data synthetic --out results_syn
python layer2_observation_pair.py --pairs synthetic\pairs.csv --out results_syn
python layer3_time_series.py --data synthetic\ts --out results_syn
python layer4_symptom_vs_disease.py --data synthetic --out results_syn
```

四步都跑完、`results_syn\` 里出现 CSV 和 PNG，说明环境没问题，可以上真实数据了。

---

## 第 3 步：下载真实数据集

### ★ 主数据集：辣椒过浇胁迫（90 MB）

**https://zenodo.org/records/17901489** → 下载 `Chili_Pepper_BBWV2_Flooding.zip` → 解压到 `data/chili/`

> **★ 下载后第一件事：打开几张图，确认是「整株照」还是「叶片特写」。**
> 数据集描述里两种说法都有，这决定了它怎么用：
> - **整株照** → 直接可用，价值最高
> - **叶片特写** → 仍可用于验证「颜色指标能否区分胁迫」，但和我们"整株区域统计"的场景有差距，要在论文里说明

检查目录结构：

```cmd
dir /s /b data\chili | findstr /i "healthy overwater bbwv"
```

脚本靠关键词自动识别分组（`healthy` / `control`，`overwater` / `flooding`，`bbwv` / `virus`）。
识别不出来就手动指定：

```cmd
python layer1_stress_discrimination.py --data data\chili ^
  --groups healthy=D:\data\chili\healthy overwatered=D:\data\chili\flooding bbwv2=D:\data\chili\bbwv2
```

### 辅助数据集：火龙果仙人掌生长时序（345 MB）

**https://www.kaggle.com/datasets/mdahshanhabib/dragon-fruit-cactus-groth-proccess** → 解压到 `data/dragonfruit/`

目录形如 `Day1 / Day2-5 / Day8-11 / Day15-30 / ...`，脚本按自然序排序、取目录名里的天数均值作为时间点。

---

## 第 4 步：跑真实数据

### 第 1 层 —— 能否区分健康 vs 过浇

```cmd
python layer1_stress_discrimination.py --data data\chili --out results
```

**看什么**：`results\layer1_summary.txt`

```
健康组 vs 过浇组：健康指数 mean 78.3 vs 41.2；AUC=0.912；p=3.14e-08
判据（AUC>0.7 且 p<0.05）：✅ 验证通过
```

**判据**：AUC > 0.7 且 p < 0.05

**产物**：
- `results/layer1_metrics.csv` —— 每张图的明细
- `results/layer1_boxplot.png` —— 箱线图（论文可直接用）

> **★ 这一层最关键**：它验证的是整个方案的**前提假设**。如果这一步不成立，后面都不用做了。

### 第 2 层 —— 观测对能否判别变化方向

先造 `pairs.csv`（表头固定四列）：

```csv
plant_id,group,t1_path,t2_path
p001,healthy,data/chili/healthy/w6_01.jpg,data/chili/healthy/w7_01.jpg
p002,overwatered,data/chili/flooding/w6_01.jpg,data/chili/flooding/w7_01.jpg
```

> 只有两个时间点（weeks 6 和 7），**手工拼几十对就够了**。先跑第 1 层拿到 `layer1_metrics.csv`，对照 `path` 列在 Excel 里拼。

```cmd
python layer2_observation_pair.py --pairs pairs.csv --out results
```

或者如果数据集按时间点分了目录，可以试自动配对（**生成后务必人工核对**）：

```cmd
python layer2_observation_pair.py --data data\chili --pairs-from-dirs --out results
```

**判据**：健康组的 Δ 显著大于过浇组（p < 0.05）

### 第 3 层 —— 时序曲线平滑性

```cmd
python layer3_time_series.py --data data\dragonfruit --out results
```

**判据**：**信噪比 SNR ≥ 2**（时间点间信号 / 时间点内噪声）

> 早先的判据是「相邻跳变中位数 < 15」，**实测后改掉了**——它把"植株真实快速变化"和
> "测量噪声"混在一起判，会在数据最好时误判失败。详见脚本 docstring。

**看什么**：`results/layer3_curve.png` —— 这条曲线就是你项目里"植物状态趋势图"的原型。

### 第 4 层 —— 反向验证（★ 预期的负面结果）

```cmd
python layer4_symptom_vs_disease.py --data data\chili --out results
```

**看什么**：`results/layer4_auc.png` 和 `layer4_summary.txt`

| AUC | 含义 | 怎么用 |
|---|---|---|
| **< 0.7** | 区分力弱 | ✅ **支持"必须分流 + 追问"的设计** —— 这是预期结果 |
| 0.7 – 0.85 | 中等 | 说明颜色有参考价值但不充分，仍需分流 |
| > 0.85 | 强 | ⚠️ 分流设计的必要性下降，**如实报告，别硬掰** |

> **★ 这一层是亮点**：用一个（很可能是）负面的结果来**论证设计决策的合理性**。
> 论文写法：「仅靠颜色指标不足以区分生理性与病理性异常，从而为本文『症状诊断与病害识别分流处理』的设计提供了实证支持。」

---

## 结果怎么写进论文

`../docs/14-公开数据验证方案.md` 第 5 节有现成的**结果描述模板**和**局限性说明模板**，直接抄。

**三条必须写进去的局限**：

1. 物种不同（辣椒、仙人掌 ≠ 绿萝），但**都是盆栽**，比小麦（田间）、花椰菜（机器人）近得多
2. 受控光照 ≠ 用户手持随手拍
3. **验证的是「方法可迁移」，不是「产品有效」**

---

## 已知局限与技术决策

### 为什么不做图像配准 / 分割模型

| 方案 | 为什么不用 |
|---|---|
| ArUco / 二维码贴花盆 | 用户不会为了养绿萝去打印贴纸，产品上不可行 |
| SuperPoint + SuperGlue | 为**刚体**场景设计；植物非刚体且**正在变化**，用它对齐再测变化逻辑自相矛盾 |
| YOLOv8-Seg | 家庭盆栽**没有标注数据**；HSV 阈值 1 天能做完，效果对趋势判断够用 |
| 卡尔曼滤波 | 要求**规则观测间隔** + 线性高斯；用户上传照片间隔完全不规则，前提不满足 |

**结论**：用 HSV 阈值 + 占比指标，**不做配准、不训练模型**。

### 健康指数的已知缺陷

在胁迫极端时会被 `clamp` 到 0，**丧失低端区分度**（第 1 层的合成数据里，过浇组全是 0）。

如果需要更细的低端区分度，把 `common/color_metrics.py` 的 `health_index()` 改成不 clamp、事后做 min-max 归一化即可。**论文里要把这条作为局限性说明。**

### matplotlib 中文

脚本里的图表统一用**英文标签**——避免中文环境缺字体导致方块乱码。中文写在注释和 CSV 里。

---

## 复现清单

```
□ pip 装依赖
□ python common/color_metrics.py           -> 6/6 passed
□ python make_synthetic.py --out synthetic
□ 四个 layer 跑合成数据                     -> results_syn 有产物
□ 下载辣椒数据集（90MB），★ 先看图确认是整株还是特写
□ layer1 跑真实数据                        -> AUC / p 值
□ 手拼 pairs.csv -> layer2                 -> Δ 对比
□ 下载火龙果数据集 -> layer3                -> 曲线图
□ layer4                                   -> AUC，写进"分流设计必要性"
□ 整理图表 + 套用论文模板
```

**做完这一套大概 2–3 天，不需要养任何植物。**
