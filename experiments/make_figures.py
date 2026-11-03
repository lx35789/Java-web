# -*- coding: utf-8 -*-
"""GreenThumb AI - figures from color_features_v2.csv (no image re-processing)."""
import os, csv, collections
import numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt

OUT = os.environ.get('GT_OUT') or r'C:\Users\lenovo\greenthumb\experiments\results'
SRC = os.path.join(OUT, 'color_features_v2.csv')
FIG = os.path.join(OUT, 'figures')
os.makedirs(FIG, exist_ok=True)

rows = []
with open(SRC, encoding='utf-8-sig') as f:
    for r in csv.DictReader(f):
        for k in r:
            if k in ('name', 'prefix', 'date', 'family'):
                continue
            r[k] = int(r[k]) if k in ('plant', 'view', 'shot', 'cls') else float(r[k])
        rows.append(r)
print('loaded', len(rows))

CLS = {0: 'Healthy', 1: 'Flood', 2: 'BBWV2'}
COL = {0: '#2e7d32', 1: '#1565c0', 2: '#c62828'}
DATES = sorted(set(r['date'] for r in rows))
DC = {}
for d in DATES:
    DC[d] = COL[sorted(set(r['cls'] for r in rows if r['date'] == d))[0]]


def box(ax, key, title, ylab):
    data = [[r[key] for r in rows if r['date'] == d] for d in DATES]
    bp = ax.boxplot(data, patch_artist=True, showfliers=False, widths=.6)
    for patch, d in zip(bp['boxes'], DATES):
        patch.set_facecolor(DC[d]); patch.set_alpha(.75)
    for med in bp['medians']:
        med.set_color('black'); med.set_linewidth(1.4)
    ax.set_xticklabels(['%s\n%s' % (d, CLS[sorted(set(r['cls'] for r in rows if r['date'] == d))[0]]) for d in DATES], fontsize=7)
    ax.set_title(title, fontsize=10)
    ax.set_ylabel(ylab, fontsize=9)
    ax.grid(axis='y', alpha=.3)


# ---- Fig 1: session effect ----
fig, axes = plt.subplots(1, 3, figsize=(15, 4.2))
box(axes[0], 'health_index', 'health_index by session', 'health_index')
box(axes[1], 'a_mean', 'a* (chlorophyll proxy) by session', 'a*  (lower = greener)')
box(axes[2], 'L_mean', 'L* (brightness) by session', 'L*')
fig.suptitle('Session effect dominates: each date = exactly one class', fontsize=11)
fig.tight_layout()
fig.savefig(os.path.join(FIG, 'fig1_session_effect.png'), dpi=150)
plt.close(fig)

# ---- Fig 2: variance decomposition ----
METRICS = ['health_index', 'yellow_frac', 'b_mean', 'a_mean']
comp = {}
for MET in METRICS:
    pv = [r for r in rows if r['family'] == 'PV']
    by_s = collections.defaultdict(list)
    for r in pv:
        by_s[r['date']].append(r)
    sm = {d: np.mean([r[MET] for r in g]) for d, g in by_s.items()}
    v_sess = float(np.var(list(sm.values())))
    rp, rs, ws = [], [], []
    for d, g in by_s.items():
        bp = collections.defaultdict(list)
        for r in g:
            bp[(r['prefix'], r['plant'], r['view'])].append(r)
        bpp = collections.defaultdict(list)
        for r in g:
            bpp[(r['prefix'], r['plant'])].append(r)
        for k, gg in bpp.items():
            rp.append(np.mean([r[MET] for r in gg]) - sm[d])
        for k, gg in bp.items():
            rs.append(np.mean([r[MET] for r in gg]) - sm[d])
            if len(gg) >= 3:
                ws.append(np.std([r[MET] for r in gg], ddof=1))
    v_plant = max(float(np.var(rp)), 0)
    v_slot = max(float(np.var(rs)) - v_plant, 0)
    v_leaf = float(np.mean(ws) ** 2)
    tot = v_sess + v_plant + v_slot + v_leaf
    comp[MET] = [100 * v_sess / tot, 100 * v_plant / tot, 100 * v_slot / tot, 100 * v_leaf / tot]

fig, ax = plt.subplots(figsize=(8, 4.2))
labels = ['session (date)', 'plant', 'plant-view', 'leaf (within-slot)']
colors = ['#ef6c00', '#6a1b9a', '#00838f', '#2e7d32']
bottom = np.zeros(len(METRICS))
x = np.arange(len(METRICS))
for i, lb in enumerate(labels):
    vals = np.array([comp[m][i] for m in METRICS])
    ax.bar(x, vals, .55, bottom=bottom, label=lb, color=colors[i])
    for xi, (v, b) in enumerate(zip(vals, bottom)):
        if v > 5:
            ax.text(xi, b + v / 2, '%.0f%%' % v, ha='center', va='center', fontsize=8, color='white')
    bottom += vals
ax.set_xticks(x); ax.set_xticklabels(METRICS, fontsize=9)
ax.set_ylabel('% of total variance'); ax.set_ylim(0, 100)
ax.set_title('Variance decomposition (PV family, n=2102)', fontsize=10)
ax.legend(fontsize=8, ncol=4, loc='upper center', bbox_to_anchor=(.5, -.12))
fig.tight_layout()
fig.savefig(os.path.join(FIG, 'fig2_variance_decomposition.png'), dpi=150)
plt.close(fig)

# ---- Fig 3: paired slopes ----
pvm = collections.defaultdict(dict)
for r in rows:
    if r['family'] == 'PV':
        pvm[(r['prefix'], r['plant'], r['view'])].setdefault(r['date'], []).append(r)
keys = [k for k, dm in pvm.items() if '230418' in dm and '230608' in dm]
fig, axes = plt.subplots(1, 2, figsize=(11, 4.4))
for MET, ax, ttl in (('a_mean', axes[0], 'a*  (chlorophyll proxy)'), ('yellow_ratio', axes[1], 'yellow_ratio (HSV)')):
    for k in keys:
        y0 = np.mean([r[MET] for r in pvm[k]['230418']])
        y1 = np.mean([r[MET] for r in pvm[k]['230608']])
        ax.plot([0, 1], [y0, y1], color='#1565c0', alpha=.28, lw=.9)
        ax.scatter([0, 1], [y0, y1], color='#1565c0', s=4, alpha=.35)
    ax.set_xticks([0, 1]); ax.set_xticklabels(['230418\nFlood', '230608\nHealthy'], fontsize=8)
    ax.set_title('%s  (n=%d paired plant-views)' % (ttl, len(keys)), fontsize=9)
    ax.grid(alpha=.3)
fig.suptitle('Same plant, same view, two sessions: consistent within-plant shift', fontsize=11)
fig.tight_layout()
fig.savefig(os.path.join(FIG, 'fig3_paired_slopes.png'), dpi=150)
plt.close(fig)

# ---- Fig 4: session-pairwise AUC heatmap ----
def rankdata(a):
    a = np.asarray(a, float); order = a.argsort(kind='mergesort')
    r = np.empty(len(a), float); r[order] = np.arange(1, len(a) + 1)
    sa = a[order]; i = 0
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
    r = rankdata(np.concatenate([x, y]))
    return (r[:n1].sum() - n1 * (n1 + 1) / 2.0) / (n1 * n2)


fd = sorted(set(r['date'] for r in rows if r['cls'] == 1))
hd = sorted(set(r['date'] for r in rows if r['cls'] == 0))
fig, axes = plt.subplots(1, 2, figsize=(9.5, 3.6))
for MET, ax in (('health_index', axes[0]), ('a_mean', axes[1])):
    M = np.zeros((len(fd), len(hd)))
    for i, a in enumerate(fd):
        for j, b in enumerate(hd):
            M[i, j] = auc([r[MET] for r in rows if r['date'] == b], [r[MET] for r in rows if r['date'] == a])
    im = ax.imshow(M, cmap='RdBu_r', vmin=0, vmax=1)
    for i in range(len(fd)):
        for j in range(len(hd)):
            ax.text(j, i, '%.2f' % M[i, j], ha='center', va='center', fontsize=10,
                    color='white' if abs(M[i, j] - .5) > .25 else 'black')
    ax.set_xticks(range(len(hd))); ax.set_xticklabels(hd, fontsize=9)
    ax.set_yticks(range(len(fd))); ax.set_yticklabels(fd, fontsize=9)
    ax.set_xlabel('Healthy session', fontsize=9); ax.set_ylabel('Flood session', fontsize=9)
    ax.set_title('AUC %s (Healthy>Flood?)' % MET, fontsize=9)
fig.colorbar(im, ax=axes, shrink=.8)
fig.suptitle('Same contrast flips sign across sessions -> class is aliased with session', fontsize=11)
fig.savefig(os.path.join(FIG, 'fig4_session_pair_auc.png'), dpi=150, bbox_inches='tight')
plt.close(fig)

print('figures ->', FIG)
for f in sorted(os.listdir(FIG)):
    print('  ', f, os.path.getsize(os.path.join(FIG, f)))
