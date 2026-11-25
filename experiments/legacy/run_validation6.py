# -*- coding: utf-8 -*-
"""
GreenThumb AI - v6: can the ILLUMINANT be recovered from specular highlights on the leaf?
If yes -> cross-session colour normalisation becomes possible, and a real QC gate becomes implementable.

Classic colour-constancy cue: a specular highlight reflects the light source directly,
so its chromaticity approximates the illuminant chromaticity.
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


def feats(img):
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
    V = hsv[..., 2].astype(np.float32)
    leaf = (exg > 10.0) & ~((Ls > 93.0) & (chroma < 6.0))
    leaf = cv2.morphologyEx(leaf.astype(np.uint8), cv2.MORPH_OPEN, np.ones((3, 3), np.uint8)).astype(bool)
    n = int(leaf.sum())
    if n < MIN_LEAF:
        return None
    lv = Ls[leaf]; la = A[leaf]; lb = Bb[leaf]; ls = S[leaf]
    a_mean = float(la.mean()); b_mean = float(lb.mean())

    # --- illuminant probe A: specular highlights (bright AND desaturated) ---
    vthr = np.percentile(lv, 97)
    idx = np.flatnonzero(lv >= vthr)
    if len(idx) >= 20:
        sub = ls[idx]
        keep = idx[sub <= np.percentile(sub, 60)]
        if len(keep) < 20:
            keep = idx
    else:
        keep = idx
    ill_a = float(np.median(la[keep])); ill_b = float(np.median(lb[keep])); ill_L = float(np.median(lv[keep]))
    hl_frac = float(len(keep)) / n

    # --- probe B: gray-world over the leaf ---
    gw_a = a_mean; gw_b = b_mean

    # --- probe C: darkest leaf pixels (self-shadow, ambient-lit) ---
    dthr = np.percentile(lv, 5)
    dk = lv <= dthr
    dark_a = float(np.median(la[dk])); dark_b = float(np.median(lb[dk]))

    return dict(a_mean=a_mean, b_mean=b_mean, L_mean=float(lv.mean()),
                chlorophyll=float(-a_mean),
                ill_a=ill_a, ill_b=ill_b, ill_L=ill_L, hl_frac=hl_frac,
                dark_a=dark_a, dark_b=dark_b, sat_leaf=float(ls.mean()))


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
        m = feats(img)
        if m is None:
            continue
        r = parse(nm); r['name'] = nm; r['cls'] = labels.get(nm, -1); r.update(m)
        rows.append(r)
        if (i + 1) % 1500 == 0:
            print('  ...%d/%d %.1fs' % (i + 1, len(names), time.time() - t0)); sys.stdout.flush()
    print('images=%d' % len(rows))

    dates = sorted(set(r['date'] for r in rows))
    L = []; A_ = L.append

    def sep(key):
        means = np.array([np.mean([r[key] for r in rows if r['date'] == d]) for d in dates])
        within = np.mean([np.std([r[key] for r in rows if r['date'] == d]) for d in dates])
        return np.std(means, ddof=1), within, np.std(means, ddof=1) / max(1e-9, within), means

    A_('== 1. WHICH PROBE RECOVERS THE ILLUMINANT? (separability = between-session sd / within-session sd) ==')
    A_('%-12s %12s %12s %12s %s' % ('probe', 'between_sd', 'within_sd', 'separability', 'usable?'))
    probes = ['ill_a', 'ill_b', 'ill_L', 'dark_a', 'dark_b', 'a_mean', 'b_mean', 'L_mean', 'sat_leaf']
    usable = []
    for k in probes:
        bs, ws, s, _ = sep(k)
        ok = s > 1.0
        if ok and k.startswith('ill'):
            usable.append(k)
        A_('%-12s %12.3f %12.3f %12.3f %s' % (k, bs, ws, s, 'YES' if ok else 'no'))
    A_('   session means ill_a: %s' % ' '.join('%6.2f' % x for x in sep('ill_a')[3]))
    A_('   session means ill_b: %s' % ' '.join('%6.2f' % x for x in sep('ill_b')[3]))
    A_('   session means dark_a: %s' % ' '.join('%6.2f' % x for x in sep('dark_a')[3]))

    # normalisation using the best usable probe (prefer ill_a / ill_b)
    GA = float(np.median([r['ill_a'] for r in rows]))
    GB = float(np.median([r['ill_b'] for r in rows]))
    GA_d = float(np.median([r['dark_a'] for r in rows]))
    GB_d = float(np.median([r['dark_b'] for r in rows]))
    for r in rows:
        r['a_ill'] = r['a_mean'] - (r['ill_a'] - GA)
        r['b_ill'] = r['b_mean'] - (r['ill_b'] - GB)
        r['a_dark'] = r['a_mean'] - (r['dark_a'] - GA_d)
        r['b_dark'] = r['b_mean'] - (r['dark_b'] - GB_d)
        r['chloro_ill'] = -r['a_ill']
        r['chloro_dark'] = -r['a_dark']

    def vardecomp(key):
        pv = [r for r in rows if r['family'] == 'PV']
        by_s = collections.defaultdict(list)
        for r in pv:
            by_s[r['date']].append(r)
        sm = {d: np.mean([r[key] for r in g]) for d, g in by_s.items()}
        v_sess = float(np.var(list(sm.values())))
        rp, ws = [], []
        for d, g in by_s.items():
            bpp = collections.defaultdict(list); bpv = collections.defaultdict(list)
            for r in g:
                bpp[(r['prefix'], r['plant'])].append(r)
                bpv[(r['prefix'], r['plant'], r['view'])].append(r)
            for k, gg in bpp.items():
                rp.append(np.mean([r[key] for r in gg]) - sm[d])
            for k, gg in bpv.items():
                if len(gg) >= 3:
                    ws.append(np.std([r[key] for r in gg], ddof=1))
        v_plant = max(float(np.var(rp)), 0)
        v_leaf = float(np.mean(ws) ** 2)
        tot = v_sess + v_plant + v_leaf
        return 100 * v_sess / tot, 100 * v_leaf / tot

    A_('')
    A_('== 2. DOES ILLUMINANT NORMALISATION REMOVE THE SESSION EFFECT? ==')
    A_('%-14s %14s %14s' % ('metric', 'session var %', 'leaf var %'))
    for k in ('a_mean', 'a_ill', 'a_dark', 'b_mean', 'b_ill', 'chlorophyll', 'chloro_ill'):
        s, lf = vardecomp(k)
        A_('%-14s %13.1f%% %13.1f%%' % (k, s, lf))

    # paired test
    pvm = collections.defaultdict(dict)
    for r in rows:
        if r['family'] == 'PV':
            pvm[(r['prefix'], r['plant'], r['view'])].setdefault(r['date'], []).append(r)
    keys = [k for k, dm in pvm.items() if '230418' in dm and '230608' in dm]

    A_('')
    A_('== 3. PAIRED SIGNAL BEFORE vs AFTER ILLUMINANT NORMALISATION (n=%d) ==' % len(keys))
    A_('%-14s %12s %12s %10s %12s' % ('metric', 'mean_delta', 'sd_delta', 'cohen_d', 'same_sign'))
    for k in ('chlorophyll', 'chloro_ill', 'chloro_dark', 'a_mean', 'a_ill', 'b_mean', 'b_ill'):
        d = np.array([np.mean([x[k] for x in pvm[kk]['230608']]) - np.mean([x[k] for x in pvm[kk]['230418']]) for kk in keys])
        mu, sd = float(d.mean()), float(d.std(ddof=1))
        ss = max((d > 0).mean(), (d < 0).mean())
        A_('%-14s %+12.4f %12.4f %10.2f %11.1f%%' % (k, mu, sd, mu / sd, 100 * ss))

    # QC gate
    A_('')
    A_('== 4. QC GATE: can an incomparable session be flagged? ==')
    for k in ('ill_a', 'ill_b', 'a_mean', 'L_mean'):
        _, _, s, means = sep(k)
        med = np.median([r[k] for r in rows])
        mad = np.median(np.abs(np.array([r[k] for r in rows]) - med)) * 1.4826
        zs = [(d, abs(m - med) / max(1e-9, mad)) for d, m in zip(dates, means)]
        flagged = [d for d, z in zs if z > 2]
        A_('%-10s separability=%.2f   session |z|: %s   flagged: %s' % (
            k, s, ' '.join('%s=%.1f' % (d, z) for d, z in zs), flagged if flagged else 'none'))

    A_('')
    A_('== 5. VERDICT ==')
    A_('   best illuminant probe separability: ill_a=%.2f  ill_b=%.2f  dark_a=%.2f' % (
        sep('ill_a')[2], sep('ill_b')[2], sep('dark_a')[2]))
    A_('   a* session variance: raw %.1f%% -> ill-normalised %.1f%% -> dark-normalised %.1f%%' % (
        vardecomp('a_mean')[0], vardecomp('a_ill')[0], vardecomp('a_dark')[0]))

    cols = list(rows[0].keys())
    with open(os.path.join(OUTDIR, 'color_features_v6.csv'), 'w', newline='', encoding='utf-8-sig') as f:
        wr = csv.DictWriter(f, fieldnames=cols); wr.writeheader()
        for r in rows:
            wr.writerow(r)

    A_('')
    A_('elapsed %.1fs' % (time.time() - t0))
    txt = '\n'.join(L)
    open(os.path.join(OUTDIR, 'validation_report_v6.txt'), 'w', encoding='utf-8').write(txt)
    print(txt)


if __name__ == '__main__':
    try:
        main()
    except Exception:
        traceback.print_exc(); sys.exit(1)
