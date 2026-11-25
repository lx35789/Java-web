# -*- coding: utf-8 -*-
"""Build a concise project report deck for GreenThumb AI."""

from pathlib import Path

from PIL import Image, ImageDraw
from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.text import MSO_ANCHOR, PP_ALIGN
from pptx.oxml.ns import qn
from pptx.util import Inches, Pt


ROOT = Path(__file__).resolve().parents[1]
ASSET_DIR = ROOT / "report" / "assets"
OUT = ROOT / "植物养护与病害识别项目汇报_最终版.pptx"

SHOT_DIR = Path(r"D:\indoor-plant-data\shots")
SHOT_W9 = Path(r"D:\indoor-plant-data\shots_w9")

FONT = "Microsoft YaHei"
W, H = 13.333, 7.5

BG = RGBColor(0xF7, 0xF5, 0xEF)
INK = RGBColor(0x18, 0x24, 0x1E)
INK2 = RGBColor(0x3E, 0x4B, 0x44)
MUTED = RGBColor(0x7D, 0x88, 0x81)
LINE = RGBColor(0xDF, 0xE2, 0xDB)
WHITE = RGBColor(0xFF, 0xFF, 0xFF)
GREEN = RGBColor(0x1D, 0x6B, 0x4B)
GREEN2 = RGBColor(0x2E, 0x87, 0x63)
GREEN_LIGHT = RGBColor(0xE6, 0xF0, 0xEA)
GREEN_PALE = RGBColor(0xF1, 0xF7, 0xF4)
ORANGE = RGBColor(0xC5, 0x7B, 0x2D)
ORANGE_LIGHT = RGBColor(0xFA, 0xEF, 0xE2)
RED = RGBColor(0xBD, 0x45, 0x3E)
RED_LIGHT = RGBColor(0xF9, 0xEA, 0xE8)
DARK = RGBColor(0x14, 0x2A, 0x21)


def set_run_font(run, size=14, bold=False, color=INK):
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.color.rgb = color
    run.font.name = FONT
    rpr = run._r.get_or_add_rPr()
    latin = rpr.find(qn("a:latin"))
    ea = rpr.makeelement(qn("a:ea"), {"typeface": FONT})
    if latin is not None:
        latin.addnext(ea)
    else:
        rpr.append(ea)


def add_text(
    slide,
    text,
    x,
    y,
    w,
    h,
    size=14,
    color=INK,
    bold=False,
    align=PP_ALIGN.LEFT,
    anchor=MSO_ANCHOR.TOP,
    line=1.2,
    margin=0,
):
    box = slide.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    tf = box.text_frame
    tf.clear()
    tf.word_wrap = True
    tf.vertical_anchor = anchor
    tf.margin_left = Inches(margin)
    tf.margin_right = Inches(margin)
    tf.margin_top = Inches(margin)
    tf.margin_bottom = Inches(margin)
    p = tf.paragraphs[0]
    p.alignment = align
    p.line_spacing = line
    r = p.add_run()
    r.text = text
    set_run_font(r, size, bold, color)
    return tf


def add_rich_text(parts, slide, x, y, w, h, size=14, line=1.2, margin=0):
    box = slide.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    tf = box.text_frame
    tf.clear()
    tf.word_wrap = True
    tf.margin_left = Inches(margin)
    tf.margin_right = Inches(margin)
    tf.margin_top = Inches(margin)
    tf.margin_bottom = Inches(margin)
    p = tf.paragraphs[0]
    p.line_spacing = line
    for text, bold, color in parts:
        r = p.add_run()
        r.text = text
        set_run_font(r, size, bold, color)
    return tf


def add_multiline(
    slide,
    lines,
    x,
    y,
    w,
    h,
    size=12,
    color=INK2,
    line=1.35,
    bullet=False,
    space_after=3,
):
    box = slide.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    tf = box.text_frame
    tf.clear()
    tf.word_wrap = True
    tf.margin_left = 0
    tf.margin_right = 0
    tf.margin_top = 0
    tf.margin_bottom = 0
    for i, item in enumerate(lines):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.line_spacing = line
        p.space_after = Pt(space_after)
        r = p.add_run()
        r.text = ("• " if bullet else "") + item
        set_run_font(r, size, False, color)
    return tf


def add_box(
    slide,
    x,
    y,
    w,
    h,
    fill=WHITE,
    border=LINE,
    radius=True,
    border_width=0.8,
):
    shape_type = MSO_SHAPE.ROUNDED_RECTANGLE if radius else MSO_SHAPE.RECTANGLE
    shape = slide.shapes.add_shape(
        shape_type, Inches(x), Inches(y), Inches(w), Inches(h)
    )
    if fill is None:
        shape.fill.background()
    else:
        shape.fill.solid()
        shape.fill.fore_color.rgb = fill
    if border is None:
        shape.line.fill.background()
    else:
        shape.line.color.rgb = border
        shape.line.width = Pt(border_width)
    shape.shadow.inherit = False
    return shape


def add_circle_number(slide, number, x, y, diameter=0.42, fill=GREEN, color=WHITE):
    shape = slide.shapes.add_shape(
        MSO_SHAPE.OVAL, Inches(x), Inches(y), Inches(diameter), Inches(diameter)
    )
    shape.fill.solid()
    shape.fill.fore_color.rgb = fill
    shape.line.fill.background()
    tf = shape.text_frame
    tf.clear()
    tf.margin_left = 0
    tf.margin_right = 0
    tf.margin_top = 0
    tf.margin_bottom = 0
    tf.vertical_anchor = MSO_ANCHOR.MIDDLE
    p = tf.paragraphs[0]
    p.alignment = PP_ALIGN.CENTER
    r = p.add_run()
    r.text = str(number)
    set_run_font(r, 11, True, color)
    return shape


def add_header(slide, kicker, title, subtitle=None, number=None):
    add_text(slide, kicker, 0.72, 0.45, 7.0, 0.24, 10.5, GREEN, True)
    add_text(slide, title, 0.72, 0.78, 11.8, 0.58, 27, INK, True, line=1.05)
    if subtitle:
        add_text(slide, subtitle, 0.72, 1.38, 11.8, 0.34, 12.2, MUTED)
    if number:
        add_text(
            slide,
            f"{number:02d} / 07",
            11.9,
            7.08,
            0.72,
            0.18,
            9,
            MUTED,
            align=PP_ALIGN.RIGHT,
        )


def add_picture_contain(slide, path, x, y, w, h):
    with Image.open(path) as img:
        iw, ih = img.size
    scale = min(w / iw, h / ih)
    dw, dh = iw * scale, ih * scale
    return slide.shapes.add_picture(
        str(path),
        Inches(x + (w - dw) / 2),
        Inches(y + (h - dh) / 2),
        Inches(dw),
        Inches(dh),
    )


def add_image_card(slide, path, x, y, w, h, label=None):
    add_box(slide, x, y, w, h, WHITE, LINE, radius=True)
    add_picture_contain(slide, path, x + 0.1, y + 0.1, w - 0.2, h - 0.2)
    if label:
        add_text(slide, label, x + 0.18, y + 0.18, w - 0.36, 0.24, 9.5, MUTED, True)


def make_assets():
    ASSET_DIR.mkdir(parents=True, exist_ok=True)

    # The current app screenshots are wide crops that preserve the whole work area.
    species = Image.open(SHOT_DIR / "A_第1步_物种识别_crop.png").convert("RGB")
    species.save(ASSET_DIR / "species_recognition.png", quality=95)

    disease = Image.open(SHOT_DIR / "B_第2步_病害识别_crop.png").convert("RGB")
    disease.save(ASSET_DIR / "disease_recognition_leaf.png", quality=95)

    cactus = Image.open(SHOT_W9 / "02_病害识别结果.png").convert("RGB")
    cactus_strip = cactus.crop((310, 410, 1225, 615))
    cactus_strip.save(ASSET_DIR / "disease_recognition_cactus.png", quality=95)

    species_shot_map = {
        "rose": SHOT_DIR / "sp_Rose_Black_Spot.png",
        "hibiscus": SHOT_DIR / "sp_Hibiscus_Scorch.png",
        "turmeric": SHOT_DIR / "sp_Turmeric_Leaf_Spot.png",
        "chrysanthemum": SHOT_DIR / "sp_Chrysanthemum_Septoria_Leaf_Spot.png",
    }
    for key, path in species_shot_map.items():
        image = Image.open(path).convert("RGB")
        result_panel = image.crop((60, 720, 2220, 1390))
        result_panel.save(ASSET_DIR / f"new_species_{key}.png", quality=95)

    # Crop the in-app cactus photo for the cover. It is evidence from a real page,
    # not a decorative stock image.
    cover_photo = cactus.crop((1240, 492, 1718, 690))
    cover = Image.new("RGB", (478, 208), (245, 247, 242))
    cover.paste(cover_photo, (0, 0))
    cover.save(ASSET_DIR / "cover_leaf_photo.jpg", quality=95)

    # A tiny visual legend for the three recognition stages.
    legend = Image.new("RGB", (1200, 250), "white")
    draw = ImageDraw.Draw(legend)
    for i in range(3):
        cx = 200 + i * 400
        draw.ellipse((cx - 44, 55, cx + 44, 143), fill=(29, 107, 75))
        draw.text((cx - 9, 82), str(i + 1), fill="white")
    legend.save(ASSET_DIR / "stage_legend.png")


def slide_cover(prs):
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    bg = slide.background.fill
    bg.solid()
    bg.fore_color.rgb = BG

    add_box(slide, 8.82, 0, 4.513, 7.5, DARK, None, radius=False)
    add_text(slide, "PROJECT REVIEW · 2026.11", 0.78, 0.55, 6.8, 0.26, 10.5, GREEN, True)
    add_text(slide, "植语助手", 0.78, 1.25, 7.3, 0.74, 40, INK, True, line=1.0)
    add_text(slide, "GreenThumb AI", 0.78, 1.93, 7.4, 0.58, 29, GREEN, True, line=1.0)
    add_text(
        slide,
        "室内绿植养护与病害识别项目",
        0.8,
        2.78,
        6.9,
        0.4,
        16.5,
        INK2,
        False,
    )
    add_text(
        slide,
        "一张叶片照片，串起“识别 → 诊断 → 养护”。",
        0.8,
        3.48,
        7.2,
        0.72,
        23,
        INK,
        True,
        line=1.22,
    )
    add_text(
        slide,
        "从物种确认、病害分类到症状判因，结果都配置信度、依据和下一步建议。",
        0.8,
        4.33,
        7.15,
        0.72,
        12.4,
        MUTED,
        False,
        line=1.45,
    )

    metrics = [
        ("10", "覆盖物种"),
        ("44", "病害类别"),
        ("91.00%", "测试集 Top-1"),
        ("3 步", "诊断链路"),
    ]
    for i, (value, label) in enumerate(metrics):
        x = 0.8 + i * 1.82
        add_text(slide, value, x, 5.62, 1.55, 0.38, 21, GREEN if i < 3 else ORANGE, True)
        add_text(slide, label, x, 6.1, 1.55, 0.25, 9.8, MUTED)

    add_text(slide, "项目汇报", 0.8, 6.91, 2.0, 0.22, 9.5, MUTED)

    add_text(slide, "已上线的识别界面", 9.28, 0.72, 3.45, 0.28, 10.5, RGBColor(0xB9, 0xD6, 0xC7), True)
    add_picture_contain(
        slide,
        ASSET_DIR / "disease_recognition_leaf.png",
        9.2,
        1.1,
        3.78,
        3.0,
    )
    add_box(slide, 9.28, 4.38, 3.55, 1.35, RGBColor(0x21, 0x3A, 0x2F), RGBColor(0x3B, 0x5B, 0x4B))
    add_text(slide, "实际输出", 9.52, 4.57, 0.9, 0.22, 9.5, RGBColor(0xA8, 0xC8, 0xB7), True)
    add_text(slide, "绿萝·黄化病", 9.52, 4.83, 2.15, 0.32, 17, WHITE, True)
    add_text(slide, "94.1%", 11.62, 4.78, 0.9, 0.34, 18, RGBColor(0xE3, 0xB2, 0x69), True, PP_ALIGN.RIGHT)
    add_text(
        slide,
        "同时给出其他可能、典型症状、处理与预防建议",
        9.52,
        5.25,
        2.95,
        0.25,
        9.4,
        RGBColor(0xCE, 0xDA, 0xD3),
    )

    add_box(slide, 9.2, 6.05, 3.78, 0.02, RGBColor(0x3B, 0x5B, 0x4B), None, radius=False)
    add_text(
        slide,
        "Vue 3  ·  Spring Boot  ·  FastAPI / ONNX  ·  MySQL",
        9.22,
        6.28,
        3.7,
        0.48,
        8.8,
        RGBColor(0xA8, 0xC8, 0xB7),
        False,
        line=1.4,
    )


def slide_what_we_did(prs):
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    slide.background.fill.solid()
    slide.background.fill.fore_color.rgb = BG
    add_header(
        slide,
        "WHAT WE BUILT",
        "我们做了什么",
        "把植物养护从“凭经验判断”变成一条可解释、可复核的诊断链路",
        number=2,
    )

    stages = [
        ("拍一张", "手机拍照 / 相册上传", "压到 1600px，减少移动端上传等待"),
        ("认物种", "Top-3 候选", "自有模型优先，百度 / Pl@ntNet 兜底"),
        ("判问题", "病害 + 症状双通道", "病理问题与养护问题分别判断"),
        ("给建议", "处理 / 预防 / 养护", "结果落回植物档案与养护任务"),
    ]
    y = 2.04
    for i, (title, subtitle, detail) in enumerate(stages):
        x = 0.78 + i * 3.05
        add_box(slide, x, y, 2.8, 1.38, WHITE, LINE)
        add_circle_number(slide, i + 1, x + 0.18, y + 0.18, 0.34)
        add_text(slide, title, x + 0.64, y + 0.16, 1.9, 0.32, 15.5, INK, True)
        add_text(slide, subtitle, x + 0.2, y + 0.64, 2.35, 0.28, 10.3, GREEN, True)
        add_text(slide, detail, x + 0.2, y + 0.96, 2.38, 0.26, 8.7, MUTED)
        if i < 3:
            add_text(slide, "→", x + 2.83, y + 0.48, 0.2, 0.28, 17, GREEN, True, PP_ALIGN.CENTER)

    add_text(slide, "四项核心交付", 0.78, 3.78, 3.0, 0.34, 13.5, INK, True)
    deliverables = [
        (
            "01",
            "物种识别",
            "自研模型 + 第三方服务降级；Top-3 由用户确认，避免模型替用户拍板。",
            GREEN_PALE,
            GREEN,
        ),
        (
            "02",
            "病害识别",
            "MobileNetV3 + ONNX，通用 44 类 / 10 物种；芦荟、月季走专用模型。",
            RED_LIGHT,
            RED,
        ),
        (
            "03",
            "症状判因",
            "规则库结合叶片位置、浇水频率、盆材质与环境；低置信度先追问。",
            ORANGE_LIGHT,
            ORANGE,
        ),
        (
            "04",
            "养护闭环",
            "按植物、季节、光照和环境系数生成计划，并落到日历、任务与日志。",
            GREEN_PALE,
            GREEN,
        ),
    ]
    positions = [(0.78, 4.22), (6.62, 4.22), (0.78, 5.45), (6.62, 5.45)]
    for (code, title, body, fill, accent), (x, y) in zip(deliverables, positions):
        add_box(slide, x, y, 5.56, 1.03, fill, None)
        add_text(slide, code, x + 0.2, y + 0.18, 0.5, 0.25, 10, accent, True)
        add_text(slide, title, x + 0.72, y + 0.15, 1.5, 0.3, 13, INK, True)
        add_text(slide, body, x + 0.72, y + 0.47, 4.55, 0.44, 9.4, INK2, False, line=1.25)

    add_text(
        slide,
        "从“做一个分类模型”扩展为“识别、解释、养护”三件事都有人接住。",
        0.78,
        6.76,
        11.8,
        0.3,
        10.5,
        MUTED,
    )


def slide_pipeline(prs):
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    slide.background.fill.solid()
    slide.background.fill.fore_color.rgb = BG
    add_header(
        slide,
        "RECOGNITION PIPELINE",
        "识别链路：先确认物种，再判病害",
        "把最容易出错的“跨物种识别”在中间环节过滤掉",
        number=3,
    )

    add_text(slide, "用户看到的是一条三步向导，背后是一条分层判断链。", 0.78, 1.92, 8.4, 0.3, 12, INK2, True)

    steps = [
        ("拍照", "选图或应用内相机"),
        ("物种 Top-3", "识别 + 用户确认"),
        ("物种过滤", "只保留该物种类别"),
        ("病害 Top-K", "专用模型优先"),
        ("知识库", "症状 / 处理 / 预防"),
    ]
    line_y = 2.73
    add_box(slide, 1.12, line_y, 10.95, 0.035, GREEN_LIGHT, None, radius=False)
    for i, (title, detail) in enumerate(steps):
        x = 0.76 + i * 2.48
        add_circle_number(slide, i + 1, x + 0.74, line_y - 0.19, 0.42, GREEN, WHITE)
        add_text(slide, title, x, 3.02, 1.9, 0.32, 13.2, INK, True, PP_ALIGN.CENTER)
        add_text(slide, detail, x - 0.03, 3.39, 1.96, 0.48, 9.2, MUTED, False, PP_ALIGN.CENTER, line=1.25)

    add_box(slide, 0.78, 4.18, 3.82, 2.33, WHITE, LINE)
    add_text(slide, "关键设计 01", 1.02, 4.4, 1.4, 0.25, 9.5, GREEN, True)
    add_text(slide, "物种一致性过滤", 1.02, 4.7, 3.0, 0.34, 16, INK, True)
    add_text(
        slide,
        "绿萝照片只保留绿萝类别概率，避免模型把“月季病害”顶到第一。",
        1.02,
        5.18,
        3.2,
        0.78,
        10,
        INK2,
        False,
        line=1.42,
    )
    add_text(slide, "跨物种误判 → 先被拦住", 1.02, 6.02, 2.9, 0.26, 9.5, RED, True)

    add_box(slide, 4.76, 4.18, 3.82, 2.33, WHITE, LINE)
    add_text(slide, "关键设计 02", 5.0, 4.4, 1.4, 0.25, 9.5, ORANGE, True)
    add_text(slide, "低置信度不硬判", 5.0, 4.7, 3.0, 0.34, 16, INK, True)
    add_text(
        slide,
        "Top-1 低于 50% 不直接给结论；模型不可用时说明原因，并允许继续走症状诊断。",
        5.0,
        5.18,
        3.18,
        0.78,
        10,
        INK2,
        False,
        line=1.42,
    )
    add_text(slide, "宁可说“判断不了” → 不给假确定性", 5.0, 6.02, 3.3, 0.26, 9.5, ORANGE, True)

    add_box(slide, 8.74, 4.18, 3.82, 2.33, GREEN_LIGHT, None)
    add_text(slide, "关键设计 03", 8.98, 4.4, 1.4, 0.25, 9.5, GREEN, True)
    add_text(slide, "病害不是唯一出口", 8.98, 4.7, 3.2, 0.34, 16, INK, True)
    add_text(
        slide,
        "病理问题与养护问题可能同时存在；病害之后仍可继续做症状判因，不会一条路走死。",
        8.98,
        5.18,
        3.18,
        0.78,
        10,
        INK2,
        False,
        line=1.42,
    )
    add_text(slide, "病害通道 + 养护通道并行", 8.98, 6.02, 3.1, 0.26, 9.5, GREEN, True)


def slide_screenshot(prs):
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    slide.background.fill.solid()
    slide.background.fill.fore_color.rgb = BG
    add_header(
        slide,
        "PRODUCT EVIDENCE",
        "识别页已经跑通：结果可读、依据可追溯",
        "以下为当前前端页面的实际截图",
        number=4,
    )

    add_image_card(
        slide,
        ASSET_DIR / "disease_recognition_leaf.png",
        0.72,
        1.74,
        8.12,
        4.8,
    )
    add_box(slide, 9.08, 1.74, 3.48, 2.97, WHITE, LINE)
    add_text(slide, "这张页面同时回答四个问题", 9.34, 1.98, 2.9, 0.3, 13.2, INK, True)
    qa = [
        ("是什么", "绿萝·黄化病，置信度 94.1%"),
        ("像不像", "列出健康、叶斑病等其他可能"),
        ("为什么", "给出典型症状，不让用户只看到病名"),
        ("怎么办", "处理建议、预防方式直接可执行"),
    ]
    for i, (q, a) in enumerate(qa):
        yy = 2.48 + i * 0.5
        add_box(slide, 9.34, yy + 0.05, 0.35, 0.25, GREEN_LIGHT, None)
        add_text(slide, q, 9.43, yy + 0.08, 0.31, 0.16, 7.4, GREEN, True, PP_ALIGN.CENTER)
        add_text(slide, a, 9.82, yy + 0.01, 2.43, 0.32, 8.7, INK2, False, line=1.16)

    add_box(slide, 9.08, 4.86, 3.48, 1.08, WHITE, LINE)
    add_picture_contain(
        slide,
        ASSET_DIR / "disease_recognition_cactus.png",
        9.19,
        4.95,
        3.26,
        0.67,
    )
    add_text(slide, "另一类结果：虫害", 9.28, 5.7, 1.55, 0.2, 8.8, MUTED, True)
    add_text(slide, "仙人掌·胭脂虫危害 89.7%", 10.2, 5.69, 2.1, 0.2, 8.8, RED, True, PP_ALIGN.RIGHT)

    add_text(
        slide,
        "页面不是只给一个病名：它把置信度、其他候选、症状依据和处理建议放在同一屏。",
        0.92,
        6.67,
        7.75,
        0.3,
        10.4,
        GREEN,
        True,
    )


def slide_new_species_examples(prs):
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    slide.background.fill.solid()
    slide.background.fill.fore_color.rgb = BG
    add_header(
        slide,
        "NEW SPECIES EXAMPLES",
        "新增四类物种：各抽一种病害做验证",
        "四张截图均来自同一识别页，验证模型链路可跨物种复用",
        number=5,
    )

    examples = [
        (
            "01",
            "月季",
            "黑斑病",
            "96.9%",
            "真菌性",
            "new_species_rose.png",
            RED,
            RED_LIGHT,
        ),
        (
            "02",
            "扶桑",
            "日灼",
            "95.3%",
            "生理性",
            "new_species_hibiscus.png",
            ORANGE,
            ORANGE_LIGHT,
        ),
        (
            "03",
            "姜黄",
            "叶斑病",
            "95.6%",
            "真菌性",
            "new_species_turmeric.png",
            GREEN,
            GREEN_PALE,
        ),
        (
            "04",
            "菊花",
            "斑枯病",
            "94.1%",
            "真菌性",
            "new_species_chrysanthemum.png",
            GREEN,
            GREEN_PALE,
        ),
    ]

    positions = [(0.78, 1.82), (6.62, 1.82), (0.78, 3.98), (6.62, 3.98)]
    for (code, species, disease, confidence, category, asset, accent, fill), (x, y) in zip(
        examples, positions
    ):
        add_box(slide, x, y, 5.56, 1.98, WHITE, LINE)
        add_box(slide, x + 0.18, y + 0.16, 0.4, 0.28, fill, None)
        add_text(
            slide,
            code,
            x + 0.25,
            y + 0.2,
            0.26,
            0.16,
            8,
            accent,
            True,
            PP_ALIGN.CENTER,
        )
        add_text(
            slide,
            f"{species} · {disease}",
            x + 0.74,
            y + 0.14,
            2.45,
            0.3,
            13.2,
            INK,
            True,
        )
        add_text(
            slide,
            category,
            x + 3.58,
            y + 0.18,
            0.82,
            0.22,
            8.5,
            accent,
            True,
            PP_ALIGN.RIGHT,
        )
        add_text(
            slide,
            confidence,
            x + 4.55,
            y + 0.14,
            0.72,
            0.26,
            12,
            accent,
            True,
            PP_ALIGN.RIGHT,
        )
        add_picture_contain(
            slide,
            ASSET_DIR / asset,
            x + 0.18,
            y + 0.53,
            5.2,
            1.31,
        )

    add_text(
        slide,
        "同一套“识别 → 物种过滤 → 病害分类 → 知识库建议”链路，可直接承接不同物种。",
        0.8,
        6.55,
        11.7,
        0.3,
        10.5,
        GREEN,
        True,
    )


def slide_model_results(prs):
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    slide.background.fill.solid()
    slide.background.fill.fore_color.rgb = BG
    add_header(
        slide,
        "MODEL RESULTS",
        "模型效果：44 类基础上，重点物种再做专用模型",
        "同分布测试集用于判断能力边界，不等同于真实手机场景准确率",
        number=6,
    )

    metrics = [
        ("10", "覆盖物种"),
        ("44", "病害类别"),
        ("91.00%", "通用模型 Top-1"),
        ("98.10%", "通用模型 Top-3"),
    ]
    for i, (value, label) in enumerate(metrics):
        x = 0.78 + i * 2.13
        add_box(slide, x, 1.9, 1.92, 1.18, WHITE, LINE)
        add_text(slide, value, x + 0.18, 2.06, 1.58, 0.42, 21, GREEN if i < 2 else ORANGE, True)
        add_text(slide, label, x + 0.18, 2.58, 1.58, 0.24, 8.7, MUTED)

    add_text(slide, "测试集表现", 0.78, 3.48, 2.1, 0.31, 13.2, INK, True)
    rows = [
        ("通用 44 类", 91.00, 98.10, "10 物种 / 4.26M 参数"),
        ("月季专用 10 类", 90.46, 98.34, "224px，难类针对性提升"),
        ("芦荟专用 5 类", 74.81, 95.05, "高风险类，需继续补样本"),
    ]
    for i, (name, top1, top3, note) in enumerate(rows):
        y = 3.94 + i * 0.82
        add_text(slide, name, 0.78, y, 1.75, 0.26, 10.4, INK, True)
        add_box(slide, 2.62, y + 0.06, 2.4, 0.08, RGBColor(0xEA, 0xEC, 0xE7), None, radius=False)
        add_box(slide, 2.62, y + 0.06, 2.4 * top1 / 100, 0.08, GREEN, None, radius=False)
        add_text(slide, f"{top1:.2f}%", 5.12, y - 0.04, 0.72, 0.24, 10.2, GREEN, True)
        add_text(slide, f"Top-3 {top3:.2f}%", 6.0, y - 0.04, 1.2, 0.24, 9.3, MUTED, False)
        add_text(slide, note, 7.35, y - 0.02, 2.15, 0.28, 8.6, MUTED)

    add_box(slide, 9.64, 1.9, 2.92, 4.02, DARK, None)
    add_text(slide, "一个明显的收益", 9.92, 2.16, 2.2, 0.25, 9.5, RGBColor(0xA8, 0xC8, 0xB7), True)
    add_text(slide, "月季真菌性叶斑", 9.92, 2.53, 2.1, 0.3, 13.2, WHITE, True)
    add_text(slide, "65.48%", 9.92, 2.98, 1.15, 0.4, 22, RGBColor(0xCE, 0x91, 0x4A), True)
    add_text(slide, "→", 11.18, 3.02, 0.3, 0.28, 18, WHITE, True, PP_ALIGN.CENTER)
    add_text(slide, "75.90%", 11.34, 2.98, 1.08, 0.4, 18, RGBColor(0x87, 0xC9, 0xA7), True)
    add_text(slide, "通用模型", 9.92, 3.45, 0.9, 0.22, 8.5, RGBColor(0xA8, 0xC8, 0xB7))
    add_text(slide, "月季专用", 11.46, 3.45, 0.9, 0.22, 8.5, RGBColor(0xA8, 0xC8, 0xB7))
    add_box(slide, 9.92, 3.92, 2.32, 0.01, RGBColor(0x3B, 0x5B, 0x4B), None, radius=False)
    add_multiline(
        slide,
        [
            "数据准备缩放到 448px，避免手机原图拖慢训练。",
            "按原图组切分训练 / 验证 / 测试，防止同图副本泄漏。",
            "通用模型不给力时，专用模型只做针对性补强。",
        ],
        9.92,
        4.15,
        2.3,
        1.35,
        8.6,
        RGBColor(0xD7, 0xE1, 0xDB),
        line=1.38,
        bullet=True,
        space_after=5,
    )

    add_text(
        slide,
        "关键结论：模型指标已可用，但真实拍摄的构图、光照和背景差异仍需继续用真实样本验证。",
        0.78,
        6.42,
        11.8,
        0.3,
        10.4,
        RED,
        True,
    )


def slide_next(prs):
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    slide.background.fill.solid()
    slide.background.fill.fore_color.rgb = BG
    add_header(
        slide,
        "CONCLUSION & NEXT",
        "项目结论与下一步",
        "能力已经闭环，下一步优先解决“真实场景更稳”和“覆盖更完整”",
        number=7,
    )

    add_box(slide, 0.78, 1.88, 6.05, 4.36, WHITE, LINE)
    add_text(slide, "当前已完成", 1.06, 2.12, 2.0, 0.32, 14, GREEN, True)
    done = [
        ("产品链路闭环", "拍照识别 → 物种确认 → 病害判断 → 症状判因 → 养护计划 / 日历 / 日志"),
        ("模型已接入服务", "通用 44 类 / 10 物种，芦荟 5 类、月季 10 类专用模型；模型缺失时页面可降级"),
        ("可信机制落地", "低置信度不硬判，主动披露覆盖范围，证据与建议直接来自知识库"),
    ]
    for i, (title, body) in enumerate(done):
        y = 2.65 + i * 1.12
        add_text(slide, "✓", 1.08, y - 0.02, 0.26, 0.25, 13, GREEN, True, PP_ALIGN.CENTER)
        add_text(slide, title, 1.42, y - 0.03, 1.55, 0.27, 11.5, INK, True)
        add_text(slide, body, 1.42, y + 0.33, 4.85, 0.48, 9.3, INK2, False, line=1.38)

    add_box(slide, 7.08, 1.88, 5.46, 4.36, GREEN_LIGHT, None)
    add_text(slide, "下一阶段优先级", 7.36, 2.12, 2.4, 0.32, 14, INK, True)
    next_items = [
        ("01", "补真实手机叶片样本并重训", "优先覆盖黄化、叶斑、虫害等高频问题"),
        ("02", "补覆盖不足的病害与追问", "软腐、日灼、根腐等类别继续完善"),
        ("03", "稳定部署与移动端发布", "云端地址固定、HTTPS 与安装包交付"),
    ]
    for i, (code, title, body) in enumerate(next_items):
        y = 2.7 + i * 1.08
        add_text(slide, code, 7.38, y, 0.55, 0.26, 10.5, ORANGE, True)
        add_text(slide, title, 8.02, y - 0.03, 3.75, 0.3, 11.2, INK, True)
        add_text(slide, body, 8.02, y + 0.33, 3.82, 0.3, 9.2, MUTED, False)

    add_box(slide, 0.78, 6.5, 11.76, 0.5, DARK, None)
    add_text(
        slide,
        "从“能识别一个结果”，走向“能解释、能复核、能持续使用”的诊断工具。",
        1.02,
        6.61,
        11.2,
        0.24,
        10.8,
        WHITE,
        True,
        PP_ALIGN.CENTER,
    )


def build():
    make_assets()
    prs = Presentation()
    prs.slide_width = Inches(W)
    prs.slide_height = Inches(H)
    prs.core_properties.title = "植语助手 GreenThumb AI 项目汇报"
    prs.core_properties.subject = "室内绿植养护与病害识别项目"
    prs.core_properties.author = "GreenThumb AI"

    slide_cover(prs)
    slide_what_we_did(prs)
    slide_pipeline(prs)
    slide_screenshot(prs)
    slide_new_species_examples(prs)
    slide_model_results(prs)
    slide_next(prs)

    prs.save(OUT)
    print(f"saved={OUT}")
    print(f"slides={len(prs.slides)}")


if __name__ == "__main__":
    build()
