# -*- coding: utf-8 -*-
"""
GreenThumb AI - v4: is the paired change BIOLOGICAL or a LIGHTING artefact?
Reads color_features_v3.csv (no image re-processing).

Logic:
  A pure illumination shift changes leaf brightness/colour globally.
  -> If Delta(chlorophyll) tracks Delta(L*) tightly, the "change" is lighting.
  -> If they are independent, the change carries plant-specific information.
"""
import os, csv, collections
import numpy as np

try:
    from scipy.stats import pearsonr, spearmanr, linregress
    HAVE_SCIPY = True
except Exception:
    HAVE_SCIPY = False

OUT = os.environ.get('GT_OUT') or r'C:\Users\lenovo\greenthumb\experiments\results'
rows = []
with open(os.path.join(OUT, 'color_features_v3.csv'), encoding='utf-8-sig') as f:
    for r in csv.DictReader(f):
        for k in list(r):
            if k in ('name', 'prefix', 'date', 'family'):
                continue
            r[k] = int(r[k]) if k in ('plant', 'view', 'cls') else float(r[k])
        rows.append(r)

pvm = collections.defaultdict(dict)
for r in rows:
    if r['family'] == 'PV':
        pvm[(r['prefix'], r['plant'], r['view'])].setdefault(r['date'], []).append(r)
keys = [k for k, dm in pvm.items() if '230418' in dm and '230608' in dm]

METS = ['chlorophyll', 'a_mean', 'b_mean', 'L_mean', 'chroma_mean', 'health_index',
        'exg_mean', 'green_ratio', 'yellow_ratio', 'bg_L', 'bg_a', 'bg_b', 'leaf_frac']


def dvec(m):
    return np.array([np.mean([x[m] for x in pvm[k]['230608']]) - np.mean([x[m] for x in pvm[k]['230418']]) for k in keys])


D = {m: dvec(m) for m in METS}
L = []
A = L.append
A('pairs = %d   scipy = %s' % (len(keys), HAVE_SCIPY))

A('')
A('== 1. paired deltas (mean +/- sd) ==')
for m in METS:
    A('  %-14s %+10.4f +/- %8.4f' % (m, D[m].mean(), D[m].std(ddof=1)))

A('')
A('== 2. Is the change driven by illumination?  corr(Delta metric, Delta L*) ==')
A('%-14s %10s %10s %10s %10s' % ('metric', 'pearson_r', 'p', 'spearman', 'R^2'))
for m in METS:
    if m == 'L_mean':
        continue
    if HAVE_SCIPY:
        r, p = pearsonr(D[m], D['L_mean'])
        rs, _ = spearmanr(D[m], D['L_mean'])
        A('%-14s %10.3f %10.2e %10.3f %10.3f' % (m, r, p, rs, r * r))
    else:
        r = np.corrcoef(D[m], D['L_mean'])[0, 1]
        A('%-14s %10.3f %10s %10s %10.3f' % (m, r, 'n/a', 'n/a', r * r))

A('')
A('== 3. Same check against background brightness change (Delta bg_L) ==')
A('%-14s %10s %10s' % ('metric', 'pearson_r', 'p'))
for m in ('chlorophyll', 'a_mean', 'b_mean', 'health_index', 'exg_mean'):
    if HAVE_SCIPY:
        r, p = pearsonr(D[m], D['bg_L'])
        A('%-14s %10.3f %10.2e' % (m, r, p))
    else:
        A('%-14s %10.3f %10s' % (m, np.corrcoef(D[m], D['bg_L'])[0, 1], 'n/a'))

A('')
A('== 4. Remove the illumination component, does the signal survive? ==')
A('   regress Delta(chlorophyll) on Delta(L*); test the sign of the residual')
if HAVE_SCIPY:
    lr = linregress(D['L_mean'], D['chlorophyll'])
    A('   fit: d_chlorophyll = %.4f * dL + %.4f     (R^2 = %.4f, p = %.2e)' % (lr.slope, lr.intercept, lr.rvalue ** 2, lr.pvalue))
    pred = lr.slope * D['L_mean'] + lr.intercept
    resid = D['chlorophyll'] - pred
    A('   raw   Delta: mean=%+8.4f sd=%7.4f  same-sign=%5.1f%%' % (D['chlorophyll'].mean(), D['chlorophyll'].std(ddof=1), 100 * max((D['chlorophyll'] > 0).mean(), (D['chlorophyll'] < 0).mean())))
    A('   resid Delta: mean=%+8.4f sd=%7.4f  same-sign=%5.1f%%' % (resid.mean(), resid.std(ddof=1), 100 * max((resid > 0).mean(), (resid < 0).mean())))
    try:
        from scipy.stats import wilcoxon
        A('   residual Wilcoxon p = %.3e' % wilcoxon(resid).pvalue)
    except Exception:
        pass
else:
    A('   scipy unavailable, skipped')

A('')
A('== 5. Split pairs by how much brightness changed ==')
o = np.argsort(D['L_mean'])
half = len(o) // 2
for tag, idx in (('brightness DROPPED most', o[:half]), ('brightness ROSE most', o[-half:])):
    A('  %-24s n=%2d  dL*=%+7.3f   d_chlorophyll=%+8.3f (sd %6.3f)' % (
        tag, len(idx), D['L_mean'][idx].mean(), D['chlorophyll'][idx].mean(), D['chlorophyll'][idx].std(ddof=1)))

A('')
A('== 6. Illumination-corrected metric across ALL images ==')
if HAVE_SCIPY:
    beta = linregress([r['L_mean'] for r in rows], [r['chlorophyll'] for r in rows]).slope
    for r in rows:
        r['chloro_lc'] = r['chlorophyll'] - beta * r['L_mean']


    def vardecomp(key):
        pv = [r for r in rows if r['family'] == 'PV']
        by_s = collections.defaultdict(list)
        for r in pv:
            by_s[r['date']].append(r)
        sm = {d: np.mean([r[key] for r in g]) for d, g in by_s.items()}
        v_sess = float(np.var(list(sm.values())))
        rp, ws = [], []
        for d, g in by_s.items():
            bpp = collections.defaultdict(list)
            for r in g:
                bpp[(r['prefix'], r['plant'])].append(r)
            for k, gg in bpp.items():
                rp.append(np.mean([r[key] for r in gg]) - sm[d])
            bpv = collections.defaultdict(list)
            for r in g:
                bpv[(r['prefix'], r['plant'], r['view'])].append(r)
            for k, gg in bpv.items():
                if len(gg) >= 3:
                    ws.append(np.std([r[key] for r in gg], ddof=1))
        v_plant = max(float(np.var(rp)), 0)
        v_leaf = float(np.mean(ws) ** 2)
        tot = v_sess + v_plant + v_leaf
        return 100 * v_sess / tot, 100 * v_leaf / tot
    A('   beta = %.4f' % beta)
    for k in ('chlorophyll', 'chloro_lc'):
        s, lf = vardecomp(k)
        A('   %-12s session%%=%5.1f  leaf%%=%5.1f' % (k, s, lf))

A('')
A('== 7. VERDICT ==')
if HAVE_SCIPY:
    r_light = pearsonr(D['chlorophyll'], D['L_mean'])[0]
    keep = max((resid > 0).mean(), (resid < 0).mean())
    A('   corr(d_chlorophyll, dL*) = %.3f  -> illumination explains %.1f%% of the paired change' % (r_light, 100 * r_light ** 2))
    A('   after removing illumination, same-sign rate = %.1f%% (raw %.1f%%)' % (
        100 * keep, 100 * max((D['chlorophyll'] > 0).mean(), (D['chlorophyll'] < 0).mean())))
    A('   => %s' % ('the change SURVIVES illumination removal -> it carries plant-specific biological information'
                    if keep > 0.75 else
                    'the change is largely explained by illumination -> NOT usable as a biological signal'))

txt = '\n'.join(L)
open(os.path.join(OUT, 'validation_report_v4.txt'), 'w', encoding='utf-8').write(txt)
print(txt)
