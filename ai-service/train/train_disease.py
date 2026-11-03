"""
植语助手 · 病害分类模型训练脚本
================================

用法
----
1) 装依赖（只在训练机上装，服务器不需要）：
       pip install torch torchvision onnx scikit-learn

2) 准备数据集，放在任意目录，结构是标准的 ImageFolder：
       data/
         train/  <类别名>/ xxx.jpg ...
         valid/  <类别名>/ xxx.jpg ...
         test/   <类别名>/ xxx.jpg ...

   两个数据集都是这个结构，直接指过去即可：

   ★ 主训练集（推荐）—— Indoor Plant Disease Detection Dataset，21,097 张，CC BY 4.0
       https://www.kaggle.com/datasets/abdulahad0296/indoor-plant-disease-detection-dataset
       覆盖：芦荟 / 仙人掌 / 绿萝(Money Plant) / 虎皮兰 / 吊兰

     对照集（可选）—— PlantVillage，54,306 张，14 种农作物
       https://github.com/spmohanty/PlantVillage-Dataset
       用途：量化「实验室 → 家庭」的域不匹配，写进论文的对照实验。

3) 训练：
       python train_disease.py --data D:/datasets/indoor-plant --out ../models
       python train_disease.py --data D:/datasets/plantvillage/color --out ../models-plantvillage

4) 产出（写到 --out 目录）：
       disease.onnx    模型
       labels.json     {"0":"Aloe_Vera___Healthy", ...}
       meta.json       训练信息（架构/输入尺寸/准确率/时间）

命名约定
--------
类别文件夹名建议用 `物种___病害` 格式（三个下划线），例如：
       Aloe_Vera___Healthy
       Aloe_Vera___Rust
       Snake_Plant___Leaf_Withering
AI 服务会按 `___` 拆出物种名和病害名，直接对应 db 的 disease_info.code。
"""
from __future__ import annotations

import argparse
import json
import time
from pathlib import Path

import torch
import torch.nn as nn
from torch.utils.data import DataLoader
from torchvision import datasets, models, transforms

SEED = 42


def build_transforms(size: int = 224):
    """训练/验证两套 transform。推理端 app/disease.py 必须与 eval_tf 保持一致。"""
    train_tf = transforms.Compose(
        [
            transforms.RandomResizedCrop(size, scale=(0.7, 1.0)),
            transforms.RandomHorizontalFlip(),
            transforms.RandomRotation(15),
            # 颜色抖动刻意调小：叶病的关键特征就是颜色，抖太猛会把病斑洗掉
            transforms.ColorJitter(brightness=0.15, contrast=0.15, saturation=0.15),
            transforms.ToTensor(),
            transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225]),
        ]
    )
    eval_tf = transforms.Compose(
        [
            transforms.Resize(int(size * 256 / 224)),
            transforms.CenterCrop(size),
            transforms.ToTensor(),
            transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225]),
        ]
    )
    return train_tf, eval_tf


def build_model(arch: str, num_classes: int) -> nn.Module:
    if arch == "mobilenet_v3_small":
        m = models.mobilenet_v3_small(weights=models.MobileNet_V3_Small_Weights.IMAGENET1K_V1)
        m.classifier[3] = nn.Linear(m.classifier[3].in_features, num_classes)
    elif arch == "efficientnet_b0":
        m = models.efficientnet_b0(weights=models.EfficientNet_B0_Weights.IMAGENET1K_V1)
        m.classifier[1] = nn.Linear(m.classifier[1].in_features, num_classes)
    elif arch == "resnet18":
        m = models.resnet18(weights=models.ResNet18_Weights.IMAGENET1K_V1)
        m.fc = nn.Linear(m.fc.in_features, num_classes)
    else:
        raise ValueError(f"不支持的架构：{arch}")
    return m


def evaluate(model: nn.Module, loader: DataLoader, device: torch.device):
    model.eval()
    correct = total = 0
    with torch.no_grad():
        for x, y in loader:
            x, y = x.to(device), y.to(device)
            pred = model(x).argmax(1)
            correct += (pred == y).sum().item()
            total += y.numel()
    return correct / max(1, total)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", required=True, help="数据集根目录（含 train/valid[/test]）")
    ap.add_argument("--out", default="../models", help="模型输出目录")
    ap.add_argument("--arch", default="mobilenet_v3_small",
                    choices=["mobilenet_v3_small", "efficientnet_b0", "resnet18"])
    ap.add_argument("--epochs", type=int, default=15)
    ap.add_argument("--batch", type=int, default=32)
    ap.add_argument("--lr", type=float, default=3e-4)
    ap.add_argument("--size", type=int, default=224)
    ap.add_argument("--workers", type=int, default=4)
    args = ap.parse_args()

    torch.manual_seed(SEED)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"[env] device={device}  arch={args.arch}  size={args.size}")

    root = Path(args.data)
    train_dir, valid_dir = root / "train", root / "valid"
    if not train_dir.exists():
        raise SystemExit(f"找不到 {train_dir}，请确认目录结构是 ImageFolder 格式")
    if not valid_dir.exists():
        valid_dir = train_dir  # 没提供 valid 就退化成用训练集监控（仅为了跑通）

    train_tf, eval_tf = build_transforms(args.size)
    train_ds = datasets.ImageFolder(train_dir, transform=train_tf)
    valid_ds = datasets.ImageFolder(valid_dir, transform=eval_tf)

    classes = train_ds.classes
    print(f"[data] 类别数={len(classes)}  训练={len(train_ds)}  验证={len(valid_ds)}")
    for i, c in enumerate(classes):
        print(f"        {i:>3}  {c}")

    train_loader = DataLoader(train_ds, batch_size=args.batch, shuffle=True,
                              num_workers=args.workers, pin_memory=True)
    valid_loader = DataLoader(valid_ds, batch_size=args.batch, shuffle=False,
                              num_workers=args.workers, pin_memory=True)

    model = build_model(args.arch, len(classes)).to(device)
    criterion = nn.CrossEntropyLoss()
    optimizer = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=1e-4)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=args.epochs)

    best_acc, best_state = 0.0, None
    for epoch in range(1, args.epochs + 1):
        model.train()
        running = 0.0
        for x, y in train_loader:
            x, y = x.to(device), y.to(device)
            optimizer.zero_grad()
            loss = criterion(model(x), y)
            loss.backward()
            optimizer.step()
            running += loss.item() * y.size(0)
        scheduler.step()

        acc = evaluate(model, valid_loader, device)
        print(f"[epoch {epoch:>2}/{args.epochs}] loss={running / len(train_ds):.4f}  valid_acc={acc:.4f}")
        if acc > best_acc:
            best_acc = acc
            best_state = {k: v.detach().cpu().clone() for k, v in model.state_dict().items()}

    if best_state:
        model.load_state_dict(best_state)
    print(f"[best] valid_acc={best_acc:.4f}")

    # ---------------- 导出 ONNX ----------------
    out_dir = Path(args.out).resolve()
    out_dir.mkdir(parents=True, exist_ok=True)

    model.eval().to("cpu")
    dummy = torch.randn(1, 3, args.size, args.size)
    onnx_path = out_dir / "disease.onnx"
    torch.onnx.export(
        model, dummy, str(onnx_path),
        input_names=["input"], output_names=["logits"],
        dynamic_axes={"input": {0: "batch"}, "logits": {0: "batch"}},
        opset_version=17,
    )
    print(f"[onnx] 已导出 {onnx_path}  ({onnx_path.stat().st_size / 1e6:.1f} MB)")

    # 精度校验：ONNX 输出应与 PyTorch 一致
    try:
        import numpy as np
        import onnxruntime as ort

        with torch.no_grad():
            torch_out = model(dummy).numpy()
        sess = ort.InferenceSession(str(onnx_path), providers=["CPUExecutionProvider"])
        onnx_out = sess.run(None, {"input": dummy.numpy()})[0]
        diff = float(np.max(np.abs(torch_out - onnx_out)))
        print(f"[check] PyTorch 与 ONNX 最大输出差 = {diff:.2e}  (应 < 1e-4)")
    except Exception as exc:
        print(f"[check] 跳过 ONNX 校验：{exc}")

    # ---------------- 标签与元信息 ----------------
    (out_dir / "labels.json").write_text(
        json.dumps({str(i): c for i, c in enumerate(classes)}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    (out_dir / "meta.json").write_text(
        json.dumps(
            {
                "arch": args.arch,
                "input_size": args.size,
                "num_classes": len(classes),
                "best_valid_acc": round(best_acc, 4),
                "dataset": str(root),
                "labels": classes,
                "trained_at": time.strftime("%Y-%m-%d %H:%M:%S"),
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    print(f"[done] labels.json / meta.json 已写入 {out_dir}")
    print("[next] 把 models 目录指给 AI 服务：export GT_MODEL_DIR=<out_dir>，然后重启 uvicorn")


if __name__ == "__main__":
    main()
