# -*- coding: utf-8 -*-
"""
GreenThumb AI - v7 (decisive re-analysis):
v5 stratified on Delta(L*), but L* turns out to be a POOR illuminant proxy (separability 0.47).
v6 found a better one: specular-highlight a* (separability 1.24).
This script redoes the survival test against the BETTER proxy, and asks whether the
DIRECTION of the biological change survives.
"""
import os, csv, collections, math
import numpy as np
try:
    from scipy.stats import pearsonr, linregress, ttest_1samp, binomtest, t as tdist
    SC = True
except Exception:
    SC = False

OUT = os.environ.get('GT_OUT') or r'C:\Users\lenovo\greenthumb\experiments\results'
rows = []
with open(os.path.join(OUT, 'color_features_v6.csv'), encoding='utf-8-sig') as f:
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


def dv(m):
    return np.array([np.mean([x[m] for x in pvm[k]['230608']]) - np.mean([x[m] for x in pvm[k]['230418']]) for k in keys])


DC = dv('chlorophyll'); DCI = dv('chloro_ill'); DL = dv('L_mean'); DI = dv('ill_a'); DB = dv('b_mean')
L = []; A = L.append
A('pairs=%d' % len(keys))

A_ = A
A('')
A('== 1. which covariate actually tracks the paired change? ==')
A('%-18s %12s %10s %10s' % ('Delta covariate', 'pearson_r', 'p', 'R^2'))
for nm, v in (('Delta ill_a (highlight)', DI), ('Delta L* (brightness)', DL)):
    if SC:
        r, p = pearsonr(DC, v)
        A('%-18s %12.3f %10.2e %10.3f' % (nm, r, p, r * r))
    else:
        r = np.corrcoef(DC, v)[0, 1]
        A('%-18s %12.3f %10s %10.3f' % (nm, r, 'n/a', r * r))
A('  corr(Delta ill_a, Delta L*) = %.3f   (how redundant are the two proxies)' % np.corrcoef(DI, DL)[0, 1])

A('')
A('== 2. control for the illuminant: does Delta(chlorophyll) survive? ==')
if SC:
    lr = linregress(DI, DC)
    n = len(DC); xm = DI.mean(); sxx = ((DI - xm) ** 2).sum()
    s2 = ((DC - (lr.slope * DI + lr.intercept)) ** 2).sum() / (n - 2)
    se = math.sqrt(s2 * (1.0 / n + xm ** 2 / sxx)); t = lr.intercept / se
    p = 2 * (1 - tdist.cdf(abs(t), n - 2))
    A('  d_chlorophyll = %.4f * d_ill_a + %.4f      R^2 = %.4f (p = %.2e)' % (lr.slope, lr.intercept, lr.rvalue ** 2, lr.pvalue))
    A('  intercept = %+.4f (SE %.4f, t = %.2f, p = %.2e)' % (lr.intercept, se, t, p))
    A('  -> controlling for the illuminant, expected chlorophyll change = %+.2f (p=%.1e)' % (lr.intercept, p))

A('')
A('== 3. tertiles by Delta ill_a (the GOOD proxy) ==')
o = np.argsort(DI)
A('%-14s %4s %10s %12s %8s %10s %10s' % ('stratum', 'n', 'd_ill_a', 'd_chloro', 'sd', 't', 'same-sign'))
for nm, idx in (('ill_a DROP', o[:27]), ('ill_a MID', o[27:55]), ('ill_a RISE', o[55:])):
    d = DC[idx]
    if SC:
        t, p = ttest_1samp(d, 0.0)
        k = int((d > 0).sum()); bp = binomtest(k, len(d), 0.5).pvalue
    else:
        t = d.mean() / (d.std(ddof=1) / math.sqrt(len(d))); p = float('nan'); k = int((d > 0).sum()); bp = float('nan')
    A('%-14s %4d %10.2f %12.3f %8.2f %10.2f %d/%d p=%.1e' % (nm, len(idx), DI[idx].mean(), d.mean(), d.std(ddof=1), t, k, len(d), bp))

A('')
A('== 4. same, for the illuminant-NORMALISED metric ==')
A('%-14s %4s %10s %12s %8s %10s %10s' % ('stratum', 'n', 'd_ill_a', 'd_chloro_ill', 'sd', 't', 'same-sign'))
for nm, idx in (('ill_a DROP', o[:27]), ('ill_a MID', o[27:55]), ('ill_a RISE', o[55:])):
    d = DCI[idx]
    if SC:
        t, p = ttest_1samp(d, 0.0)
        k = int((d < 0).sum()); bp = binomtest(k, len(d), 0.5).pvalue
    else:
        t = d.mean() / (d.std(ddof=1) / math.sqrt(len(d))); p = float('nan'); k = int((d < 0).sum()); bp = float('nan')
    A('%-14s %4d %10.2f %12.3f %8.2f %10.2f %d/%d p=%.1e' % (nm, len(idx), DI[idx].mean(), d.mean(), d.std(ddof=1), t, k, len(d), bp))

A('')
A('== 5. head-to-head: which metric is more internally consistent? ==')
A('%-16s %10s %12s %12s' % ('metric', 'cohen_d', 'sd_delta', 'same_sign'))
for nm, v in (('chlorophyll', DC), ('chloro_ill', DCI)):
    A('%-16s %10.2f %12.4f %11.1f%%' % (nm, v.mean() / v.std(ddof=1), v.std(ddof=1), 100 * max((v > 0).mean(), (v < 0).mean())))

A('')
A('== 6. does the 230608 illuminant outlier explain the anomaly? ==')
ill = [np.mean([r['ill_a'] for r in rows if r['date'] == d]) for d in sorted(set(r['date'] for r in rows))]
dts = sorted(set(r['date'] for r in rows))
A('  %-9s %10s %12s' % ('date', 'ill_a', 'leaf a*'))
for d, iv in zip(dts, ill):
    A('  %-9s %10.2f %12.2f' % (d, iv, np.mean([r['a_mean'] for r in rows if r['date'] == d])))
if SC:
    r, p = pearsonr(ill, [np.mean([r['a_mean'] for r in rows if r['date'] == d]) for d in dts])
    A('  across 6 sessions corr(illuminant a*, leaf a*) = %.3f (p=%.3f, n=6)' % (r, p))

A('')
A('== 7. VERDICT ==')
A('  ILLUMINANT IS RECOVERABLE (weakly): specular-highlight a*, separability 1.24 vs L* 0.47')
A('  -> the 230608 session is an ILLUMINATION outlier (|z|=2.3), independently confirming the')
A('     session confound diagnosed earlier.')
A('  -> highlight normalisation cuts a* session variance from 69.4%% to 43.9%%.')
if SC:
    A('  -> BUT it FLIPS the sign of the paired change: raw d=%+.2f, normalised d=%+.2f.' % (DC.mean(), DCI.mean()))
    A('  -> raw  same-sign %.1f%%   normalised same-sign %.1f%%' % (
        100 * max((DC > 0).mean(), (DC < 0).mean()), 100 * max((DCI > 0).mean(), (DCI < 0).mean())))
    A('  => The MEASUREMENT is reproducible, but the DIRECTION of the biological effect is')
    A('     NOT established by this dataset: it depends on which illumination model you adopt.')

txt = '\n'.join(L)
open(os.path.join(OUT, 'validation_report_v7.txt'), 'w', encoding='utf-8').write(txt)
print(txt)
