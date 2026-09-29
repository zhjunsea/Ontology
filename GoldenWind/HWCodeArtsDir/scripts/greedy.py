"""贪心取大：从 980 起，每步优先 1960→1680→1400，避焊缝才降档；末组落平台带内。
位置恒为 980+280k（280 倍数）。对比两种平台带下界（1400 / 840）。"""

import openpyxl
import sys

sys.setrecursionlimit(100000)

F = r"D:/work/Ontology/GoldenWind/design/towerdesign-main/TowerGeoInput_10563080_HH130m_6段_中径4.95m_GWH182-7.5_外置_锚栓式__主体434t-20251103-2025-11-06-11-24-50.xlsx"
wb = openpyxl.load_workbook(F, data_only=True)
ws = wb["TowerGeo"]
rows = []
for r in range(2, ws.max_row + 1):
    b, d = ws.cell(r, 2).value, ws.cell(r, 4).value
    if b is None or d is None:
        continue
    rows.append((r, float(b), float(d)))
wf = wb["Flange"]
fl = [float(wf.cell(r, 1).value) for r in range(3, wf.max_row + 1) if wf.cell(r, 1).value is not None]

FIRST, EDGE, PLAT2TOP, STEP = 980, 42.5, 1250, 280


def welds_of(s):
    lo, hi = fl[s - 1], fl[s]
    sel = [(rn, b) for (rn, b, d) in rows if lo - 1e-9 <= b < hi - 1e-9]
    base = sel[0][1]
    return [round((b - base) * 1000) for (rn, b) in sel if abs(b - lo) > 1e-9]


def solve(platH, welds, low):
    bandLo, bandHi = platH - 1960, platH - low
    if not (all(abs(FIRST - w) - EDGE > 100 for w in welds)):
        return None
    if bandLo <= FIRST <= bandHi:
        return [FIRST]
    memofail = set()
    maxk = int((bandHi - FIRST) // STEP)
    if maxk < 5:
        return None

    def safe(h):
        return all(abs(h - w) - EDGE > 100 for w in welds)

    def dfs(k):
        h = FIRST + k * STEP
        if bandLo - 1e-6 <= h <= bandHi + 1e-6:
            return [k]
        if k in memofail:
            return None
        for m in (7, 6, 5):
            k2 = k + m
            if k2 > maxk:
                continue
            if not safe(FIRST + k2 * STEP):
                continue
            sub = dfs(k2)
            if sub is not None:
                return [k] + sub
        memofail.add(k)
        return None

    ks = dfs(0)
    return None if ks is None else [FIRST + k * STEP for k in ks]


for low in (1400, 840):
    print("#" * 84)
    print("末组到平台下界 = %d  （平台带 [platH-1960, platH-%d]）" % (low, low))
    for s in [2, 3, 4, 5]:
        secH = round((fl[s] - fl[s - 1]) * 1000, 1)
        platH = secH - PLAT2TOP
        welds = welds_of(s)
        hs = solve(platH, welds, low)
        print("-" * 84)
        if hs is None:
            print("第%d段 platH=%.0f → 无解" % (s, platH))
            continue
        inc = [int(hs[i] - hs[i - 1]) for i in range(1, len(hs))]
        print("第%d段 platH=%.0f n=%d  位置=%s" % (s, platH, len(hs), hs))
        print("            间距=%s  末组距平台=%.0f" % (inc, platH - hs[-1]))
