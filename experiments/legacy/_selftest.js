/**
 * 零依赖自测：验证 HSV 颜色指标与健康指数的核心算法
 *
 * 目的：在拿不到真实数据集的情况下，先证明【算法逻辑】是对的。
 * 没有网络、没有图像库、没有真实图片 —— 用合成像素直接验证数学。
 *
 * 运行： node greenthumb/experiments/_selftest.js
 * 结果同时写入同目录的 selftest-report.txt（stdout 在某些环境下不可靠）
 *
 * 与 Python 端 experiments/common/color_metrics.py 采用完全一致的 OpenCV 8-bit 约定：
 *   H ∈ [0,180)   S ∈ [0,255]   V ∈ [0,255]
 */
const fs = require('fs')
const path = require('path')

/* ============ 核心算法（与 Python 端保持一致） ============ */

/** RGB(0-255) -> HSV，OpenCV 8-bit 约定：H/2, S*255, V*255 */
function rgb2hsv(r, g, b) {
  const R = r / 255, G = g / 255, B = b / 255
  const max = Math.max(R, G, B), min = Math.min(R, G, B), d = max - min
  let h = 0
  if (d !== 0) {
    if (max === R) h = 60 * ((((G - B) / d) % 6 + 6) % 6)
    else if (max === G) h = 60 * ((B - R) / d + 2)
    else h = 60 * ((R - G) / d + 4)
  }
  const s = max === 0 ? 0 : d / max
  return { h: h / 2, s: s * 255, v: max * 255 }
}

/** 三个色彩类别的阈值（与 docs/13 一致，OpenCV 8-bit 约定） */
const isGreen = (c) => c.h >= 35 && c.h <= 85 && c.s >= 40 && c.v >= 40
const isYellow = (c) => c.h >= 20 && c.h < 35 && c.s >= 60 && c.v >= 60
const isBrown = (c) => c.h >= 0 && c.h < 20 && c.s >= 60 && c.v >= 20 && c.v <= 160

const clamp = (x, lo, hi) => Math.max(lo, Math.min(hi, x))

/** 从一批像素统计颜色占比 */
function colorRatios(pixels) {
  let g = 0, y = 0, br = 0
  for (const px of pixels) {
    const c = rgb2hsv(px[0], px[1], px[2])
    if (isGreen(c)) g++
    else if (isYellow(c)) y++
    else if (isBrown(c)) br++
  }
  const plant = g + y + br
  if (plant === 0) return { valid: false, greenRatio: 0, yellowRatio: 0, brownRatio: 0, plantPixels: 0 }
  return {
    valid: true,
    greenRatio: g / plant,
    yellowRatio: y / plant,
    brownRatio: br / plant,
    plantPixels: plant
  }
}

/** 健康指数：绿色加分，黄褐扣分，clamp 到 0-100 */
function healthIndex(r) {
  return Math.round(clamp(r.greenRatio * 100 - r.yellowRatio * 80 - r.brownRatio * 120, 0, 100))
}

/* ============ 测试框架 ============ */
const LOG = []
const say = (s) => { LOG.push(s) }
let pass = 0, fail = 0

function check(name, actual, expected, tol) {
  const t = tol === undefined ? 0.5 : tol
  const ok = typeof expected === 'number'
    ? Math.abs(actual - expected) <= t
    : JSON.stringify(actual) === JSON.stringify(expected)
  if (ok) { pass++; say('  [PASS] ' + name) }
  else { fail++; say('  [FAIL] ' + name + '  expected=' + JSON.stringify(expected) + ' actual=' + JSON.stringify(actual)) }
}

/* ============ 1. RGB -> HSV 基准值 ============ */
say('[1] RGB->HSV (对照 OpenCV 基准值)')
check('纯红 (255,0,0) -> [0,255,255]', (function () { const c = rgb2hsv(255, 0, 0); return [Math.round(c.h), Math.round(c.s), Math.round(c.v)] })(), [0, 255, 255])
check('纯绿 (0,255,0) -> [60,255,255]', (function () { const c = rgb2hsv(0, 255, 0); return [Math.round(c.h), Math.round(c.s), Math.round(c.v)] })(), [60, 255, 255])
check('纯蓝 (0,0,255) -> [120,255,255]', (function () { const c = rgb2hsv(0, 0, 255); return [Math.round(c.h), Math.round(c.s), Math.round(c.v)] })(), [120, 255, 255])
check('灰 (128,128,128) -> S=0', (function () { const c = rgb2hsv(128, 128, 128); return Math.round(c.s) })(), 0)

/* ============ 2. 颜色分类 ============ */
say('')
say('[2] 颜色分类')
check('叶片绿 (34,139,34) 判为绿', isGreen(rgb2hsv(34, 139, 34)), true)
check('枯黄 (218,165,32) 判为黄', isYellow(rgb2hsv(218, 165, 32)), true)
check('褐色 (101,67,33) 判为褐', isBrown(rgb2hsv(101, 67, 33)), true)
check('叶片绿 不是黄', isYellow(rgb2hsv(34, 139, 34)), false)
check('白背景 (255,255,255) 三类都不中', (function () { const c = rgb2hsv(255, 255, 255); return isGreen(c) || isYellow(c) || isBrown(c) })(), false)
check('黑背景 (0,0,0) 三类都不中', (function () { const c = rgb2hsv(0, 0, 0); return isGreen(c) || isYellow(c) || isBrown(c) })(), false)

/* ============ 3. 健康指数合成 ============ */
say('')
say('[3] 健康指数合成')
check('全绿 -> 100', healthIndex({ greenRatio: 1, yellowRatio: 0, brownRatio: 0 }), 100)
check('全黄 -> 0 (clamp)', healthIndex({ greenRatio: 0, yellowRatio: 1, brownRatio: 0 }), 0)
check('全褐 -> 0 (clamp)', healthIndex({ greenRatio: 0, yellowRatio: 0, brownRatio: 1 }), 0)
check('70%绿+30%黄 -> 46', healthIndex({ greenRatio: 0.7, yellowRatio: 0.3, brownRatio: 0 }), 46)
check('90%绿+10%黄 -> 82', healthIndex({ greenRatio: 0.9, yellowRatio: 0.1, brownRatio: 0 }), 82)
check('80%绿+10%黄+10%褐 -> 60', healthIndex({ greenRatio: 0.8, yellowRatio: 0.1, brownRatio: 0.1 }), 60)

/* ============ 4. 端到端：健康组 vs 过浇组 ============ */
say('')
say('[4] 端到端模拟（对应第 1 层验证）')

const healthyPixels = []
for (let i = 0; i < 900; i++) healthyPixels.push([34, 139, 34])    // 绿
for (let i = 0; i < 50; i++) healthyPixels.push([218, 165, 32])    // 黄
for (let i = 0; i < 50; i++) healthyPixels.push([255, 255, 255])   // 白背景

const overwateredPixels = []
for (let i = 0; i < 450; i++) overwateredPixels.push([34, 139, 34])
for (let i = 0; i < 400; i++) overwateredPixels.push([218, 165, 32])
for (let i = 0; i < 150; i++) overwateredPixels.push([101, 67, 33])

const hh = colorRatios(healthyPixels)
const oo = colorRatios(overwateredPixels)

say('    健康组: green=' + hh.greenRatio.toFixed(3) + ' yellow=' + hh.yellowRatio.toFixed(3) + ' brown=' + hh.brownRatio.toFixed(3) + ' -> 健康指数 ' + healthIndex(hh))
say('    过浇组: green=' + oo.greenRatio.toFixed(3) + ' yellow=' + oo.yellowRatio.toFixed(3) + ' brown=' + oo.brownRatio.toFixed(3) + ' -> 健康指数 ' + healthIndex(oo))

check('白色背景被正确排除（植物像素=950，白背景不计入）', hh.plantPixels, 950)
check('健康组绿色占比 ~0.947', hh.greenRatio, 0.947, 0.01)
check('过浇组绿色占比 ~0.450', oo.greenRatio, 0.450, 0.01)
check('健康指数：健康组 > 过浇组', healthIndex(hh) > healthIndex(oo), true)

/* ============ 5. 观测对变化判别 ============ */
say('')
say('[5] 观测对变化判别（对应第 2 层验证）')

const obs1 = { greenRatio: 0.85, yellowRatio: 0.12, brownRatio: 0.03 }
const obs2 = { greenRatio: 0.48, yellowRatio: 0.40, brownRatio: 0.12 }
const hi1 = healthIndex(obs1), hi2 = healthIndex(obs2)

say('    week6 健康指数 ' + hi1 + '  ->  week7 健康指数 ' + hi2 + '  =>  delta = ' + (hi2 - hi1))
check('变化方向被正确判为"恶化"（delta < 0）', hi2 - hi1 < 0, true)
check('delta 幅度约 -70（72 -> 2）', hi2 - hi1, -70, 3)

/* ============ 汇总 ============ */
say('')
say('='.repeat(46))
say('  通过 ' + pass + ' 项，失败 ' + fail + ' 项')
say('='.repeat(46))

const reportPath = path.join(__dirname, 'selftest-report.txt')
fs.writeFileSync(reportPath, LOG.join('\n') + '\n\nRESULT pass=' + pass + ' fail=' + fail + '\n', 'utf8')
process.exit(fail === 0 ? 0 : 1)
