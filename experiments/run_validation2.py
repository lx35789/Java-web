# -*- coding: utf-8 -*-
"""
GreenThumb AI - v2: illumination-robust features + variance decomposition + confound diagnostics
Dataset: Chili Pepper BBWV2 & Flooding (Zenodo 17901489), 4102 real images
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
    ex = exg[leaf]; a = A[leaf]; b = Bb[leaf]; l = Ls[leaf]; c = chroma[leaf]; s = S[leaf]
    Hl = H[leaf]; Sl = S[leaf].astype(np.int32); Vl = V[leaf].astype(np.int32)
    g = (Hl >= 35) & (Hl < 85) & (Sl >= 40) & (Vl >= 40)
    y = (Hl >= 20) & (Hl < 35) & (Sl >= 60) & (Vl >= 60)
    br = (Hl < 20) & (Sl >= 60) & (Vl >= 20) & (Vl < 160)
    gr = float(g.mean()); yr = float(y.mean()); brr = float(br.mean())
    hi = max(0.0, min(100.0, gr * 100.0 - yr * 80.0 - brr * 120.0))
    return dict(leaf_frac=n / float(img.shape[0] * img.shape[1]),
                exg_mean=float(ex.mean()), a_mean=float(a.mean()), b_mean=float(b.mean()),
                L_mean=float(l.mean()), chroma_mean=float(c.mean()), sat_mean=float(s.mean()),
                green_frac=float((a < -8).mean()), yellow_frac=float((b > 20).mean()),
                brown_frac=float(((l < 35) & (b > 5)).mean()), dark_frac=float((l < 25).mean()),
                chlorophyll=float(-a.mean()), yellowness=float(b.mean()),
                green_ratio=gr, yellow_ratio=yr, brown_ratio=brr, health_index=hi)


def parse(name):
    stem = name[:-4]
    p = stem.split('_')
    rest = p[2:]
    if 'DSC' in rest[0]:
        return dict(prefix=p[0], date=p[1], family='DSC', plant=-1, view=-1, shot=int(rest[-1]))
    m = re.match(r'^(\d+)-(\d+)$', rest[0])
    if m:
        return dict(prefix=p[0], date=p[1], family='PV', plant=int(m.group(1)), view=int(m.group(2)), shot=int(rest[-1]))
    return dict(prefix=p[0], date=p[1], family='OTHER', plant=-1, view=-1, shot=int(rest[-1]))


def rankdata(a):
    a = np.asarray(a, float)
    order = a.argsort(kind='mergesort')
    r = np.empty(len(a), float)
    r[order] = np.arange(1, len(a) + 1)
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
    x = np.asarray(x, float); y = np.asarray(y, float)
    n1, n2 = len(x), len(y)
    if n1 == 0 or n2 == 0:
        return float('nan')
    r = rankdata(np.concatenate([x, y]))
    return (r[:n1].sum() - n1 * (n1 + 1) / 2.0) / (n1 * n2)


def mwu_p(x, y):
    try:
        from scipy.stats import mannwhitneyu
        return float(mannwhitneyu(x, y, alternative='two-sided').pvalue)
    except Exception:
        return float('nan')


def wilcoxon_p(v):
    try:
        from scipy.stats import wilcoxon
        v = np.asarray(v, float)
        v = v[v != 0]
        if len(v) < 5:
            return float('nan')
        return float(wilcoxon(v).pvalue)
    except Exception:
        return float('nan')


def boot_ci(x, y, n=1000, seed=1):
    rng = np.random.default_rng(seed)
    x = np.asarray(x, float); y = np.asarray(y, float)
    v = [auc(rng.choice(x, len(x), True), rng.choice(y, len(y), True)) for _ in range(n)]
    return float(np.percentile(v, 2.5)), float(np.percentile(v, 97.5))


def cv_lda_auc(X, y, groups, K=5, seed=0):
    ug = np.unique(groups)
    rng = np.random.default_rng(seed); rng.shuffle(ug)
    folds = np.array_split(ug, K)
    sc = np.full(len(y), np.nan)
    for fset in folds:
        te = np.isin(groups, fset); tr = ~te
        if te.sum() == 0 or len(set(y[tr])) < 2:
            continue
        Xtr, ytr = X[tr], y[tr]
        mu0 = Xtr[ytr == 0].mean(0); mu1 = Xtr[ytr == 1].mean(0)
        Xc = Xtr - np.where((ytr == 1)[:, None], mu1, mu0)
        S = (Xc.T @ Xc) / max(1, tr.sum() - 2) + 1e-2 * np.eye(X.shape[1])
        w = np.linalg.solve(S, mu1 - mu0)
        sc[te] = X[te] @ w
    m = ~np.isnan(sc)
    return auc(sc[m & (y == 1)], sc[m & (y == 0)])


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
        if (i + 1) % 1000 == 0:
            print('  ...%d/%d %.1fs' % (i + 1, len(names), time.time() - t0)); sys.stdout.flush()

    cols = ['name', 'prefix', 'date', 'family', 'plant', 'view', 'shot', 'cls'] + \
           [k for k in rows[0] if k not in ('name', 'prefix', 'date', 'family', 'plant', 'view', 'shot', 'cls')]
    with open(os.path.join(OUTDIR, 'color_features_v2.csv'), 'w', newline='', encoding='utf-8-sig') as f:
        wr = csv.DictWriter(f, fieldnames=cols); wr.writeheader()
        for r in rows:
            wr.writerow({k: r[k] for k in cols})

    FEATS = ['a_mean', 'b_mean', 'L_mean', 'chroma_mean', 'exg_mean', 'sat_mean', 'chlorophyll',
             'yellowness', 'green_frac', 'yellow_frac', 'brown_frac', 'dark_frac',
             'green_ratio', 'yellow_ratio', 'brown_ratio', 'health_index', 'leaf_frac']
    log = []
    A = log.append

    def mean_of(rs, k):
        return float(np.mean([r[k] for r in rs]))

    A('n=%d  features=%d  elapsed=%.1fs' % (len(rows), len(FEATS), time.time() - t0))
    A('')
    A('== 1. per-class feature means ==')
    A('%-14s %10s %10s %10s' % ('feature', 'Healthy', 'Flood', 'BBWV2'))
    for k in FEATS:
        A('%-14s %10.4f %10.4f %10.4f' % (k,
          mean_of([r for r in rows if r['cls'] == 0], k),
          mean_of([r for r in rows if r['cls'] == 1], k),
          mean_of([r for r in rows if r['cls'] == 2], k)))

    A('')
    A('== 2. per-session (date) feature means  [KEY: class is 1:1 with session] ==')
    A('%-9s %-8s %6s %9s %9s %9s %9s %9s' % ('date', 'class', 'n', 'health', 'yellow_frac', 'b_mean', 'a_mean', 'L_mean'))
    for d in sorted(set(r['date'] for r in rows)):
        g = [r for r in rows if r['date'] == d]
        cs = sorted(set(r['cls'] for r in g))
        A('%-9s %-8s %6d %9.2f %9.4f %9.3f %9.3f %9.2f' % (
            d, '/'.join(CLASSES[c] for c in cs), len(g), mean_of(g, 'health_index'),
            mean_of(g, 'yellow_frac'), mean_of(g, 'b_mean'), mean_of(g, 'a_mean'), mean_of(g, 'L_mean')))

    A('')
    A('== 3. LAYER1 single-feature discrimination: Healthy vs Flood ==')
    hx = [r for r in rows if r['cls'] == 0]
    fl = [r for r in rows if r['cls'] == 1]
    A('%-14s %8s %10s %22s' % ('feature', 'AUC', 'p', 'AUC 95%CI'))
    best = []
    for k in FEATS:
        xv = [r[k] for r in hx]; yv = [r[k] for r in fl]
        a = auc(xv, yv); p = mwu_p(xv, yv); lo, hi = boot_ci(xv, yv)
        A('%-14s %8.4f %10.2e %22s' % (k, a, p, '[%.3f,%.3f]' % (lo, hi)))
        best.append((max(a, 1 - a), k, a))
    best.sort(reverse=True)
    A('  BEST |AUC|: %s  AUC=%.4f (raw %.4f)' % (best[0][1], best[0][0], best[0][2]))

    A('')
    A('== 4. LAYER4 single-feature: Flood vs BBWV2 ==')
    bv = [r for r in rows if r['cls'] == 2]
    A('%-14s %8s %10s' % ('feature', 'AUC', 'p'))
    for k in FEATS:
        A('%-14s %8.4f %10.2e' % (k, auc([r[k] for r in fl], [r[k] for r in bv]), mwu_p([r[k] for r in fl], [r[k] for r in bv])))

    A('')
    A('== 5. SESSION-PAIRWISE AUC (does any flood session separate from any healthy session?) ==')
    fdates = sorted(set(r['date'] for r in fl))
    hdates = sorted(set(r['date'] for r in hx))
    A('%-14s %-10s %-10s %8s %8s' % ('metric', 'flood', 'healthy', 'AUC', 'p'))
    for fd in fdates:
        for hd in hdates:
            fg = [r for r in fl if r['date'] == fd]
            hg = [r for r in hx if r['date'] == hd]
            for k in ('health_index', 'yellow_frac', 'b_mean'):
                a = auc([r[k] for r in hg], [r[k] for r in fg])
                A('%-14s %-10s %-10s %8.4f %8.2e' % (k, fd, hd, a, mwu_p([r[k] for r in hg], [r[k] for r in fg])))

    A('')
    A('== 6. VARIANCE DECOMPOSITION (PV family, metric = health_index) ==')
    for MET in ('health_index', 'yellow_frac', 'b_mean', 'a_mean'):
        pv = [r for r in rows if r['family'] == 'PV']
        by_s = collections.defaultdict(list)
        for r in pv:
            by_s[r['date']].append(r)
        sess_means = {d: np.mean([r[MET] for r in g]) for d, g in by_s.items()}
        v_sess = float(np.var(list(sess_means.values())))
        # plant within session
        resid_plant = []
        for d, g in by_s.items():
            bp = collections.defaultdict(list)
            for r in g:
                bp[(r['prefix'], r['plant'])].append(r)
            for k, gg in bp.items():
                resid_plant.append(np.mean([r[MET] for r in gg]) - sess_means[d])
        v_plant = float(np.var(resid_plant))
        # slot within plant
        resid_slot = []
        within_slot = []
        for d, g in by_s.items():
            bp = collections.defaultdict(list)
            for r in g:
                bp[(r['prefix'], r['plant'], r['view'])].append(r)
            for k, gg in bp.items():
                resid_slot.append(np.mean([r[MET] for r in gg]) - sess_means[d])
                if len(gg) >= 3:
                    within_slot.append(np.std([r[MET] for r in gg], ddof=1))
        v_slot = float(np.var(resid_slot)) - v_plant
        v_leaf = float(np.mean(within_slot) ** 2)
        tot = v_sess + max(v_plant, 0) + max(v_slot, 0) + v_leaf
        A('  metric=%s  total_var=%.3f' % (MET, tot))
        A('    session(date)  %8.3f  %5.1f%%' % (v_sess, 100 * v_sess / tot))
        A('    plant          %8.3f  %5.1f%%' % (max(v_plant, 0), 100 * max(v_plant, 0) / tot))
        A('    plant-view     %8.3f  %5.1f%%' % (max(v_slot, 0), 100 * max(v_slot, 0) / tot))
        A('    leaf (within)  %8.3f  %5.1f%%' % (v_leaf, 100 * v_leaf / tot))
        A('    -> session sd=%.2f  leaf sd=%.2f  ratio=%.2f' % (
            np.sqrt(v_sess), np.sqrt(v_leaf), np.sqrt(v_leaf) / max(1e-9, np.sqrt(v_sess))))

    A('')
    A('== 7. PAIRED ANALYSIS: same (prefix,plant,view), 230418(Flood) -> 230608(Healthy) ==')
    pvm = collections.defaultdict(dict)
    for r in rows:
        if r['family'] == 'PV':
            pvm[(r['prefix'], r['plant'], r['view'])].setdefault(r['date'], []).append(r)
    keys = [k for k, dm in pvm.items() if '230418' in dm and '230608' in dm]
    A('  paired plant-views: %d' % len(keys))
    A('%-14s %12s %12s %10s %10s' % ('feature', 'mean_delta', 'std_delta', 'wilcoxon_p', 'pairs_delta>0'))
    for k in FEATS:
        d = []
        for kk in keys:
            a = np.mean([r[k] for r in pvm[kk]['230608']])
            b = np.mean([r[k] for r in pvm[kk]['230418']])
            d.append(a - b)
        d = np.array(d)
        A('%-14s %12.4f %12.4f %10.2e %6d/%d' % (k, d.mean(), d.std(ddof=1), wilcoxon_p(d), int((d > 0).sum()), len(d)))

    A('')
    A('== 8. MULTIVARIATE LDA, 5-fold CV grouped by (prefix,plant)  [PV family only] ==')
    pv = [r for r in rows if r['family'] == 'PV']
    for tag, c0, c1 in (('Healthy vs Flood', 0, 1), ('Flood vs BBWV2', 1, 2)):
        sub = [r for r in pv if r['cls'] in (c0, c1)]
        X = np.array([[r[k] for k in FEATS] for r in sub], float)
        X = (X - X.mean(0)) / (X.std(0) + 1e-9)
        y = np.array([1 if r['cls'] == c1 else 0 for r in sub])
        g = np.array(['%s_%s' % (r['prefix'], r['plant']) for r in sub])
        a = cv_lda_auc(X, y, g, K=5)
        A('  %-18s n=%-5d groups=%-4d CV_AUC(class %d higher)=%.4f' % (tag, len(sub), len(set(g)), c1, a))

    A('')
    A('elapsed %.1fs' % (time.time() - t0))
    txt = '\n'.join(log)
    open(os.path.join(OUTDIR, 'validation_report_v2.txt'), 'w', encoding='utf-8').write(txt)
    print(txt)


if __name__ == '__main__':
    try:
        main()
    except Exception:
        traceback.print_exc(); sys.exit(1)
