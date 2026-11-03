"""
病害分类推理封装（ONNX Runtime，CPU）—— 支持「通用模型 + 物种专用模型」按物种路由。

为什么需要专用模型
------------------
通用 29 类模型在芦荟上只有 8%（在芦荟专用预处理图上），
而芦荟专用的 5 类模型在同一批图上能到 68%（Top-3 96%）。
所以对已经训过专用模型的物种，优先走专用模型。

目录结构
--------
    models/
        disease.onnx        通用模型（29 类，覆盖 6 个物种）
        labels.json
        meta.json
        specialized/
            aloe/           物种专用模型，目录名 = 代码里的 species key（小写）
                disease.onnx
                labels.json
                meta.json

路由规则
--------
    predict(bytes, species="Aloe")  → 有 aloe 专用模型就用它
    predict(bytes)                  → 用通用模型
    返回结果里带 "model" 字段，说明实际用了哪个，便于排查。

设计要点
--------
1. **模型缺失不报错**：没训练模型时 available=False，接口返回 degraded，
   而不是让 AI 服务起不来。
2. **标签来自 labels.json**：训练脚本一起导出，避免类别顺序对不上。
3. **预处理与训练严格一致**：训练 eval 用的是 Resize(int(s*1.15)) → CenterCrop(s)，
   这里必须一模一样，否则准确率会莫名掉很多。
"""
from __future__ import annotations

import json
import os
from pathlib import Path
from typing import Any, Dict, List, Optional

import numpy as np
from PIL import Image

try:
    import onnxruntime as ort
except Exception:  # pragma: no cover
    ort = None  # type: ignore

MODEL_DIR = Path(os.environ.get("GT_MODEL_DIR", Path(__file__).resolve().parent.parent / "models"))
SPECIALIZED_DIR = MODEL_DIR / "specialized"

# 与 torchvision 的 ImageNet 归一化保持一致
IMAGENET_MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32)
IMAGENET_STD = np.array([0.229, 0.224, 0.225], dtype=np.float32)


class DiseaseClassifier:
    """单个 ONNX 模型的封装。线程安全（onnxruntime 的 InferenceSession 本身线程安全）。"""

    def __init__(self, name: str = "general", model_dir: Optional[Path] = None) -> None:
        self.name = name
        self.dir = Path(model_dir) if model_dir else MODEL_DIR
        self.session = None
        self.labels: Dict[str, Any] = {}
        self.meta: Dict[str, Any] = {}
        self.input_size = 160
        self.input_name = ""
        self.reason: Optional[str] = None
        self._load()

    # ------------------------------------------------------------------
    def _load(self) -> None:
        if ort is None:
            self.reason = "onnxruntime 未安装"
            return
        model_path = self.dir / "disease.onnx"
        labels_path = self.dir / "labels.json"
        meta_path = self.dir / "meta.json"

        if not model_path.exists():
            self.reason = f"模型文件不存在：{model_path}"
            return
        try:
            so = ort.SessionOptions()
            so.intra_op_num_threads = max(1, (os.cpu_count() or 2) // 2)
            self.session = ort.InferenceSession(str(model_path), so, providers=["CPUExecutionProvider"])
            self.input_name = self.session.get_inputs()[0].name

            if labels_path.exists():
                self.labels = json.loads(labels_path.read_text(encoding="utf-8"))
            else:
                self.reason = "labels.json 缺失，无法解释输出"
                self.session = None
                return

            if meta_path.exists():
                self.meta = json.loads(meta_path.read_text(encoding="utf-8"))
            self.input_size = int(self.meta.get("input_size", 160))
        except Exception as exc:  # pragma: no cover
            self.reason = f"加载模型失败：{exc}"
            self.session = None

    # ------------------------------------------------------------------
    @property
    def available(self) -> bool:
        return self.session is not None

    @property
    def num_classes(self) -> int:
        return len(self.labels)

    @property
    def species_set(self) -> List[str]:
        """这个模型覆盖哪些物种（从标签里推出来）"""
        out = set()
        for i in range(len(self.labels)):
            s = self._label_info(i).get("species")
            if s:
                out.add(s)
        return sorted(out)

    # ------------------------------------------------------------------
    def preprocess(self, image_bytes: bytes) -> np.ndarray:
        """bytes → NCHW float32。短边缩到 int(s*1.15) 再中心裁剪，与训练 eval 完全一致。"""
        import io

        img = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        short = int(self.input_size * 1.15)

        w, h = img.size
        scale = short / min(w, h)
        img = img.resize((max(1, round(w * scale)), max(1, round(h * scale))), Image.BILINEAR)

        w, h = img.size
        left = (w - self.input_size) // 2
        top = (h - self.input_size) // 2
        img = img.crop((left, top, left + self.input_size, top + self.input_size))

        arr = np.asarray(img, dtype=np.float32) / 255.0
        arr = (arr - IMAGENET_MEAN) / IMAGENET_STD
        arr = np.transpose(arr, (2, 0, 1))  # HWC → CHW
        return np.expand_dims(arr, 0)  # → NCHW

    # ------------------------------------------------------------------
    def predict(self, image_bytes: bytes, top_k: int = 3) -> Dict[str, Any]:
        if not self.available:
            return {"available": False, "reason": self.reason, "topK": [], "model": self.name}

        x = self.preprocess(image_bytes)
        logits = self.session.run(None, {self.input_name: x})[0][0]  # type: ignore[union-attr]

        z = logits - np.max(logits)
        p = np.exp(z) / np.sum(np.exp(z))

        order = np.argsort(-p)[:top_k]
        items: List[Dict[str, Any]] = []
        for idx in order:
            meta = self._label_info(int(idx))
            items.append(
                {
                    "code": meta["code"],
                    "nameCn": meta["nameCn"],
                    "species": meta["species"],
                    "disease": meta["disease"],
                    "confidence": round(float(p[idx]), 4),
                }
            )
        return {"available": True, "reason": None, "topK": items, "model": self.name}

    # ------------------------------------------------------------------
    def _label_info(self, idx: int) -> Dict[str, str]:
        """
        标签解析。同时支持两种 labels.json 写法：

        新格式（train_indoor.py 导出，推荐）：
            {"0": {"code": "Aloe_Anthracnose", "cn": "芦荟·炭疽病",
                   "species": "Aloe", "disease": "Anthracnose"}, ...}

        旧格式（早期 train_disease.py 导出）：
            {"0": "Aloe_Vera___Anthracnose", ...}
        """
        raw = self.labels.get(str(idx))
        if isinstance(raw, dict):
            code = raw.get("code") or f"class_{idx}"
            return {
                "code": code,
                "nameCn": raw.get("cn") or raw.get("nameCn") or code,
                "species": raw.get("species") or "",
                "disease": raw.get("disease") or "",
            }
        label = raw if isinstance(raw, str) else f"class_{idx}"
        species, _, disease = label.partition("___")
        return {
            "code": label,
            "nameCn": label,
            "species": species,
            "disease": disease or species,
        }


class ClassifierRegistry:
    """通用模型 + 若干物种专用模型，按 species 路由。"""

    def __init__(self) -> None:
        self.general = DiseaseClassifier("general", MODEL_DIR)
        self.specialized: Dict[str, DiseaseClassifier] = {}
        self._scan()

    def _scan(self) -> None:
        if not SPECIALIZED_DIR.is_dir():
            return
        for d in sorted(SPECIALIZED_DIR.iterdir()):
            if not d.is_dir():
                continue
            c = DiseaseClassifier(d.name, d)
            if c.available:
                self.specialized[d.name.lower()] = c

    # ---- 兼容旧接口：默认暴露通用模型的状态 ----
    @property
    def available(self) -> bool:
        return self.general.available

    @property
    def reason(self) -> Optional[str]:
        return self.general.reason

    @property
    def num_classes(self) -> int:
        return self.general.num_classes

    @property
    def meta(self) -> Dict[str, Any]:
        return self.general.meta

    @property
    def labels(self) -> Dict[str, Any]:
        return self.general.labels

    # ------------------------------------------------------------------
    def pick(self, species: Optional[str]) -> DiseaseClassifier:
        """按物种选模型。没有专用模型就退回通用模型。"""
        if species:
            key = str(species).strip().lower()
            if key in self.specialized:
                return self.specialized[key]
        return self.general

    def predict(self, image_bytes: bytes, top_k: int = 3, species: Optional[str] = None) -> Dict[str, Any]:
        clf = self.pick(species)
        result = clf.predict(image_bytes, top_k=top_k)
        result["usedSpecies"] = species
        result["routedTo"] = clf.name
        result["fallback"] = bool(species) and clf is self.general
        return result

    def describe(self) -> Dict[str, Any]:
        """/health 用：列出所有已加载的模型。"""
        spec = {}
        for k, c in self.specialized.items():
            spec[k] = {
                "available": c.available,
                "numClasses": c.num_classes,
                "species": c.species_set,
            }
        return {"specialized": spec}


# 模块级单例
classifier = ClassifierRegistry()
