# 模型训练说明

## 一、数据准备

目录结构（按类别分文件夹，一层即可）：

```
<数据集目录>/
├── Aloe_Healthy/
│   ├── 00001.jpg
│   └── ...
├── Aloe_Rust/
└── ...
```

**关键：先预处理成 448px 再训练。**

原始数据集常是 2000~4000px 的大图。直接训练时每张图都要现场解码，
实测 29 轮训练会从 **72 分钟** 涨到 **6 小时**（约 5 倍差距）。

```python
# 预处理脚本参考（与 aloe / general 数据集用的是同一套思路）
from PIL import Image
import os
MAX_SIDE = 448
im = Image.open(src).convert("RGB")
k = MAX_SIDE / max(im.size)
if k < 1:
    im = im.resize((int(im.width * k), int(im.height * k)), Image.BILINEAR)
im.save(dst, "JPEG", quality=92)
```

已备好的两个数据集：

| 用途 | 路径 | 规模 |
|---|---|---|
| 通用 29 类 | `D:\indoor-plant-data\general_prep` | 11,315 张 / 29 类 |
| 芦荟专用 5 类 | `D:\indoor-plant-data\aloe5_prep` | 4,461 张 / 5 类 |

## 二、训练

```bash
python train_indoor.py \
  --data  <数据集目录> \
  --out   <输出目录> \
  --arch  mobilenetv3_large_100 \
  --img-size 160 \
  --epochs 29 \
  --batch 64 \
  --lr 3e-3 \
  --cap 0 \
  --workers 0 \
  --export-onnx 1
```

### 参数要点

| 参数 | 建议 | 说明 |
|---|---|---|
| `--arch` | `mobilenetv3_large_100` | 实测优于 EfficientNet-B0 / ConvNeXt-Atto / mnv3_small |
| `--img-size` | 160（通用）/ 224（单物种） | 224 更准但训练慢约 2 倍 |
| `--epochs` | 29~40 | 用 OneCycleLR，轮数要够才能越过平台期 |
| `--lr` | **3e-3** | 低于 1e-3 会连训练集都拟合不了 |
| `--workers` | **0** | Windows 上多进程 DataLoader 反而更慢 |
| `--cap` | 0 表示不限 | 每类上限，控制训练时长 |
| `--bg-random` | 0.3 | 背景随机化概率，提升换背景后的鲁棒性 |
| `--bg-dir` | 其他物种的图片目录 | 背景素材来源 |

### 可选：黑底数据集

如果数据集是"叶片 + 纯黑底"的合成图（如芦荟），加：

```bash
--replace-bg 1 --bg-dir <背景素材目录>
```

它用黑底当天然 alpha 掩膜，把叶片合成到真实背景上。
芦荟实测近黑像素占比 **74% → 0%**，是提升真实场景准确率的关键一步。

## 三、输出

```
<输出目录>/
├── disease.onnx     导出的模型（部署用）
├── best.pt          PyTorch 权重（继续训练用）
├── labels.json      类别映射（含 code / 中文名 / 物种）
├── meta.json        输入尺寸、骨干、类别数
├── metrics.json     测试集准确率、Top-3、耗时
├── split.json       train/val/test 切分清单（严格留出评测用）
└── train.log        训练日志
```

## 四、部署

把三个文件放进 AI 服务的对应目录：

```bash
# 通用模型
cp disease.onnx labels.json meta.json  <仓库>/ai-service/models/

# 物种专用模型（目录名 = 代码里的 species key，小写）
cp disease.onnx labels.json meta.json  <仓库>/ai-service/models/specialized/aloe/
```

重启 AI 服务后，`GET /health` 会显示：

```json
{
  "diseaseModel": { "available": true, "numClasses": 29 },
  "specialized": { "aloe": { "available": true, "numClasses": 5, "species": ["Aloe"] } }
}
```

**不需要改任何代码**——`app/disease.py` 的注册表会自动扫描 `models/specialized/` 下的所有子目录。

## 五、评测

训练脚本会在 `metrics.json` 里给出 `test_acc` / `test_top3`，
测试集来自 `split.json` 里独立切出来的部分，训练时没碰过。

⚠️ 但这只是**同分布**成绩。真实拍照场景（换背景、换构图、换光照）会明显下降，
这是本领域公认的域偏移问题。做真实评测请用自己手机拍的照片标一份测试集。
