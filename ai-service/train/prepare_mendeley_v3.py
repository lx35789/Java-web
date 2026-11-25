"""
Prepare the Mendeley plant-disease ZIP parts for GreenThumb model v3.

The source archives contain species/condition directories. Flower-only
directories are intentionally ignored because they do not describe a disease
or a healthy leaf state.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import zipfile
from collections import Counter, defaultdict
from pathlib import Path, PurePosixPath

from PIL import Image, ImageOps

IMG_EXT = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}

# Exact source directory -> model class code.
CLASS_MAP = {
    ("Rosa", "Healthy Leaf"): "Rose_Healthy",
    ("Rosa", "Nutrient Deficiency"): "Rose_Nutrient_Deficiency",
    ("Rosa", "Fungal Leaf Spot Disease"): "Rose_Fungal_Leaf_Spot",
    ("Rosa", "insect damage"): "Rose_Insect_Damage",
    ("Hibiscus rosa-sinensis", "Healthy Leaf"): "Hibiscus_Healthy",
    ("Hibiscus rosa-sinensis", "Bacterial Leaf or Spot"): "Hibiscus_Bacterial_Leaf_Spot",
    ("Hibiscus rosa-sinensis", "Nutrient Deficiency"): "Hibiscus_Nutrient_Deficiency",
    ("Tagetes erecta", "Heaalthy Leaf"): "Tagetes_Healthy",
    ("Tagetes erecta", "Alternaria Leaf Blight"): "Tagetes_Alternaria_Leaf_Blight",
    ("Tagetes erecta", "Pest Damage"): "Tagetes_Pest_Damage",
}

# These directories contain flowers or unlabelled plant parts, not a disease.
SKIP_MAP = {
    ("Rosa", "Rosa"): "flower image, no disease label",
    ("Hibiscus rosa-sinensis", "Hibiscus rosa-sinensis"): "flower image, no disease label",
    ("Tagetes erecta", "Tagetes"): "flower image, no disease label",
}


def group_stem(filename: str) -> str:
    """Remove dataset variants that depict the same source photo."""
    stem = Path(filename).stem
    stem = re.sub(r"^no_bg_", "", stem, flags=re.IGNORECASE)
    # Capture timestamps end in six digits (for example _094023), so only
    # collapse the explicit one-digit duplicate suffix used by _1 variants.
    stem = re.sub(r"_[1-9]$", "", stem)
    return stem or Path(filename).stem


def group_id(species: str, condition: str, filename: str) -> str:
    key = f"{species}|{condition}|{group_stem(filename)}".encode("utf-8")
    return "m" + hashlib.sha1(key).hexdigest()[:12]


def safe_name(filename: str) -> str:
    stem = Path(filename).stem
    return re.sub(r"[^0-9A-Za-z._-]+", "_", stem).strip("_") or "image"


def iter_sources(zips: list[Path]):
    for zip_path in zips:
        with zipfile.ZipFile(zip_path) as archive:
            for info in archive.infolist():
                if info.is_dir():
                    continue
                path = PurePosixPath(info.filename)
                if len(path.parts) < 4 or path.parts[0] != "dataset":
                    continue
                if path.suffix.lower() not in IMG_EXT:
                    continue
                yield zip_path, archive, info, path


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--zip", action="append", required=True, type=Path,
                    help="Mendeley ZIP part; repeat for all parts")
    ap.add_argument("--out", required=True, type=Path)
    ap.add_argument("--max-side", type=int, default=448)
    ap.add_argument("--quality", type=int, default=92)
    args = ap.parse_args()

    missing = [str(p) for p in args.zip if not p.is_file()]
    if missing:
        sys.exit("ZIP file not found: " + ", ".join(missing))
    if args.out.exists() and any(args.out.iterdir()):
        sys.exit(f"Output directory is not empty: {args.out}")

    Image.MAX_IMAGE_PIXELS = None
    counts: Counter[str] = Counter()
    groups: dict[str, set[str]] = defaultdict(set)
    skipped: Counter[str] = Counter()
    unknown: Counter[tuple[str, str]] = Counter()
    seen_images: set[str] = set()
    duplicates = 0

    for zip_path, archive, info, path in iter_sources(args.zip):
        species, condition = path.parts[1], path.parts[2]
        source_key = (species, condition)
        code = CLASS_MAP.get(source_key)
        if code is None:
            if source_key in SKIP_MAP:
                skipped[SKIP_MAP[source_key]] += 1
            else:
                unknown[source_key] += 1
            continue

        data = archive.read(info)
        digest = hashlib.sha1(data).hexdigest()
        if digest in seen_images:
            duplicates += 1
            continue
        seen_images.add(digest)

        try:
            from io import BytesIO

            with Image.open(BytesIO(data)) as source:
                image = ImageOps.exif_transpose(source).convert("RGB")
                if max(image.size) > args.max_side:
                    image.thumbnail((args.max_side, args.max_side), Image.Resampling.LANCZOS)
                class_dir = args.out / code
                class_dir.mkdir(parents=True, exist_ok=True)
                gid = group_id(species, condition, path.name)
                variant = hashlib.sha1(info.filename.encode("utf-8")).hexdigest()[:8]
                target = class_dir / f"{gid}__{safe_name(path.name)}__{variant}.jpg"
                image.save(target, "JPEG", quality=args.quality, optimize=True)
        except Exception as exc:
            print(f"[skip unreadable] {zip_path.name}:{info.filename}: {exc}", flush=True)
            continue

        counts[code] += 1
        groups[code].add(gid)

    if unknown:
        print("[warning] Unmapped image directories were ignored:", flush=True)
        for (species, condition), count in sorted(unknown.items()):
            print(f"  {species}/{condition}: {count}", flush=True)

    report = {
        "source": "Mendeley Data DOI 10.17632/2vzp6ss7vg.1, CC BY 4.0",
        "max_side": args.max_side,
        "classes": {
            code: {"images": counts[code], "groups": len(groups[code])}
            for code in sorted(counts)
        },
        "total_images": sum(counts.values()),
        "total_groups": sum(len(v) for v in groups.values()),
        "duplicate_images_removed": duplicates,
        "skipped_no_disease_label": dict(sorted(skipped.items())),
        "unmapped_image_directories": {
            f"{species}/{condition}": count
            for (species, condition), count in sorted(unknown.items())
        },
    }
    args.out.mkdir(parents=True, exist_ok=True)
    (args.out / "dataset_report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    print(json.dumps(report, ensure_ascii=False, indent=2), flush=True)
    print(f"[done] Wrote {report['total_images']} images to {args.out}", flush=True)


if __name__ == "__main__":
    main()
