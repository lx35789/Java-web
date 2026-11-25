"""Evaluate a trained checkpoint on the held-out split, including per class."""
from __future__ import annotations

import argparse
import json
from collections import defaultdict
from pathlib import Path

import timm
import torch
import torchvision.transforms as T
from torch.utils.data import DataLoader

from train_indoor import LeafDataset


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--model-dir", required=True, type=Path)
    ap.add_argument("--batch", type=int, default=64)
    args = ap.parse_args()

    split = json.loads((args.model_dir / "split.json").read_text(encoding="utf-8"))
    meta = json.loads((args.model_dir / "meta.json").read_text(encoding="utf-8"))
    classes = split["classes"]
    class_to_idx = {name: i for i, name in enumerate(classes)}
    samples = [(path, class_to_idx[Path(path).parent.name]) for path in split["test"]]

    size = int(meta["input_size"])
    tf = T.Compose([
        T.Resize(int(size * 1.15)),
        T.CenterCrop(size),
        T.ToTensor(),
        T.Normalize((0.485, 0.456, 0.406), (0.229, 0.224, 0.225)),
    ])
    dataset = LeafDataset(samples, tf, auto_crop=1)
    loader = DataLoader(dataset, batch_size=args.batch, shuffle=False, num_workers=0)

    model = timm.create_model(meta["arch"], pretrained=False, num_classes=len(classes))
    state = torch.load(args.model_dir / "best.pt", map_location="cpu", weights_only=True)
    model.load_state_dict(state)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model.to(device).eval()

    correct = defaultdict(int)
    top3 = defaultdict(int)
    total = defaultdict(int)
    overall_correct = overall_top3 = overall_total = 0
    with torch.no_grad():
        for images, labels in loader:
            images, labels = images.to(device), labels.to(device)
            pred = model(images).topk(3, dim=1).indices
            for row, label in enumerate(labels.tolist()):
                name = classes[label]
                hit = int(pred[row, 0].item() == label)
                hit3 = int(label in pred[row].tolist())
                correct[name] += hit
                top3[name] += hit3
                total[name] += 1
                overall_correct += hit
                overall_top3 += hit3
                overall_total += 1

    metrics = {
        "overall": {
            "images": overall_total,
            "top1": overall_correct / max(overall_total, 1),
            "top3": overall_top3 / max(overall_total, 1),
        },
        "classes": {
            name: {
                "images": total[name],
                "top1": correct[name] / max(total[name], 1),
                "top3": top3[name] / max(total[name], 1),
            }
            for name in classes
        },
    }
    (args.model_dir / "per_class_metrics.json").write_text(
        json.dumps(metrics, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(f"overall images={overall_total} top1={metrics['overall']['top1']:.4f} "
          f"top3={metrics['overall']['top3']:.4f}")
    for name in classes:
        row = metrics["classes"][name]
        print(f"{name:<42} n={row['images']:>4} top1={row['top1']:.4f} top3={row['top3']:.4f}")


if __name__ == "__main__":
    main()
