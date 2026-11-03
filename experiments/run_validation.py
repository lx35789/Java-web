# -*- coding: utf-8 -*-
"""
GreenThumb AI - Layer1/2/4 validation on REAL data
Chili Pepper BBWV2 & Flooding dataset (Zenodo 17901489)

Run:  python run_validation.py
Out:  <OUTDIR>\\color_features.csv
      <OUTDIR>\\validation_report.txt
"""
import os, sys, json, csv, time, math, re, collections, traceback

import numpy as np
import cv2

ROOT = r'C:\Users\lenovo\greenthumb\experiments\data\Chili\pepper_chili_dataset'
IMG = os.path.join(ROOT, 'images')
OUTDIR = os.environ.get('GT_OUT') or r'C:\Users\lenovo\greenthumb\experiments\results'
os.makedirs(OUTDIR, exist_ok=True)

CLASSES = {0: 'Healthy', 1: 'Flood', 2: 'BBWV2'}

# OpenCV 8-bit HSV: H in [0,180), S,V in [0,255]
GREEN_LO, GREEN_HI = (35, 40, 40), (85, 255, 255)
YELLOW_LO, YELLOW_HI = (20, 60, 60), (35, 255, 255)
BROWN_LO, BROWN_HI = (0, 60, 20), (20, 255, 160)
MIN_PLANT_PIXELS = 500
MAX_SIDE = 512

_L = np.array
_u8 = np.uint8


def metrics(img):
    h, w = img.shape[:2]
    sc = max(h, w) / float(MAX_SIDE)
    if sc > 1:
        img = cv2.resize(img, (int(round(w / sc)), int(round(h / sc))), interpolation=cv2.INTER_AREA)
    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
    S = hsv[..., 1]
    V = hsv[..., 2]
    green = cv2.inRange(hsv, _L(GREEN_LO, _u8), _L(GREEN_HI, _u8)) > 0
    yellow = cv2.inRange(hsv, _L(YELLOW_LO, _u8), _L(YELLOW_HI, _u8)) > 0
    brown = cv2.inRange(hsv, _L(BROWN_LO, _u8), _L(BROWN_HI, _u8)) > 0
    tot = img.shape[0] * img.shape[1]
    plant = green | yellow | brown
    nplant = int(plant.sum())
    if nplant < MIN_PLANT_PIXELS:
        return None
    g = float(green.sum()); y = float(yellow.sum()); b = float(brown.sum())
    gr, yr, br = g / nplant, y / nplant, b / nplant
    hi = gr * 100.0 - yr * 80.0 - br * 120.0
    hi = max(0.0, min(100.0, hi))
    pv = V[plant]
    return dict(w=img.shape[1], h=img.shape[0],
                plant_ratio=nplant / float(tot),
                green_ratio=gr, yellow_ratio=yr, brown_ratio=br,
                health_index=hi,
                mean_sat=float(S[plant].mean()),
                mean_val=float(pv.mean()),
                std_val=float(pv.std()),
                dark_ratio=float((pv < 60).mean()))


def parse(name):
    stem = name[:-4] if name.lower().endswith('.jpg') else name
    p = stem.split('_')
    prefix, date = p[0], p[1]
    rest = p[2:]
    if 'DSC' in rest[0]:
        return dict(prefix=prefix, date=date, family='DSC', plant=-1, view=-1, shot=int(rest[-1]))
    m = re.match(r'^(\d+)-(\d+)$', rest[0])
    if m:
        return dict(prefix=prefix, date=date, family='PV', plant=int(m.group(1)), view=int(m.group(2)), shot=int(rest[-1]))
    return dict(prefix=prefix, date=date, family='OTHER', plant=-1, view=-1, shot=int(rest[-1]))


def rankdata(a):
    a = np.asarray(a)
    order = a.argsort()
    r = np.empty(len(a), float)
    r[order] = np.arange(1, len(a) + 1)
    # average ties
    sa = a[order]
    i = 0
    while i < len(sa):
        j = i
        while j + 1 < len(sa) and sa[j + 1] == sa[i]:
            j += 1
        if j > i:
            r[order[i:j + 1]] = (i + j + 2) / 2.0
        i = j + 1
    return r


def auc(x, y):
    """P(x > y) + 0.5 P(x == y)"""
    x = np.asarray(x, float); y = np.asarray(y, float)
    n1, n2 = len(x), len(y)
    if n1 == 0 or n2 == 0:
        return float('nan')
    r = rankdata(np.concatenate([x, y]))
    u = r[:n1].sum() - n1 * (n1 + 1) / 2.0
    return u / (n1 * n2)


def mannwhitney_p(x, y):
    try:
        from scipy.stats import mannwhitneyu
        return float(mannwhitneyu(x, y, alternative='two-sided').pvalue)
    except Exception:
        return float('nan')


def cliffs_delta(x, y):
    a = auc(x, y)
    return 2 * a - 1


def boot_ci(x, y, n=2000, seed=0):
    rng = np.random.default_rng(seed)
    x = np.asarray(x, float); y = np.asarray(y, float)
    vals = []
    for _ in range(n):
        a = rng.choice(x, len(x), replace=True)
        b = rng.choice(y, len(y), replace=True)
        vals.append(auc(a, b))
    return float(np.percentile(vals, 2.5)), float(np.percentile(vals, 97.5))


def main():
    t0 = time.time()
    labels = json.load(open(os.path.join(ROOT, 'labels.json'), encoding='utf-8'))
    names = sorted(f for f in os.listdir(IMG) if f.lower().endswith('.jpg'))
    log = []
    log.append('total images: %d   labels: %d' % (len(names), len(labels)))

    rows = []
    skipped = []
    for i, nm in enumerate(names):
        try:
            img = cv2.imread(os.path.join(IMG, nm))
            if img is None:
                skipped.append((nm, 'imread_none')); continue
            m = metrics(img)
            if m is None:
                skipped.append((nm, 'too_few_plant_pixels')); continue
            r = parse(nm)
            r['name'] = nm
            r['cls'] = labels.get(nm, -1)
            r.update(m)
            rows.append(r)
        except Exception as e:
            skipped.append((nm, repr(e)))
        if (i + 1) % 500 == 0:
            print('  ...%d/%d  %.1fs' % (i + 1, len(names), time.time() - t0)); sys.stdout.flush()

    log.append('processed: %d   skipped: %d' % (len(rows), len(skipped)))
    if skipped:
        log.append('skip reasons: %s' % collections.Counter(s[1] for s in skipped).most_common())

    csvp = os.path.join(OUTDIR, 'color_features.csv')
    cols = ['name', 'prefix', 'date', 'family', 'plant', 'view', 'shot', 'cls',
            'w', 'h', 'plant_ratio', 'green_ratio', 'yellow_ratio', 'brown_ratio',
            'health_index', 'mean_sat', 'mean_val', 'std_val', 'dark_ratio']
    with open(csvp, 'w', newline='', encoding='utf-8-sig') as f:
        wr = csv.DictWriter(f, fieldnames=cols)
        wr.writeheader()
        for r in rows:
            wr.writerow({k: r[k] for k in cols})
    log.append('csv -> %s' % csvp)

    def val(r, k):
        return r[k]

    by_cls = collections.defaultdict(list)
    for r in rows:
        by_cls[r['cls']].append(r)

    log.append('')
    log.append('== per-class metric means ==')
    log.append('%-8s %6s %9s %9s %9s %9s %9s %9s' % ('class', 'n', 'health', 'green', 'yellow', 'brown', 'plantR', 'darkR'))
    for c in sorted(by_cls):
        g = by_cls[c]
        f = lambda k: np.mean([x[k] for x in g])
        log.append('%-8s %6d %9.2f %9.4f %9.4f %9.4f %9.4f %9.4f' % (
            CLASSES.get(c, c), len(g), f('health_index'), f('green_ratio'),
            f('yellow_ratio'), f('brown_ratio'), f('plant_ratio'), f('dark_ratio')))

    log.append('')
    log.append('== per-date metric means ==')
    log.append('%-8s %6s %-8s %9s %9s %9s' % ('date', 'n', 'class', 'health', 'green', 'yellow'))
    for d in sorted(set(r['date'] for r in rows)):
        g = [r for r in rows if r['date'] == d]
        cs = sorted(set(r['cls'] for r in g))
        log.append('%-8s %6d %-8s %9.2f %9.4f %9.4f' % (
            d, len(g), '/'.join(CLASSES.get(c, str(c)) for c in cs),
            np.mean([x['health_index'] for x in g]),
            np.mean([x['green_ratio'] for x in g]),
            np.mean([x['yellow_ratio'] for x in g])))

    METRICS = ['health_index', 'green_ratio', 'yellow_ratio', 'brown_ratio', 'mean_sat', 'mean_val', 'dark_ratio']

    def contrast(x_cls, y_cls, tag):
        x = [r for r in rows if r['cls'] == x_cls]
        y = [r for r in rows if r['cls'] == y_cls]
        log.append('')
        log.append('== %s : %s(n=%d) vs %s(n=%d) ==' % (tag, CLASSES[x_cls], len(x), CLASSES[y_cls], len(y)))
        log.append('%-14s %8s %8s %10s %20s %10s' % ('metric', 'AUC', 'p', 'cliffs_d', 'AUC 95%CI', 'mean_diff'))
        for k in METRICS:
            xv = [r[k] for r in x]; yv = [r[k] for r in y]
            a = auc(xv, yv); p = mannwhitney_p(xv, yv)
            lo, hi = boot_ci(xv, yv, n=1000, seed=1)
            log.append('%-14s %8.4f %8.2e %10.4f %20s %10.4f' % (
                k, a, p, cliffs_delta(xv, yv), '[%.3f,%.3f]' % (lo, hi), np.mean(xv) - np.mean(yv)))
        return x, y

    # ---- Layer 1: Healthy vs Flood ----
    lx, ly = contrast(0, 1, 'LAYER1 healthy vs flood-stress')
    a1 = auc([r['health_index'] for r in lx], [r['health_index'] for r in ly])
    log.append('LAYER1 VERDICT: AUC=%.4f -> %s' % (a1, 'PASS (AUC>0.7)' if a1 > 0.7 else 'FAIL'))

    # ---- Layer 4: Flood vs BBWV2 ----
    fx, fy = contrast(1, 2, 'LAYER4 flood vs BBWV2 (reverse test)')
    a4 = auc([r['health_index'] for r in fx], [r['health_index'] for r in fy])
    log.append('LAYER4 VERDICT: AUC=%.4f -> %s' % (
        a4, 'PASS (poor separability supports symptom/disease split)' if a4 < 0.7 else 'separable (unexpected)'))

    # ---- Layer 2: within-slot repeatability (same leaf or different leaves?) ----
    log.append('')
    log.append('== LAYER2a within-slot variability (is _N the same leaf?) ==')
    slots = collections.defaultdict(list)
    for r in rows:
        if r['family'] == 'PV':
            slots[(r['prefix'], r['plant'], r['view'], r['date'])].append(r)
    within_std = []
    for k, g in slots.items():
        if len(g) >= 3:
            within_std.append(np.std([x['health_index'] for x in g], ddof=1))
    slot_means = [np.mean([x['health_index'] for x in g]) for k, g in slots.items() if len(g) >= 3]
    log.append('  slots(n>=3): %d' % len(within_std))
    log.append('  mean WITHIN-slot std  = %.3f' % np.mean(within_std))
    log.append('  std of BETWEEN-slot   = %.3f' % np.std(slot_means, ddof=1))
    ratio = np.mean(within_std) / max(1e-9, np.std(slot_means, ddof=1))
    log.append('  within/between ratio  = %.3f  -> %s' % (
        ratio, 'mostly SAME leaf repeated (low within)' if ratio < 0.5 else
        ('mostly DIFFERENT leaves (within ~= between)' if ratio > 0.8 else 'mixed')))

    # ---- Layer 2b: same plant-view across two dates ----
    log.append('')
    log.append('== LAYER2b same (prefix,plant,view) across 2 dates ==')
    pv = collections.defaultdict(dict)
    for r in rows:
        if r['family'] == 'PV':
            pv[(r['prefix'], r['plant'], r['view'])].setdefault(r['date'], []).append(r)
    pairs = collections.defaultdict(list)
    for k, dmap in pv.items():
        if len(dmap) >= 2:
            ds = sorted(dmap)
            for i in range(len(ds) - 1):
                a, b = ds[i], ds[i + 1]
                ca = dmap[a][0]['cls']; cb = dmap[b][0]['cls']
                ha = np.mean([x['health_index'] for x in dmap[a]])
                hb = np.mean([x['health_index'] for x in dmap[b]])
                pairs[(ca, cb)].append((a, b, hb - ha))
    log.append('  %-24s %6s %12s %12s' % ('transition', 'n', 'mean_delta', 'std_delta'))
    for k in sorted(pairs):
        v = [p[2] for p in pairs[k]]
        log.append('  %-24s %6d %12.3f %12.3f' % (
            '%s->%s' % (CLASSES[k[0]], CLASSES[k[1]]), len(v), np.mean(v), np.std(v, ddof=1) if len(v) > 1 else 0))
    # paired test for the dominant transition
    big = max(pairs.items(), key=lambda kv: len(kv[1])) if pairs else None
    if big and len(big[1]) >= 5:
        v = np.array([p[2] for p in big[1]], float)
        try:
            from scipy.stats import wilcoxon
            st = wilcoxon(v).pvalue
        except Exception:
            st = float('nan')
        log.append('  dominant %s->%s : n=%d mean=%+.3f wilcoxon p=%.3e' % (
            CLASSES[big[0][0]], CLASSES[big[0][1]], len(v), v.mean(), st))

    # ---- Layer 3 proxy: signal to noise ----
    log.append('')
    log.append('== LAYER3 signal/noise proxy ==')
    noise = np.mean(within_std) if within_std else float('nan')
    signal = abs(np.mean([r['health_index'] for r in lx]) - np.mean([r['health_index'] for r in ly]))
    log.append('  noise (within-slot std)      = %.3f' % noise)
    log.append('  signal (healthy-flood gap)   = %.3f' % signal)
    log.append('  SNR = %.2f -> %s' % (signal / max(1e-9, noise), 'PASS (>=2)' if signal / max(1e-9, noise) >= 2 else 'FAIL'))

    log.append('')
    log.append('elapsed: %.1f s' % (time.time() - t0))
    rep = os.path.join(OUTDIR, 'validation_report.txt')
    open(rep, 'w', encoding='utf-8').write('\n'.join(log))
    print('\n'.join(log))


if __name__ == '__main__':
    try:
        main()
    except Exception:
        traceback.print_exc()
        sys.exit(1)
