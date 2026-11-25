# -*- coding: utf-8 -*-
"""Build a six-slide weekly report focused on four newly added diseases."""

from pathlib import Path

from PIL import Image
from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.text import MSO_ANCHOR, PP_ALIGN
from pptx.oxml.ns import qn
from pptx.util import Inches, Pt


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "植语助手-本周新增四类病害汇报-最终版.pptx"
SHOTS = ROOT / "report" / "weekly_shots"

FONT = "Microsoft YaHei"
W, H = 13.333, 7.5

BG = RGBColor(0xF7, 0xF5, 0xEF)
INK = RGBColor(0x18, 0x24, 0x1E)
INK2 = RGBColor(0x3E, 0x4B, 0x44)
MUTED = RGBColor(0x7D, 0x88, 0x81)
LINE = RGBColor(0xDF, 0xE2, 0xDB)
WHITE = RGBColor(0xFF, 0xFF, 0xFF)
GREEN = RGBColor(0x1D, 0x6B, 0x4B)
GREEN_LIGHT = RGBColor(0xE6, 0xF0, 0xEA)
GREEN_PALE = RGBColor(0xF1, 0xF7, 0xF4)
ORANGE = RGBColor(0xC5, 0x7B, 0x2D)
ORANGE_LIGHT = RGBColor(0xFA, 0xEF, 0xE2)
RED = RGBColor(0xBD, 0x45, 0x3E)
RED_LIGHT = RGBColor(0xF9, 0xEA, 0xE8)
DARK = RGBColor(0x14, 0x2A, 0x21)


def set_font(run, size=14, bold=False, color=INK):
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


def text(
    slide,
    value,
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
):
    box = slide.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    tf = box.text_frame
    tf.clear()
    tf.word_wrap = True
    tf.vertical_anchor = anchor
    tf.margin_left = 0
    tf.margin_right = 0
    tf.margin_top = 0
    tf.margin_bottom = 0
    p = tf.paragraphs[0]
    p.alignment = align
    p.line_spacing = line
    r = p.add_run()
    r.text = value
    set_font(r, size, bold, color)
    return tf


def box(
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
    kind = MSO_SHAPE.ROUNDED_RECTANGLE if radius else MSO_SHAPE.RECTANGLE
    shape = slide.shapes.add_shape(kind, Inches(x), Inches(y), Inches(w), Inches(h))
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


def number(slide, value, x, y, fill=GREEN, color=WHITE, diameter=0.42):
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
    r.text = str(value)
    set_font(r, 11, True, color)


def background(slide):
    fill = slide.background.fill
    fill.solid()
    fill.fore_color.rgb = BG


def header(slide, kicker, title, subtitle=None, page=None):
    text(slide, kicker, 0.72, 0.45, 8.0, 0.24, 10.5, GREEN, True)
    text(slide, title, 0.72, 0.78, 11.9, 0.58, 27, INK, True, line=1.05)
    if subtitle:
        text(slide, subtitle, 0.72, 1.38, 11.8, 0.32, 12.2, MUTED)
    if page:
        text(
            slide,
            f"{page:02d} / 06",
            11.88,
            7.08,
            0.74,
            0.18,
            9,
            MUTED,
            align=PP_ALIGN.RIGHT,
        )


def full_picture(slide, path, x, y, w, h):
    # Keep the original screenshot aspect ratio and preserve the entire page.
    with Image.open(path) as img:
        iw, ih = img.size
    scale = min(w / iw, h / ih)
    dw, dh = iw * scale, ih * scale
    slide.shapes.add_picture(
        str(path),
        Inches(x + (w - dw) / 2),
        Inches(y + (h - dh) / 2),
        Inches(dw),
        Inches(dh),
    )


def cover(prs):
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    background(slide)

    box(slide, 8.95, 0, 4.383, 7.5, DARK, None, radius=False)
    text(slide, "WEEKLY REPORT · 2026.11", 0.78, 0.6, 6.4, 0.25, 10.5, GREEN, True)
    text(slide, "本周工作汇报", 0.78, 1.32, 7.3, 0.72, 40, INK, True)
    text(slide, "新增四类病害识别", 0.78, 2.15, 7.6, 0.72, 31, GREEN, True)
    text(
        slide,
        "完成四类病害的识别接入与页面结果验证",
        0.8,
        3.1,
        7.2,
        0.38,
        16.5,
        INK2,
    )
    text(
        slide,
        "仙人掌胭脂虫危害  ·  万寿菊链格孢叶枯病  ·  吊兰真菌性叶斑病  ·  虎皮兰炭疽病",
        0.8,
        3.72,
        7.45,
        0.4,
        13.2,
        MUTED,
        True,
    )

    items = [
        ("4 类", "本周新增病害"),
        ("10 种", "当前覆盖物种"),
        ("44 类", "当前识别类别"),
    ]
    for i, (value, label) in enumerate(items):
        x = 0.82 + i * 2.12
        text(slide, value, x, 5.48, 1.72, 0.43, 23, GREEN if i < 2 else ORANGE, True)
        text(slide, label, x, 5.98, 1.72, 0.26, 9.5, MUTED)

    text(slide, "植语助手 GreenThumb AI", 0.82, 6.75, 3.8, 0.24, 10, MUTED)

    text(slide, "本周结果", 9.38, 0.76, 3.2, 0.28, 10.5, RGBColor(0xB9, 0xD6, 0xC7), True)
    names = [
        ("01", "仙人掌 · 胭脂虫危害", "97.2%"),
        ("02", "万寿菊 · 链格孢叶枯病", "92.0%"),
        ("03", "吊兰 · 真菌性叶斑病", "94.8%"),
        ("04", "虎皮兰 · 炭疽病", "93.5%"),
    ]
    for i, (code, name, confidence) in enumerate(names):
        y = 1.3 + i * 1.12
        box(
            slide,
            9.38,
            y,
            3.25,
            0.84,
            RGBColor(0x20, 0x39, 0x2E),
            RGBColor(0x39, 0x58, 0x48),
        )
        text(slide, code, 9.6, y + 0.17, 0.34, 0.2, 9, RGBColor(0x9E, 0xC0, 0xAF), True)
        text(slide, name, 10.02, y + 0.12, 1.75, 0.28, 11.5, WHITE, True)
        text(slide, confidence, 11.58, y + 0.13, 0.78, 0.26, 12, RGBColor(0xE3, 0xB2, 0x69), True, PP_ALIGN.RIGHT)

    text(
        slide,
        "四类均保留完整页面截图，展示病名、置信度、其他可能、症状依据和处理建议。",
        9.4,
        6.22,
        3.2,
        0.72,
        9.4,
        RGBColor(0xC8, 0xD6, 0xCE),
        line=1.4,
    )


def overview(prs):
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    background(slide)
    header(
        slide,
        "THIS WEEK",
        "本周主要做了什么",
        "新增四类病害识别，并逐类验证页面能完整返回识别结果和处置建议",
        page=2,
    )

    diseases = [
        ("01", "仙人掌", "胭脂虫危害", "97.2%", "虫害", RED_LIGHT, RED),
        ("02", "万寿菊", "链格孢叶枯病", "92.0%", "真菌性", ORANGE_LIGHT, ORANGE),
        ("03", "吊兰", "真菌性叶斑病", "94.8%", "真菌性", GREEN_PALE, GREEN),
        ("04", "虎皮兰", "炭疽病", "93.5%", "真菌性", RED_LIGHT, RED),
    ]
    for i, (code, species, disease, confidence, category, fill, accent) in enumerate(diseases):
        x = 0.78 + i * 3.04
        box(slide, x, 2.08, 2.78, 1.72, fill, None)
        text(slide, code, x + 0.2, 2.28, 0.45, 0.24, 10, accent, True)
        text(slide, species, x + 0.2, 2.66, 1.4, 0.34, 17, INK, True)
        text(slide, disease, x + 0.2, 3.06, 1.55, 0.32, 13.2, accent, True)
        text(slide, category, x + 1.7, 2.74, 0.82, 0.22, 8.5, accent, True, PP_ALIGN.RIGHT)
        text(slide, confidence, x + 1.48, 3.03, 1.06, 0.34, 17, accent, True, PP_ALIGN.RIGHT)

    box(slide, 0.78, 4.16, 11.78, 2.04, WHITE, LINE)
    text(slide, "当前模型覆盖", 1.04, 4.4, 3.2, 0.3, 13, INK, True)

    text(slide, "10", 1.04, 4.88, 1.3, 0.56, 31, GREEN, True)
    text(slide, "种物种", 1.04, 5.48, 1.3, 0.27, 10, MUTED)
    box(slide, 2.55, 4.82, 0.02, 0.92, LINE, None, radius=False)

    text(slide, "44", 2.86, 4.88, 1.3, 0.56, 31, ORANGE, True)
    text(slide, "个识别类别", 2.86, 5.48, 1.55, 0.27, 10, MUTED)
    box(slide, 4.58, 4.82, 0.02, 0.92, LINE, None, radius=False)

    text(
        slide,
        "其中 34 个为病害 / 异常状态类别，10 个为健康类别。",
        4.88,
        4.91,
        3.18,
        0.56,
        11,
        INK2,
        False,
        line=1.35,
    )
    text(
        slide,
        "芦荟 · 仙人掌 · 菊花 · 扶桑 · 绿萝 · 月季 · 虎皮兰 · 吊兰 · 万寿菊 · 姜黄",
        4.88,
        5.52,
        7.14,
        0.42,
        9.6,
        MUTED,
        False,
        line=1.3,
    )

    text(
        slide,
        "本次汇报只保留本周新增病害和验证结果；完整页面截图见后续四页。",
        0.78,
        6.54,
        11.8,
        0.3,
        10.5,
        MUTED,
    )


def screenshot_slide(prs, item, page):
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    background(slide)

    text(slide, item["kicker"], 0.55, 0.28, 6.0, 0.22, 9.5, item["accent"], True)
    text(slide, item["title"], 0.55, 0.55, 7.8, 0.46, 23, INK, True)

    # The screenshot is shown in full. No crop and no overlay.
    box(slide, 0.42, 1.13, 8.3, 5.96, WHITE, LINE)
    full_picture(
        slide,
        SHOTS / item["file"],
        0.56,
        1.2,
        7.98,
        5.82,
    )

    panel_x = 8.98
    box(slide, panel_x, 1.16, 3.74, 5.91, WHITE, LINE)
    text(slide, item["step"], panel_x + 0.26, 1.42, 1.25, 0.24, 9.2, item["accent"], True)
    text(slide, item["species"], panel_x + 0.26, 1.77, 1.7, 0.38, 22, INK, True)
    text(slide, item["disease"], panel_x + 0.26, 2.2, 2.7, 0.42, 20, item["accent"], True)
    text(slide, item["confidence"], panel_x + 0.26, 2.78, 1.55, 0.52, 27, item["accent"], True)
    text(slide, item["category"], panel_x + 2.35, 1.82, 0.96, 0.24, 9, item["accent"], True, PP_ALIGN.RIGHT)

    text(slide, "页面完整包含", panel_x + 0.26, 3.56, 2.9, 0.28, 12.2, INK, True)
    lines = [
        ("01", "病害名称与置信度"),
        ("02", "其他可能结果"),
        ("03", "典型症状依据"),
        ("04", "处理建议与预防"),
    ]
    for i, (num, label) in enumerate(lines):
        y = 3.98 + i * 0.55
        text(slide, num, panel_x + 0.26, y, 0.35, 0.2, 8.4, item["accent"], True)
        text(slide, label, panel_x + 0.68, y - 0.01, 2.45, 0.24, 10.2, INK2)

    box(slide, panel_x + 0.26, 6.22, 3.02, 0.01, LINE, None, radius=False)
    text(
        slide,
        "截图按原始完整页面展示，未裁切。",
        panel_x + 0.26,
        6.42,
        2.9,
        0.24,
        9.2,
        MUTED,
    )

    text(
        slide,
        f"{page:02d} / 06",
        11.86,
        7.08,
        0.8,
        0.18,
        9,
        MUTED,
        align=PP_ALIGN.RIGHT,
    )


def build():
    prs = Presentation()
    prs.slide_width = Inches(W)
    prs.slide_height = Inches(H)
    prs.core_properties.title = "本周新增四类病害识别汇报"
    prs.core_properties.subject = "本周主要工作：新增四类病害识别"
    prs.core_properties.author = "GreenThumb AI"

    cover(prs)
    overview(prs)

    screenshots = [
        {
            "kicker": "DISEASE 01",
            "title": "仙人掌 · 胭脂虫危害",
            "step": "病害识别结果",
            "species": "仙人掌",
            "disease": "胭脂虫危害",
            "confidence": "97.2%",
            "category": "虫害",
            "file": "Cactus_Dactylopius_Opuntia.png",
            "accent": RED,
        },
        {
            "kicker": "DISEASE 02",
            "title": "万寿菊 · 链格孢叶枯病",
            "step": "病害识别结果",
            "species": "万寿菊",
            "disease": "链格孢叶枯病",
            "confidence": "92.0%",
            "category": "真菌性",
            "file": "Tagetes_Alternaria_Leaf_Blight.png",
            "accent": ORANGE,
        },
        {
            "kicker": "DISEASE 03",
            "title": "吊兰 · 真菌性叶斑病",
            "step": "病害识别结果",
            "species": "吊兰",
            "disease": "真菌性叶斑病",
            "confidence": "94.8%",
            "category": "真菌性",
            "file": "Spider_Plant_Fungal_leaf_spot.png",
            "accent": GREEN,
        },
        {
            "kicker": "DISEASE 04",
            "title": "虎皮兰 · 炭疽病",
            "step": "病害识别结果",
            "species": "虎皮兰",
            "disease": "炭疽病",
            "confidence": "93.5%",
            "category": "真菌性",
            "file": "Snake_Plant_Anthracnose.png",
            "accent": RED,
        },
    ]
    for index, item in enumerate(screenshots, start=3):
        screenshot_slide(prs, item, index)

    prs.save(OUT)
    print(f"saved={OUT}")
    print(f"slides={len(prs.slides)}")


if __name__ == "__main__":
    build()
