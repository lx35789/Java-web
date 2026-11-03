# -*- coding: utf-8 -*-
"""
室内植物病害分类训练脚本
- 自动扫描 "<root>/<Species>/<Class>/*.jpg" 结构（也兼容平铺的 <Class>/*.jpg）
- 分层切分 train/val/test，类别均衡采样，可选每类上限
- timm 骨干 + 迁移学习，训练完导出 ONNX，并做 ONNX vs PyTorch 一致性校验
用法示例：
  python train_indoor.py --data "D:\\indoor-plant-data\\main\\Final Dataset" \
      --out "D:\\indoor-plant-data\\model" --arch mobilenetv3_small_100 \
      --img-size 160 --epochs 10 --cap 600 --batch 64
"""
import argparse
import json
import os
import random
import sys
import time
from collections import Counter

# 预训练权重走国内镜像（huggingface.co 在本机不可达）
os.environ.setdefault("HF_ENDPOINT", "https://hf-mirror.com")

import numpy as np
import torch
import torch.nn as nn
from PIL import Image
from torch.utils.data import DataLoader, Dataset, WeightedRandomSampler
import torchvision.transforms as T

EXTS = (".jpg", ".jpeg", ".png", ".bmp", ".webp")

# 类别英文名 -> 中文展示名（用于 App 展示与标签文件）
CN = {
    "Aloe_Healthy": "芦荟·健康", "Aloe_Sunburn": "芦荟·晒伤",
    "Aloe_Leaf_Spot": "芦荟·叶斑病", "Aloe_Anthracnose": "芦荟·炭疽病",
    "Aloe_Rust": "芦荟·锈病",
    "Chrysanthemum_Healthy": "菊花·健康",
    "Chrysanthemum_Bacterial_Leaf_Spot": "菊花·细菌性叶斑病",
    "Chrysanthemum_Septoria_Leaf_Spot": "菊花·斑枯病",
    "Hibiscus_Healthy": "扶桑·健康", "Hibiscus_Blight": "扶桑·枯萎病",
    "Hibiscus_Necrosis": "扶桑·叶坏死", "Hibiscus_Scorch": "扶桑·日灼",
    "Money_Plant_Healthy": "绿萝·健康",
    "Money_Plant_Manganese_Toxicity": "绿萝·锰中毒",
    "Money_Plant_Bacterial_Wilt": "绿萝·细菌性枯萎病",
    "Money_Plant_Chlorosis": "绿萝·黄化病",
    "Rose_Healthy": "月季·健康", "Rose_Black_Spot": "月季·黑斑病",
    "Rose_Insect_Damage": "月季·虫害", "Rose_Downy_Mildew": "月季·霜霉病",
    "Rose_Mosaic_Virus": "月季·花叶病毒病", "Rose_Powdery_Mildew": "月季·白粉病",
    "Rose_Rust": "月季·锈病", "Rose_Yellow_Mosaic_Virus": "月季·黄化花叶病毒病",
    "Turmeric_Healthy": "姜黄·健康", "Turmeric_Blotch": "姜黄·斑点病",
    "Turmeric_Leaf_Spot": "姜黄·叶斑病",
    "Turmeric_Aphid_Infestation": "姜黄·蚜虫危害",
    "Turmeric_Leaf_Necrosis": "姜黄·叶枯坏死",
}

LOGF = None

# 类别码前缀 -> 物种名。用于把 Aloe_Anthracnose 拆成 (Aloe, Anthracnose)
SPECIES_PREFIX = ["Aloe", "Chrysanthemum", "Hibiscus", "Money_Plant", "Rose", "Turmeric",
                  "Snake_Plant", "Spider_Plant", "Cactus"]


def split_code(code):
    """'Money_Plant_Bacterial_Wilt' -> ('Money Plant', 'Bacterial Wilt')"""
    for sp in sorted(SPECIES_PREFIX, key=len, reverse=True):
        if code.startswith(sp + "_"):
            return sp.replace("_", " "), code[len(sp) + 1:].replace("_", " ")
    return code, code


def log(msg):
    line = time.strftime("%H:%M:%S ") + str(msg)
    print(line, flush=True)
    if LOGF:
        try:
            with open(LOGF, "a", encoding="utf-8") as f:
                f.write(line + "\n")
        except Exception:
            pass


def scan(root):
    items = []
    for dirpath, _, filenames in os.walk(root):
        imgs = [f for f in filenames if f.lower().endswith(EXTS)]
        if not imgs:
            continue
        cls = os.path.basename(dirpath.rstrip("\\/"))
        for f in imgs:
            items.append((os.path.join(dirpath, f), cls))
    return items


class BlackBgReplace:
    """
    把"叶片 + 纯黑底"图片的黑底**换成真实背景**（用黑底本身当 alpha 掩膜）。
    比裁外接框彻底得多：芦荟叶片细长，外接框内部仍有 45%~85% 是黑的
    （实测：裁完外接框后近黑占比仍达 45%~85%）。

    原理：黑底图片天然自带完美掩膜 —— mask = (通道最大值 >= 阈值)。
    合成公式：out = 叶片 * mask + 背景 * (1 - mask)

    背景一半用真实图片（有纹理），一半用随机纯色，避免模型又学到新的"固定背景捷径"。
    """

    def __init__(self, bg_images, p=1.0, threshold=25, feather=1.2):
        self.bg = list(bg_images or [])
        self.p = p
        self.threshold = threshold
        self.feather = feather

    def __call__(self, img):
        if random.random() > self.p:
            return img
        try:
            a = np.asarray(img.convert("RGB"))
            m = a.max(axis=2) >= self.threshold
            cover = float(m.mean())
            if cover > 0.98 or m.sum() < 200:
                return img                      # 本来就没黑底（或整张全黑，跳过）
            w, h = img.size
            if self.bg and random.random() < 0.5:
                b = random.choice(self.bg)
                bw, bh = b.size
                k = max(w / bw, h / bh) * random.uniform(1.0, 1.5)
                b = b.resize((max(w, int(bw * k)), max(h, int(bh * k))), Image.BILINEAR)
                bg = np.asarray(b.crop((random.randint(0, max(0, b.width - w)),
                                        random.randint(0, max(0, b.height - h)),
                                        random.randint(0, max(0, b.width - w)) + w,
                                        random.randint(0, max(0, b.height - h)) + h)))
            else:
                bg = np.full((h, w, 3), (random.randint(40, 235), random.randint(40, 235),
                                         random.randint(40, 235)), np.uint8)
            # 掩膜轻微羽化，避免出现训练集里不存在的硬边
            from PIL import ImageFilter
            mm = Image.fromarray((m * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(self.feather))
            alpha = (np.asarray(mm, np.float32) / 255.0)[..., None]
            out = a.astype(np.float32) * alpha + bg.astype(np.float32) * (1 - alpha)
            return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8))
        except Exception:
            return img


class CropBlackPadding:
    """
    裁掉"叶片 + 纯黑背景"这类图片的黑边。

    动机（实测）：芦荟那 16,318 张图是把叶片抠出来后贴到黑底上生成的，
    近黑像素占 74%~87%，叶片只占画面 5%~45%；而其余 5 个物种只有 0~6%。
    模型因此在芦荟上学到了"黑底构图"这个捷径，一换真实背景就崩。

    做法：以"通道最大值 >= 25"为内容掩膜求外接框。不做任何学习、不做形态学，
    毫秒级。若外接框已覆盖 95% 以上画面，说明本来就没有黑边，原样返回。
    """

    def __init__(self, threshold=25, margin=0.04, max_cover=0.95):
        self.threshold = threshold
        self.margin = margin
        self.max_cover = max_cover

    def __call__(self, img):
        try:
            a = np.asarray(img.convert("RGB"))
            m = a.max(axis=2) >= self.threshold
            if m.mean() >= self.max_cover:
                return img
            ys, xs = np.where(m)
            if len(xs) < 100:
                return img
            x0, x1, y0, y1 = int(xs.min()), int(xs.max()), int(ys.min()), int(ys.max())
            w, h = x1 - x0 + 1, y1 - y0 + 1
            pad = int(self.margin * max(w, h))
            x0, y0 = max(0, x0 - pad), max(0, y0 - pad)
            x1, y1 = min(img.width, x1 + pad), min(img.height, y1 + pad)
            if x1 - x0 < 32 or y1 - y0 < 32:
                return img
            return img.crop((x0, y0, x1, y1))
        except Exception:
            return img


class LeafDataset(Dataset):
    def __init__(self, samples, tf, auto_crop=False):
        self.samples = samples
        self.tf = tf
        self.auto_crop = auto_crop

    def __len__(self):
        return len(self.samples)

    def __getitem__(self, i):
        path, y = self.samples[i]
        try:
            img = Image.open(path).convert("RGB")
        except Exception:
            img = Image.new("RGB", (224, 224), (128, 128, 128))
        if self.auto_crop:
            img = CropBlackPadding()(img)
        return self.tf(img), y


class RandomBackground:
    """
    域随机化：把叶片随机缩放后贴到随机背景上，让模型学会**忽略背景、只看叶片**。

    动机（实测数据，芦荟锈病 2 类）：
        叶片特写（干净背景）        → 82~85%
        同一张叶子缩小到 55%        → 85%   ← 尺度不是问题
        同一张叶子贴上任意背景      → 50% 且召回 0%（全部判健康）  ← 背景才是问题
        贴背景后再把叶片裁出来      → 80~82%（恢复）
    所以最根本的解法不是"推理时加分割"，而是**训练时就见过各种背景**。
    背景素材直接用数据集里其他物种/类别的图片，有真实纹理，成本为零。
    """

    def __init__(self, bg_paths, p=0.5, scale=(0.3, 1.0)):
        self.bg_paths = list(bg_paths or [])
        self.p = p
        self.scale = scale

    def __call__(self, img):
        if random.random() > self.p:
            return img
        w, h = img.size
        # 一半用真实图片纹理，一半用纯色/渐变 —— 只靠纯色会让模型对真实复杂背景仍然不适应
        use_real = self.bg_paths and random.random() < 0.5
        canvas = None
        if use_real:
            try:
                b = random.choice(self.bg_paths)
                bw, bh = b.size
                k = max(w / bw, h / bh) * random.uniform(1.0, 1.8)
                b = b.resize((max(w, int(bw * k)), max(h, int(bh * k))), Image.BILINEAR)
                x = random.randint(0, max(0, b.width - w))
                y = random.randint(0, max(0, b.height - h))
                canvas = b.crop((x, y, x + w, y + h))
            except Exception:
                canvas = None
        if canvas is None:
            c = (random.randint(40, 235), random.randint(40, 235), random.randint(40, 235))
            canvas = Image.new("RGB", (w, h), c)
        s = random.uniform(*self.scale)
        sw, sh = max(12, int(w * s)), max(12, int(h * s))
        leaf = img.resize((sw, sh), Image.BILINEAR)
        canvas.paste(leaf, (random.randint(0, max(0, w - sw)), random.randint(0, max(0, h - sh))))
        return canvas


def collect_backgrounds(root, limit=120, max_side=384):
    """
    预先把背景素材加载进内存并缩到 max_side。

    为什么必须预加载：数据集里的图动辄 3000~4000px，每个样本都现场解码一张
    JPEG 要 200ms 以上 —— 实测会让单轮训练时间从 65 秒涨到 5 分钟以上。
    缩到 384px 后常驻内存，单样本只剩一次廉价裁剪。
    """
    out = []
    if not root:
        return out
    for dirpath, _, filenames in os.walk(root):
        for f in filenames:
            if f.lower().endswith(EXTS):
                try:
                    im = Image.open(os.path.join(dirpath, f)).convert("RGB")
                    k = max_side / max(im.size)
                    if k < 1:
                        im = im.resize((max(8, int(im.width * k)), max(8, int(im.height * k))),
                                       Image.BILINEAR)
                    out.append(im)
                except Exception:
                    pass
                if len(out) >= limit:
                    return out
    return out


def stratified_split(items, classes, ratios=(0.7, 0.15, 0.15), seed=42):
    rng = random.Random(seed)
    by_cls = {}
    for p, c in items:
        by_cls.setdefault(c, []).append(p)
    tr, va, te = [], [], []
    for c in classes:
        ps = sorted(by_cls[c])
        rng.shuffle(ps)
        n = len(ps)
        n_va = max(1, int(n * ratios[1]))
        n_te = max(1, int(n * ratios[2]))
        idx = classes.index(c)
        va += [(p, idx) for p in ps[:n_va]]
        te += [(p, idx) for p in ps[n_va:n_va + n_te]]
        tr += [(p, idx) for p in ps[n_va + n_te:]]
    rng.shuffle(tr)
    return tr, va, te


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--arch", default="mobilenetv3_small_100")
    ap.add_argument("--img-size", type=int, default=160)
    ap.add_argument("--epochs", type=int, default=10)
    ap.add_argument("--batch", type=int, default=64)
    ap.add_argument("--lr", type=float, default=1e-3)
    ap.add_argument("--cap", type=int, default=600, help="每类最多用多少张（0=不限）")
    ap.add_argument("--workers", type=int, default=0)
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--pretrained", type=int, default=1)
    ap.add_argument("--export-onnx", type=int, default=1)
    ap.add_argument("--bg-random", type=float, default=0.0,
                    help="背景随机化的概率 0~1（0=关闭）。让模型忽略背景，见 RandomBackground 注释")
    ap.add_argument("--bg-dir", default="", help="背景素材目录（递归扫描），留空则用随机纯色")
    ap.add_argument("--auto-crop", type=int, default=1,
                    help="自动裁掉黑边（针对芦荟那种'叶片+黑底'的图）；1=开 0=关")
    ap.add_argument("--replace-bg", type=float, default=0.0,
                    help="黑底替换概率 0~1（芦荟那类'叶片+黑底'数据建议设 1.0）")
    args = ap.parse_args()

    os.makedirs(args.out, exist_ok=True)
    global LOGF
    LOGF = os.path.join(args.out, "train.log")
    torch.manual_seed(args.seed)
    random.seed(args.seed)
    np.random.seed(args.seed)
    torch.set_num_threads(max(1, os.cpu_count() or 4))

    log("=" * 70)
    log("数据目录: %s" % args.data)
    log("输出目录: %s" % args.out)
    log("CPU 线程数: %d ; CUDA: %s" % (torch.get_num_threads(), torch.cuda.is_available()))

    items = scan(args.data)
    if not items:
        log("!! 没找到图片，检查路径")
        sys.exit(1)
    cnt = Counter(c for _, c in items)
    classes = sorted(cnt.keys())
    log("扫描到 %d 张图片，%d 个类别" % (len(items), len(classes)))
    for c in classes:
        log("    %-40s %6d  %s" % (c, cnt[c], CN.get(c, "")))

    # 每类限流，控制训练时长
    if args.cap > 0:
        rng = random.Random(args.seed)
        kept = []
        for c in classes:
            ps = [p for p, cc in items if cc == c]
            rng.shuffle(ps)
            kept += [(p, c) for p in ps[:args.cap]]
        items = kept
        log("按每类上限 %d 抽样后：%d 张" % (args.cap, len(items)))

    tr, va, te = stratified_split(items, classes, seed=args.seed)
    log("切分：train=%d  val=%d  test=%d" % (len(tr), len(va), len(te)))

    mean = (0.485, 0.456, 0.406)
    std = (0.229, 0.224, 0.225)
    s = args.img_size
    bg_pool = collect_backgrounds(args.bg_dir) if (args.bg_random > 0 or args.replace_bg > 0) else []
    tf_tr = T.Compose([
        BlackBgReplace(bg_pool, p=args.replace_bg),
        T.RandomResizedCrop(s, scale=(0.7, 1.0)),
        T.RandomHorizontalFlip(),
        T.RandomRotation(15),
        T.ColorJitter(0.2, 0.2, 0.2, 0.05),
        RandomBackground(bg_pool, p=args.bg_random),
        T.ToTensor(),
        T.Normalize(mean, std),
    ])
    if args.replace_bg > 0:
        log("已启用黑底替换：p=%.2f（用黑底当掩膜合成到真实背景，背景素材 %d 张）"
            % (args.replace_bg, len(bg_pool)))
    if args.bg_random > 0:
        log("已启用背景随机化：p=%.2f 背景素材=%d 张（目录 %s）"
            % (args.bg_random, len(bg_pool), args.bg_dir or "随机纯色"))
    tf_ev = T.Compose([T.Resize(int(s * 1.15)), T.CenterCrop(s), T.ToTensor(),
                       T.Normalize(mean, std)])

    ds_tr = LeafDataset(tr, tf_tr, auto_crop=args.auto_crop)
    ds_va = LeafDataset(va, tf_ev, auto_crop=args.auto_crop)
    ds_te = LeafDataset(te, tf_ev, auto_crop=args.auto_crop)

    # 保存切分清单：评测必须用严格留出的 test 集，否则准确率不可信
    try:
        with open(os.path.join(args.out, "split.json"), "w", encoding="utf-8") as f:
            json.dump({"classes": classes,
                       "train": [p for p, _ in tr],
                       "val": [p for p, _ in va],
                       "test": [p for p, _ in te]}, f, ensure_ascii=False)
        log("切分清单已保存 split.json（train=%d val=%d test=%d）" % (len(tr), len(va), len(te)))
    except Exception as e:
        log("!! 保存切分失败：%r" % (e,))

    # 类别均衡采样，缓解 Rose_Healthy 5530 vs Money_Plant_Chlorosis 115 的失衡
    tr_labels = [y for _, y in tr]
    per_cls = Counter(tr_labels)
    w = [1.0 / per_cls[y] for y in tr_labels]
    sampler = WeightedRandomSampler(w, num_samples=len(tr_labels), replacement=True)

    dl_tr = DataLoader(ds_tr, batch_size=args.batch, sampler=sampler,
                       num_workers=args.workers, drop_last=True)
    dl_va = DataLoader(ds_va, batch_size=args.batch, shuffle=False, num_workers=args.workers)
    dl_te = DataLoader(ds_te, batch_size=args.batch, shuffle=False, num_workers=args.workers)

    import timm
    log("创建模型: %s (pretrained=%d)" % (args.arch, args.pretrained))
    try:
        model = timm.create_model(args.arch, pretrained=bool(args.pretrained),
                                  num_classes=len(classes))
    except Exception as e:
        log("!! 预训练权重加载失败（%r），改为从零训练" % (e,))
        model = timm.create_model(args.arch, pretrained=False, num_classes=len(classes))

    dev = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model.to(dev)
    nparam = sum(p.numel() for p in model.parameters())
    log("参数量: %.2f M" % (nparam / 1e6))

    # ---- 断点保险：每个 epoch 只要刷新了最佳精度，就把产物立刻落盘 ----
    # 之前吃过亏：训练跑到第 3 轮进程被杀，20 分钟白跑且一个文件都没留下。
    labels = {}
    for i, c in enumerate(classes):
        sp, dis = split_code(c)
        labels[str(i)] = {"code": c, "cn": CN.get(c, c), "species": sp, "disease": dis}

    def write_artifacts(m, tag):
        try:
            torch.save(m.state_dict(), os.path.join(args.out, "best.pt"))
            with open(os.path.join(args.out, "labels.json"), "w", encoding="utf-8") as f:
                json.dump(labels, f, ensure_ascii=False, indent=1)
            with open(os.path.join(args.out, "meta.json"), "w", encoding="utf-8") as f:
                json.dump({"input_size": args.img_size, "arch": args.arch,
                           "num_classes": len(classes), "checkpoint": tag,
                           "trained_at": time.strftime("%Y-%m-%d %H:%M:%S"),
                           "dataset": args.data, "cap_per_class": args.cap}, f,
                          ensure_ascii=False, indent=1)
            if args.export_onnx:
                m.eval()
                dummy = torch.randn(1, 3, s, s, device=dev)
                p = os.path.join(args.out, "disease.onnx")
                try:
                    torch.onnx.export(m, dummy, p, opset_version=12, dynamo=False,
                                      input_names=["input"], output_names=["logits"])
                except TypeError:
                    torch.onnx.export(m, dummy, p, opset_version=12,
                                      input_names=["input"], output_names=["logits"])
                log("  ↓ 已落盘 checkpoint(%s)：disease.onnx %.2f MB"
                    % (tag, os.path.getsize(p) / 1e6))
        except Exception as e:
            log("  !! 落盘失败：%r" % (e,))

    crit = nn.CrossEntropyLoss(label_smoothing=0.05)
    opt = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=1e-4)
    # OneCycleLR 而不是 CosineAnnealingLR：
    # 实测（每类 40 张、关增广、死记硬背测试）前 30 轮会卡在 ~78% 的平台期，
    # 第 40 轮才突然拟合到 100%。换成 OneCycle + 3e-3 峰值学习率后能顺利越过平台。
    # 用旧的 1e-3 + 无预热余弦时，5 类芦荟训练集都拟合不了（train_loss 1.54 vs 随机 1.61）。
    total_steps = max(1, args.epochs * max(1, len(dl_tr)))
    sched = torch.optim.lr_scheduler.OneCycleLR(
        opt, max_lr=args.lr, total_steps=total_steps,
        pct_start=max(0.05, min(0.3, 3.0 / max(args.epochs, 1))),
        div_factor=10.0, final_div_factor=100.0)
    log("学习率调度：OneCycleLR 峰值 %.1e，总步数 %d" % (args.lr, total_steps))


    def evaluate(loader):
        model.eval()
        correct = top3 = total = 0
        loss_sum = 0.0
        with torch.no_grad():
            for x, y in loader:
                x, y = x.to(dev), y.to(dev)
                out = model(x)
                loss_sum += crit(out, y).item() * y.size(0)
                k = min(3, out.size(1))
                _, pred = out.topk(k, dim=1)
                correct += (pred[:, 0] == y).sum().item()
                top3 += sum(1 for i in range(y.size(0)) if y[i] in pred[i])
                total += y.size(0)
        return loss_sum / max(total, 1), correct / max(total, 1), top3 / max(total, 1)

    best_acc, best_state = 0.0, None
    t_start = time.time()
    for ep in range(1, args.epochs + 1):
        model.train()
        t0 = time.time()
        run_loss = 0.0
        seen = 0
        for it, (x, y) in enumerate(dl_tr):
            x, y = x.to(dev), y.to(dev)
            opt.zero_grad()
            out = model(x)
            loss = crit(out, y)
            loss.backward()
            opt.step()
            sched.step()
            run_loss += loss.item() * y.size(0)
            seen += y.size(0)
            if (it + 1) % 50 == 0:
                log("  ep%d it%d/%d loss=%.4f 用时%.0fs"
                    % (ep, it + 1, len(dl_tr), run_loss / seen, time.time() - t0))
        vl, vacc, vtop3 = evaluate(dl_va)
        log("EPOCH %d  train_loss=%.4f  val_loss=%.4f  val_acc=%.4f  top3=%.4f  (%.0fs)"
            % (ep, run_loss / max(seen, 1), vl, vacc, vtop3, time.time() - t0))
        if vacc > best_acc:
            best_acc = vacc
            best_state = {k: v.detach().cpu().clone() for k, v in model.state_dict().items()}
            log("  ↑ 新的最佳 val_acc=%.4f" % best_acc)
            write_artifacts(model, "epoch%d_acc%.4f" % (ep, vacc))

    log("训练总用时 %.1f 分钟，最佳 val_acc=%.4f" % ((time.time() - t_start) / 60, best_acc))
    if best_state:
        model.load_state_dict(best_state)

    tl, tacc, ttop3 = evaluate(dl_te)
    log("测试集: loss=%.4f  acc=%.4f  top3=%.4f" % (tl, tacc, ttop3))

    torch.save(model.state_dict(), os.path.join(args.out, "best.pt"))

    # 导出成 AI 服务能直接读的三件套：labels.json / meta.json / disease.onnx
    # 字段约定见 ai-service/app/disease.py，改这里必须同步改那边。
    labels = {}
    for i, c in enumerate(classes):
        sp, dis = split_code(c)
        labels[str(i)] = {"code": c, "cn": CN.get(c, c), "species": sp, "disease": dis}
    with open(os.path.join(args.out, "labels.json"), "w", encoding="utf-8") as f:
        json.dump(labels, f, ensure_ascii=False, indent=1)
    with open(os.path.join(args.out, "meta.json"), "w", encoding="utf-8") as f:
        json.dump({"input_size": args.img_size, "arch": args.arch,
                   "num_classes": len(classes),
                   "trained_at": time.strftime("%Y-%m-%d %H:%M:%S"),
                   "dataset": args.data, "cap_per_class": args.cap}, f,
                  ensure_ascii=False, indent=1)
    with open(os.path.join(args.out, "metrics.json"), "w", encoding="utf-8") as f:
        json.dump({"arch": args.arch, "img_size": args.img_size, "epochs": args.epochs,
                   "n_train": len(tr), "n_val": len(va), "n_test": len(te),
                   "n_classes": len(classes), "best_val_acc": best_acc,
                   "test_acc": tacc, "test_top3": ttop3,
                   "params_M": nparam / 1e6,
                   "minutes": (time.time() - t_start) / 60}, f, ensure_ascii=False, indent=1)

    if args.export_onnx:
        try:
            model.eval()
            dummy = torch.randn(1, 3, s, s, device=dev)
            p = os.path.join(args.out, "disease.onnx")
            try:
                torch.onnx.export(model, dummy, p, opset_version=12, dynamo=False,
                                  input_names=["input"], output_names=["logits"])
            except TypeError:
                torch.onnx.export(model, dummy, p, opset_version=12,
                                  input_names=["input"], output_names=["logits"])
            log("已导出 ONNX: %s (%.2f MB)" % (p, os.path.getsize(p) / 1e6))
            try:
                import onnxruntime as ort
                sess = ort.InferenceSession(p, providers=["CPUExecutionProvider"])
                o1 = sess.run(None, {"input": dummy.cpu().numpy()})[0]
                with torch.no_grad():
                    o2 = model(dummy).cpu().numpy()
                log("ONNX vs PyTorch 最大差异 = %.3e" % float(np.abs(o1 - o2).max()))
            except Exception as e:
                log("ONNX 一致性校验跳过: %r" % (e,))
        except Exception as e:
            log("!! ONNX 导出失败: %r" % (e,))

    log("ALL_DONE")


if __name__ == "__main__":
    main()
