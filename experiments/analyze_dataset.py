# -*- coding: utf-8 -*-
"""Chili Pepper BBWV2/Flooding dataset structure analysis (stdlib only)."""
import json, os, re, struct, collections

ROOT = r'C:\Users\lenovo\greenthumb\experiments\data\Chili\pepper_chili_dataset'
IMG = os.path.join(ROOT, 'images')
CLASSES = {0: 'Healthy', 1: 'Flood', 2: 'BBWV2'}

labels = json.load(open(os.path.join(ROOT, 'labels.json'), encoding='utf-8'))
files = set(f for f in os.listdir(IMG) if f.lower().endswith('.jpg'))

print('== 1. basics ==')
print('labels entries :', len(labels))
print('jpg on disk    :', len(files))
print('labeled but missing file:', len(set(labels) - files))
print('file but unlabeled      :', len(files - set(labels)))


def parse(name):
    stem = name[:-4]
    p = stem.split('_')
    prefix, date = p[0], p[1]
    rest = p[2:]
    if 'DSC' in rest[0]:
        return prefix, date, 'DSC', None, rest[0], int(rest[-1])
    m = re.match(r'^(\d+)-(\d+)$', rest[0])
    if m:
        return prefix, date, 'PV', int(m.group(1)), int(m.group(2)), int(rest[-1])
    return prefix, date, 'OTHER', None, rest[0], int(rest[-1])


recs = {}
for f in files:
    recs[f] = parse(f) + (labels.get(f),)

print()
print('== 2. class distribution ==')
c = collections.Counter(r[6] for r in recs.values())
for k in sorted(c):
    print('  %-8s %5d' % (CLASSES.get(k, k), c[k]))

print()
print('== 3. prefix x family ==')
tab = collections.Counter((r[0], r[2]) for r in recs.values())
for k in sorted(tab):
    print('  %s %-6s %5d' % (k[0], k[1], tab[k]))

print()
print('== 4. prefix x date ==')
tab = collections.Counter((r[0], r[1]) for r in recs.values())
for k in sorted(tab):
    print('  %s %s %5d' % (k[0], k[1], tab[k]))

print()
print('== 5. date x class ==')
tab = collections.Counter((r[1], r[6]) for r in recs.values())
for k in sorted(tab):
    print('  %s %-8s %5d' % (k[0], CLASSES.get(k[1], k[1]), tab[k]))

print()
print('== 6. PV family: is class constant per (prefix,plant)? ==')
pv = [(r[0], r[3], r[4], r[1], r[6]) for r in recs.values() if r[2] == 'PV']
per_plant = collections.defaultdict(set)
for pre, pl, vw, dt, cl in pv:
    per_plant[(pre, pl)].add(cl)
multi = {k: v for k, v in per_plant.items() if len(v) > 1}
print('  plants (prefix,id) total :', len(per_plant))
print('  plants with >1 class     :', len(multi))
for k in sorted(multi)[:10]:
    print('    %s -> %s' % (k, sorted(CLASSES[x] for x in multi[k])))

print()
print('== 7. PV family: distinct dates per (prefix,plant) ==')
per_plant_dates = collections.defaultdict(set)
for pre, pl, vw, dt, cl in pv:
    per_plant_dates[(pre, pl)].add(dt)
d = collections.Counter(len(v) for v in per_plant_dates.values())
print('  #dates -> #plants:', dict(sorted(d.items())))

print()
print('== 8. PV: shots per (prefix,plant,view,date) ==')
per_slot = collections.Counter((r[0], r[3], r[4], r[1]) for r in recs.values() if r[2] == 'PV')
print('  n slots:', len(per_slot))
hist = collections.Counter(per_slot.values())
print('  shots/slot -> #slots:', dict(sorted(hist.items())))

print()
print('== 9. PV: views per (prefix,plant) ==')
per_plant_views = collections.defaultdict(set)
for pre, pl, vw, dt, cl in pv:
    per_plant_views[(pre, pl)].add(vw)
print('  #views -> #plants:', dict(sorted(collections.Counter(len(v) for v in per_plant_views.values()).items())))

print()
print('== 10. PV: sample plant timeline (first 3 plants of p1) ==')
keys = sorted(k for k in per_plant if k[0] == 'p1')[:3]
for k in keys:
    rows = sorted(set((dt, vw) for pre, pl, vw, dt, cl in pv if (pre, pl) == k))
    cls = sorted(CLASSES[x] for x in per_plant[k])
    print('  %s class=%s dates/views=%s' % (k, cls, rows))

print()
print('== 11. DSC family structure ==')
dsc = [(r[0], r[1], r[4], r[5], r[6]) for r in recs.values() if r[2] == 'DSC']
print('  n:', len(dsc))
# DSC token like 1DSC / 2DSC ; group by (prefix,date,which)
g = collections.Counter((r[0], r[1], r[2][0]) for r in dsc)
for k in sorted(g)[:20]:
    print('  %s %s view=%s %5d' % k)
print('  ...')
gname = collections.Counter(r[2] for r in dsc)
print('  token2 values:', dict(gname))


def jpeg_size(path):
    with open(path, 'rb') as f:
        data = f.read(300000)
    i = 2
    n = len(data)
    while i < n - 9:
        if data[i] != 0xFF:
            i += 1
            continue
        mk = data[i + 1]
        if mk in (0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF):
            h, w = struct.unpack('>HH', data[i + 5:i + 9])
            return w, h
        if mk in (0xD8, 0x01) or 0xD0 <= mk <= 0xD7:
            i += 2
            continue
        if i + 4 > n:
            break
        seg = struct.unpack('>H', data[i + 2:i + 4])[0]
        i += 2 + seg
    return None


print()
print('== 12. image sizes (sample) ==')
for fam in ('PV', 'DSC'):
    names = sorted(f for f, r in recs.items() if r[2] == fam)[:3]
    for nm in names:
        print('  %-6s %-30s %s' % (fam, nm, jpeg_size(os.path.join(IMG, nm))))

print()
print('== 13. per-batch: which classes, which dates ==')
for pre in sorted(set(r[0] for r in recs.values())):
    cls = sorted(set(r[6] for r in recs.values() if r[0] == pre))
    dts = sorted(set(r[1] for r in recs.values() if r[0] == pre))
    fams = sorted(set(r[2] for r in recs.values() if r[0] == pre))
    print('  %s classes=%s dates=%s families=%s' % (pre, [CLASSES[x] for x in cls], dts, fams))
