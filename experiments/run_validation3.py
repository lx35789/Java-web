# -*- coding: utf-8 -*-
"""
GreenThumb AI - v3: FEASIBILITY of same-plant longitudinal tracking.

Answers, with measurements instead of arguments:
  1. Can the image background serve as an illumination reference for cross-session normalisation?
  2. How much of the cross-session noise can that normalisation remove?
  3. Under each capture protocol, what is P(correct direction) for a single before/after comparison?
  4. How many photos / how much noise reduction is needed to reach 90/95/99%?
"""
import os, sys, json, csv, time, re, collections, traceback
import numpy as np
import cv2

ROOT = r'C:\Users\lenovo\greenthumb\experiments\data\Chili\pepper_chili_dataset'
IMG = os.path.join(ROOT, 'images')
OUTDIR = os.environ.get('GT_OUT') or r'C:\Users\lenovo\greenthumb\experiments\results'
os.makedirs(OUTDIR, exist_ok=True)
CLASSES = {0: 'Healthy', 1: 'Flood', 2: 'BBWV2'}
MAX_SIDE = 512
MIN_LEAF = 500

try:
    from scipy.stats import norm
    PHI = lambda z: float(norm.cdf(z))
    PHI_INV = lambda p: float(norm.ppf(p))
except Exception:
    import math
    PHI = lambda z: 0.5 * (1 + math.erf(z / math.sqrt(2)))
    PHI_INV = lambda p: 1.6448536269514722 if abs(p - .95) < 1e-9 else (
        1.2815515655446004 if abs(p - .90) < 1e-9 else 2.3263478740408408)


def features(img):
    h, w = img.shape[:2]
    sc = max(h, w) / float(MAX_SIDE)
    if sc > 1:
        img = cv2.resize(img, (int(round(w / sc)), int(round(h / sc))), interpolation=cv2.INTER_AREA)
    f = img.astype(np.float32)
    B, G, R = f[..., 0], f[..., 1], f[..., 2]
    exg = 2.0 * G - R - B
    lab = cv2.cvtColor(img, cv2.COLOR_BGR2LAB).astype(np.float32)
    Ls = lab[..., 0] * (100.0 / 255.0)
    A = lab[..., 1] - 128.0
    Bb = lab[..., 2] - 128.0
    chroma = np.sqrt(A * A + Bb * Bb)
    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
    S = hsv[..., 1].astype(np.float32)
    H = hsv[..., 0]
    V = hsv[..., 2]
    leaf = (exg > 10.0) & ~((Ls > 93.0) & (chroma < 6.0))
    leaf = cv2.morphologyEx(leaf.astype(np.uint8), cv2.MORPH_OPEN, np.ones((3, 3), np.uint8)).astype(bool)
    n = int(leaf.sum())
    if n < MIN_LEAF:
        return None
    H2, W2 = leaf.shape
    m = int(round(min(H2, W2) * 0.05)) + 1
    ring = np.zeros_like(leaf)
    ring[:m, :] = True; ring[-m:, :] = True; ring[:, :m] = True; ring[:, -m:] = True
    nonleaf = ring & (~leaf)
    sel = nonleaf if int(nonleaf.sum()) >= 200 else ring
    bg_L = float(np.median(Ls[sel])); bg_a = float(np.median(A[sel])); bg_b = float(np.median(Bb[sel]))
    bg_frac = float(nonleaf.sum()) / float(ring.sum())
    bg_L_std = float(np.std(Ls[sel]))

    ex = exg[leaf]; a = A[leaf]; b = Bb[leaf]; l = Ls[leaf]; c = chroma[leaf]; s = S[leaf]
    Hl = H[leaf]; Sl = S[leaf].astype(np.int32); Vl = V[leaf].astype(np.int32)
    g = (Hl >= 35) & (Hl < 85) & (Sl >= 40) & (Vl >= 40)
    y = (Hl >= 20) & (Hl < 35) & (Sl >= 60) & (Vl >= 60)
    br = (Hl < 20) & (Sl >= 60) & (Vl >= 20) & (Vl < 160)
    gr = float(g.mean()); yr = float(y.mean()); brr = float(br.mean())
    hi = max(0.0, min(100.0, gr * 100.0 - yr * 80.0 - brr * 120.0))
    return dict(leaf_frac=n / float(H2 * W2),
                exg_mean=float(ex.mean()), a_mean=float(a.mean()), b_mean=float(b.mean()),
                L_mean=float(l.mean()), chroma_mean=float(c.mean()), sat_mean=float(s.mean()),
                green_frac=float((a < -8).mean()), yellow_frac=float((b > 20).mean()),
                brown_frac=float(((l < 35) & (b > 5)).mean()), dark_frac=float((l < 25).mean()),
                chlorophyll=float(-a.mean()), yellowness=float(b.mean()),
                green_ratio=gr, yellow_ratio=yr, brown_ratio=brr, health_index=hi,
                bg_L=bg_L, bg_a=bg_a, bg_b=bg_b, bg_frac=bg_frac, bg_L_std=bg_L_std)


def parse(name):
    stem = name[:-4]; p = stem.split('_'); rest = p[2:]
    if 'DSC' in rest[0]:
        return dict(prefix=p[0], date=p[1], family='DSC', plant=-1, view=-1)
    mm = re.match(r'^(\d+)-(\d+)$', rest[0])
    if mm:
        return dict(prefix=p[0], date=p[1], family='PV', plant=int(mm.group(1)), view=int(mm.group(2)))
    return dict(prefix=p[0], date=p[1], family='OTHER', plant=-1, view=-1)


def main():
    t0 = time.time()
    labels = json.load(open(os.path.join(ROOT, 'labels.json'), encoding='utf-8'))
    names = sorted(f for f in os.listdir(IMG) if f.lower().endswith('.jpg'))
    rows = []
    for i, nm in enumerate(names):
        img = cv2.imread(os.path.join(IMG, nm))
        if img is None:
            continue
        m = features(img)
        if m is None:
            continue
        r = parse(nm); r['name'] = nm; r['cls'] = labels.get(nm, -1); r.update(m)
        rows.append(r)
        if (i + 1) % 1500 == 0:
            print('  ...%d/%d %.1fs' % (i + 1, len(names), time.time() - t0)); sys.stdout.flush()

    log = []
    A_ = log.append
    A_('n=%d' % len(rows))

    # background reference: global medians
    BGA = float(np.median([r['bg_a'] for r in rows]))
    BGB = float(np.median([r['bg_b'] for r in rows]))
    BGL = float(np.median([r['bg_L'] for r in rows]))
    for r in rows:
        r['a_adj'] = r['a_mean'] - (r['bg_a'] - BGA)
        r['b_adj'] = r['b_mean'] - (r['bg_b'] - BGB)
        r['L_adj'] = r['L_mean'] - (r['bg_L'] - BGL)
        r['chloro_adj'] = -r['a_adj']
        r['bg_off_a'] = r['bg_a'] - BGA

    A_('')
    A_('== 1. IS THE BACKGROUND A USABLE ILLUMINATION REFERENCE? ==')
    A_('global bg reference: L*=%.2f  a*=%.2f  b*=%.2f' % (BGL, BGA, BGB))
    A_('%-9s %-8s %6s %9s %9s %9s %9s %8s' % ('date', 'class', 'n', 'bg_L', 'sd(bg_L)', 'bg_a', 'bg_b', 'bg_frac'))
    for d in sorted(set(r['date'] for r in rows)):
        g = [r for r in rows if r['date'] == d]
        A_('%-9s %-8s %6d %9.2f %9.3f %9.3f %9.3f %8.3f' % (
            d, CLASSES[sorted(set(r['cls'] for r in g))[0]], len(g),
            np.mean([r['bg_L'] for r in g]), np.mean([r['bg_L_std'] for r in g]),
            np.mean([r['bg_a'] for r in g]), np.mean([r['bg_b'] for r in g]),
            np.mean([r['bg_frac'] for r in g])))
    within = []
    for d in sorted(set(r['date'] for r in rows)):
        v = [r['bg_a'] for r in rows if r['date'] == d]
        within.append(np.std(v))
    means = [np.mean([r['bg_a'] for r in rows if r['date'] == d]) for d in sorted(set(r['date'] for r in rows))]
    A_('  bg_a  within-session sd (mean) = %.3f' % np.mean(within))
    A_('  bg_a  between-session sd      = %.3f' % np.std(means, ddof=1))
    A_('  ratio between/within          = %.2f  -> %s' % (
        np.std(means, ddof=1) / max(1e-9, np.mean(within)),
        'USABLE as reference (session shift >> within-session noise)' if np.std(means, ddof=1) / max(1e-9, np.mean(within)) > 2 else 'NOT usable'))

    # ---- variance decomposition helper ----
    def vardecomp(key, subset=None):
        pv = [r for r in rows if r['family'] == 'PV' and (subset is None or subset(r))]
        by_s = collections.defaultdict(list)
        for r in pv:
            by_s[r['date']].append(r)
        sm = {d: np.mean([r[key] for r in g]) for d, g in by_s.items()}
        v_sess = float(np.var(list(sm.values())))
        rp, rs, ws = [], [], []
        for d, g in by_s.items():
            bpp = collections.defaultdict(list); bpv = collections.defaultdict(list)
            for r in g:
                bpp[(r['prefix'], r['plant'])].append(r)
                bpv[(r['prefix'], r['plant'], r['view'])].append(r)
            for k, gg in bpp.items():
                rp.append(np.mean([r[key] for r in gg]) - sm[d])
            for k, gg in bpv.items():
                rs.append(np.mean([r[key] for r in gg]) - sm[d])
                if len(gg) >= 3:
                    ws.append(np.std([r[key] for r in gg], ddof=1))
        v_plant = max(float(np.var(rp)), 0)
        v_slot = max(float(np.var(rs)) - v_plant, 0)
        v_leaf = float(np.mean(ws) ** 2)
        tot = v_sess + v_plant + v_slot + v_leaf
        return dict(sess=v_sess, plant=v_plant, slot=v_slot, leaf=v_leaf, tot=tot,
                    sd_sess=np.sqrt(v_sess), sd_plant=np.sqrt(v_plant),
                    sd_slot=np.sqrt(v_slot), sd_leaf=np.sqrt(v_leaf),
                    pct=[100 * v_sess / tot, 100 * v_plant / tot, 100 * v_slot / tot, 100 * v_leaf / tot])

    A_('')
    A_('== 2. DOES BACKGROUND NORMALISATION REMOVE THE SESSION EFFECT? ==')
    A_('%-14s %12s %12s %12s %12s' % ('metric', 'raw sess%', 'adj sess%', 'raw sd_sess', 'adj sd_sess'))
    for k, ka in (('a_mean', 'a_adj'), ('b_mean', 'b_adj'), ('L_mean', 'L_adj'), ('health_index', 'health_index')):
        d0 = vardecomp(k); d1 = vardecomp(ka)
        A_('%-14s %11.1f%% %11.1f%% %12.3f %12.3f' % (k, d0['pct'][0], d1['pct'][0], d0['sd_sess'], d1['sd_sess']))

    # ---- paired ----
    pvm = collections.defaultdict(dict)
    for r in rows:
        if r['family'] == 'PV':
            pvm[(r['prefix'], r['plant'], r['view'])].setdefault(r['date'], []).append(r)
    keys = [k for k, dm in pvm.items() if '230418' in dm and '230608' in dm]
    A_('')
    A_('== 3. THE REAL SIGNAL: same plant+view, 230418(Flood) -> 230608(Healthy), n=%d ==' % len(keys))
    A_('%-14s %12s %12s %10s %12s %10s' % ('metric', 'mean_delta', 'sd_delta', 'cohen_d', 'Phi(d)', 'pairs_same_sign'))
    signal = {}
    for k in ('chlorophyll', 'chloro_adj', 'a_mean', 'health_index', 'exg_mean', 'green_ratio', 'yellow_ratio', 'L_mean', 'b_mean'):
        d = np.array([np.mean([r[k] for r in pvm[kk]['230608']]) - np.mean([r[k] for r in pvm[kk]['230418']]) for kk in keys])
        d = d[np.isfinite(d)]
        mu, sd = float(d.mean()), float(d.std(ddof=1))
        sign = float(max((d > 0).mean(), (d < 0).mean()))
        A_('%-14s %12.4f %12.4f %10.2f %11.1f%% %10.1f%%' % (k, mu, sd, mu / sd, 100 * PHI(abs(mu) / sd), 100 * sign))
        signal[k] = (abs(mu), sd)

    # ---- feasibility ----
    A_('')
    A_('== 4. FEASIBILITY: P(correct direction) FOR A SINGLE BEFORE/AFTER COMPARISON ==')
    A_('   model: observed delta = true change + noise;  accuracy = Phi(true_change / sigma)')
    A_('   true_change taken as the measured paired mean delta above.')
    A_('%-14s %10s %10s %10s %10s %10s %10s' % ('metric', 'sigma_raw', 'same_leaf', 'leaf/1.4', 'leaf/1.7', 'sigma=2.0', 'sigma=1.0'))
    for k, lab in (('chlorophyll', 'chlorophyll'), ('chloro_adj', 'chloro_adj')):
        v = vardecomp(k)
        dtrue = signal[k][0]
        s_leaf = v['sd_leaf']
        s_raw = np.sqrt(v['tot'])
        row = [s_raw, s_leaf, s_leaf / 1.4, s_leaf / 1.7, 2.0, 1.0]
        A_('%-14s ' % lab + ' '.join('%9.1f%%' % (100 * PHI(dtrue / max(1e-9, x))) for x in row))
        A_('%-14s ' % '(sigma)' + ' '.join('%9.2f ' % x for x in row))

    A_('')
    A_('== 5. HOW SMALL MUST sigma BE? (target direction accuracy) ==')
    A_('%-14s %10s %10s %10s %10s' % ('metric', 'sigma_90%', 'sigma_95%', 'sigma_99%', 'true_change'))
    for k in ('chlorophyll', 'chloro_adj', 'health_index', 'exg_mean'):
        dtrue = signal[k][0]
        A_('%-14s %10.3f %10.3f %10.3f %10.3f' % (
            k, dtrue / PHI_INV(.90), dtrue / PHI_INV(.95), dtrue / PHI_INV(.99), dtrue))

    A_('')
    A_('== 6. HOW MANY PHOTOS PER SESSION? (averaging n shots -> sigma/sqrt(n)) ==')
    A_('%-14s %8s %8s %8s %8s %8s' % ('metric', 'n=1', 'n=3', 'n=5', 'n=9', 'n=20'))
    for k in ('chlorophyll', 'chloro_adj'):
        v = vardecomp(k); dtrue = signal[k][0]; s = v['sd_leaf']
        A_('%-14s ' % k + ' '.join('%7.1f%%' % (100 * PHI(dtrue / (s / np.sqrt(n)))) for n in (1, 3, 5, 9, 20)))
        A_('%-14s ' % ('(sigma@n)') + ' '.join('%7.2f ' % (s / np.sqrt(n)) for n in (1, 3, 5, 9, 20)))

    A_('')
    A_('== 7. PAIRED vs CROSS-SECTIONAL: signal-to-noise gain ==')
    for k in ('chlorophyll', 'chloro_adj', 'health_index'):
        v = vardecomp(k)
        xsec = signal[k][0] / max(1e-9, np.sqrt(v['tot']))          # true change vs total population noise
        pair = signal[k][0] / max(1e-9, signal[k][1])               # true change vs paired-delta noise
        A_('  %-14s  cross-sectional d=%.2f   paired d=%.2f   gain=%.2fx' % (k, xsec, pair, pair / max(1e-9, xsec)))

    cols = list(rows[0].keys())
    with open(os.path.join(OUTDIR, 'color_features_v3.csv'), 'w', newline='', encoding='utf-8-sig') as f:
        wr = csv.DictWriter(f, fieldnames=cols); wr.writeheader()
        for r in rows:
            wr.writerow(r)

    A_('')
    A_('elapsed %.1fs' % (time.time() - t0))
    txt = '\n'.join(log)
    open(os.path.join(OUTDIR, 'validation_report_v3.txt'), 'w', encoding='utf-8').write(txt)
    print(txt)


if __name__ == '__main__':
    try:
        main()
    except Exception:
        traceback.print_exc(); sys.exit(1)
