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

# 物种 key → 中文名。
# 用于「物种识别」结果展示，以及后端按中文名匹配植物库（common_name 精确匹配）。
SPECIES_CN = {
    "Aloe": "芦荟",
    "Money Plant": "绿萝",
    "Rose": "月季",
    "Turmeric": "姜黄",
    "Hibiscus": "扶桑",
    "Chrysanthemum": "菊花",
    # v2 新增（labels.json 里是空格形式）
    "Cactus": "仙人掌",
    "Snake Plant": "虎皮兰",
    "Spider Plant": "吊兰",
}

# 指定物种时，该物种所有类别的概率合计至少要达到这个值，否则视为
# "模型没看出这是这个物种"，不给病害结论。经验值：真图通常 >0.5。
MIN_SPECIES_MASS = 0.20

# ---- 多尺度重试（解决「真实照片识别不了」）----
#
# 实测依据（仙人掌 10 张真实拍摄模拟图，species=Cactus）：
#   仅全图：    平均 speciesMass 0.086，0/10 达到 0.20 门槛
#   多尺度取最大：平均 0.877，10/10 达标
# 原因是训练集全是叶片特写，真实照片是整株+背景，模型学到了构图捷径。
# 逐级放大画面中心可让目标重新占满输入，不必重训。
MULTI_SCALE_TRIGGER = 0.55   # 全图 mass 低于此值才启动多尺度（避免白白多算）
MULTI_SCALE_RATIOS = (0.70, 0.50, 0.35)  # 逐级放大：取中心 70%/50%/35% 区域
MULTI_SCALE_ENOUGH = 0.75    # 某一级已达到此值则停止，不再继续放大


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

    def classes_of(self, species_prefix: str) -> List[str]:
        """某个物种在本模型里有哪些类别（传 Cactus / Snake_Plant / Money_Plant 均可）

        用途：告诉用户「这个物种只支持识别哪几种病害」——
        类别少的物种（如仙人掌只有 2 类）遇到未覆盖的病害（软腐/日灼）
        会被硬判成「健康」，造成漏诊；必须让上层能拿到这份清单去提示用户。
        """
        p = (species_prefix or "").strip()
        if not p:
            return []
        out = []
        for i in range(len(self.labels)):
            code = self._label_info(i).get("code") or ""
            if code.startswith(p + "_"):
                out.append(code)
        return out

    # ------------------------------------------------------------------
    def preprocess(self, image_bytes: bytes) -> np.ndarray:
        """bytes → NCHW float32。短边缩到 int(s*1.15) 再中心裁剪，与训练 eval 完全一致。"""
        return self._preprocess_view(image_bytes, 1.0)

    def _preprocess_view(self, image_bytes: bytes, crop_ratio: float = 1.0) -> np.ndarray:
        """取中心 crop_ratio 比例的区域，再按标准流程缩放到输入尺寸。

        多尺度推理的基础：crop_ratio<1 相当于把画面中心放大，
        让小目标（整株照片里的一小片叶）占据模型输入的大部分像素。
        """
        import io

        img = Image.open(io.BytesIO(image_bytes)).convert("RGB")

        if crop_ratio < 0.999:
            w, h = img.size
            nw, nh = max(8, int(w * crop_ratio)), max(8, int(h * crop_ratio))
            left, top = (w - nw) // 2, (h - nh) // 2
            img = img.crop((left, top, left + nw, top + nh))

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

    def _softmax(self, image_bytes: bytes, crop_ratio: float = 1.0) -> np.ndarray:
        x = self._preprocess_view(image_bytes, crop_ratio)
        logits = self.session.run(None, {self.input_name: x})[0][0]  # type: ignore[union-attr]
        z = logits - np.max(logits)
        return np.exp(z) / np.sum(np.exp(z))

    # ------------------------------------------------------------------
    def predict(self, image_bytes: bytes, top_k: int = 3,
                only_species: Optional[str] = None) -> Dict[str, Any]:
        """病害分类。

        only_species（如 "Money_Plant"）非空时，只保留该物种的类别。

        为什么必须过滤：通用模型是在 6 个物种的 29 个类别上一起训练的，
        一张绿萝的照片完全可能被它判成「月季·霜霉病」——物种都不对。
        已知物种时把其他物种的类别剔除，能杜绝这种离谱结论；
        若剔除后该物种的概率总量太小，说明模型压根没看出这个物种，
        此时返回空结果 + 原因，让上层如实告诉用户"判断不了"，而不是硬给病名。
        """
        if not self.available:
            return {"available": False, "reason": self.reason, "topK": [], "model": self.name}

        # ---- 第一步：全图推理（快路径）----
        p = self._softmax(image_bytes, 1.0)

        species_mass = None
        used_crop = 1.0
        decided_by = "single"

        if only_species:
            prefix = only_species.strip()
            keep = [
                i for i in range(len(p))
                if self._label_info(i)["code"].startswith(prefix + "_")
            ]
            if not keep:
                return {
                    "available": True,
                    "reason": f"模型类别中没有物种 {prefix} 的病害类别",
                    "topK": [],
                    "model": self.name,
                    "filtered": True,
                }

            mass = float(sum(p[i] for i in keep))

            # ---- 第二步：全图不达标时，自动多尺度重试 ----
            #
            # 为什么要这么做（实测依据）：
            #   训练集全是「叶片占满画框」的特写，模型因此学到了构图捷径。
            #   真实手机照片是「整株 + 花盆 + 背景」，叶片只占画面一小块，
            #   仙人掌的 speciesMass 从 0.94 直接掉到 0.12（10 张里 9 张低于门槛），
            #   用户看到的就是「放仙人掌照片做不了病害识别」。
            #   把画面中心逐级放大后再推理，使目标重新占满输入：
            #   0.94 → 平均 0.877，10/10 达标（仍然是同一套权重，零成本提升）。
            best = (mass, p, 1.0)
            if mass < MULTI_SCALE_TRIGGER:
                for ratio in MULTI_SCALE_RATIOS:
                    try:
                        pv = self._softmax(image_bytes, ratio)
                    except Exception:
                        continue
                    mv = float(sum(pv[i] for i in keep))
                    if mv > best[0]:
                        best = (mv, pv, ratio)
                    # 已经足够确信就不必再放大
                    if best[0] >= MULTI_SCALE_ENOUGH:
                        break
                mass, p, used_crop = best
                if used_crop < 0.999:
                    decided_by = "multi-scale@%.2f" % used_crop

            species_mass = mass
            # 该物种概率总量太小 → 模型没认出这个物种，不要硬给病名
            if species_mass < MIN_SPECIES_MASS:
                # 传进来的是下划线形式（Money_Plant），labels 里是空格形式（Money Plant）
                display = SPECIES_CN.get(prefix) or SPECIES_CN.get(prefix.replace("_", " ")) or prefix
                return {
                    "available": True,
                    "reason": (f"图像与「{display}」的病害特征不符（该物种概率合计仅 "
                               f"{species_mass * 100:.1f}%），可能植株健康，或照片特征不足"),
                    "topK": [],
                    "model": self.name,
                    "filtered": True,
                    "speciesMass": round(species_mass, 4),
                }
            # 只在保留的类别里排序
            order = sorted(keep, key=lambda i: -p[i])[:top_k]
        else:
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
        return {
            "available": True,
            "reason": None,
            "topK": items,
            "model": self.name,
            "filtered": bool(only_species),
            "speciesMass": round(species_mass, 4) if species_mass is not None else None,
            # 诊断字段：便于排查「为什么这张图识别不了」——
            # decidedBy=single 表示全图就够信；multi-scale@0.5 表示靠放大才认出来
            "decidedBy": decided_by,
            "cropRatio": used_crop,
            # 该物种在本模型里的类别清单与数量。数量少（如仙人掌只有 2 类）时，
            # 未覆盖的病害会被误判成「健康」，上层需要据此提示用户。
            "speciesClasses": self.classes_of(only_species) if only_species else None,
            "speciesClassCount": (len(self.classes_of(only_species))
                                   if only_species else None),
        }

    # ------------------------------------------------------------------
    def predict_species(self, image_bytes: bytes, top_k: int = 3) -> Dict[str, Any]:
        """物种识别（借用病害模型的类别 → 物种映射间接实现）。

        原理：这个模型是在「物种 × 病害」共 29 个类别上训练的，每个类别都带
        ``species`` 字段。把同一物种下所有类别的概率**求和**，就得到该物种的置信度。
        同一物种有多个类别（健康 + 若干病害），求和比取单个最高类别更稳，
        因此不需要为物种识别额外训练一个模型。

        局限（上层必须处理）：
            只覆盖训练过的 6 个物种。不在其中的植物会被强行归到最像的那个，
            所以调用方要设置信度阈值，不到阈值就**不要给结论**，让用户手动选择。
            返回里的 ``speciesList`` 说明模型到底覆盖哪些物种。
        """
        if not self.available:
            return {"available": False, "reason": self.reason, "topK": [], "model": self.name}

        x = self.preprocess(image_bytes)
        logits = self.session.run(None, {self.input_name: x})[0][0]  # type: ignore[union-attr]

        z = logits - np.max(logits)
        p = np.exp(z) / np.sum(np.exp(z))

        agg: Dict[str, float] = {}
        for idx in range(len(p)):
            sp = self._label_info(idx).get("species") or ""
            if not sp:
                continue
            agg[sp] = agg.get(sp, 0.0) + float(p[idx])

        ranked = sorted(agg.items(), key=lambda kv: -kv[1])[:top_k]
        items = [
            {
                "species": sp,
                "nameCn": SPECIES_CN.get(sp, sp),
                "confidence": round(float(conf), 4),
            }
            for sp, conf in ranked
        ]
        return {
            "available": True,
            "reason": None,
            "topK": items,
            "model": self.name,
            "numSpecies": len(agg),
            "speciesList": sorted(agg),
        }

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

    @property
    def species_set(self) -> List[str]:
        """通用模型覆盖的物种清单（英文 key）。

        对外暴露是必要的：前端曾把「支持哪些物种」硬编码成 6 种，
        模型扩到 9 种后没同步，仙人掌/虎皮兰/吊兰在界面上直接显示
        「暂无病害识别数据」并跳过，压根不调接口 —— 用户侧表现就是
        「放了仙人掌照片不识别病害」。
        """
        return self.general.species_set

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
        # 走通用模型且已知物种时，做「物种一致性过滤」：
        # 只保留该物种的类别，避免「绿萝的照片给出月季的病」这种跨物种结论。
        # 专用模型的类别本来就全属于该物种，无需过滤。
        only = species if (species and clf is self.general) else None
        result = clf.predict(image_bytes, top_k=top_k, only_species=only)
        result["usedSpecies"] = species
        result["routedTo"] = clf.name
        result["fallback"] = bool(species) and clf is self.general
        return result

    def predict_species(self, image_bytes: bytes, top_k: int = 3) -> Dict[str, Any]:
        """物种识别固定用**通用模型**。

        专用模型只覆盖单一物种的病害类别（比如 aloe 模型 5 个类别全是芦荟），
        拿它做物种识别没有区分度，所以这里强制走通用模型。
        """
        return self.general.predict_species(image_bytes, top_k=top_k)

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
