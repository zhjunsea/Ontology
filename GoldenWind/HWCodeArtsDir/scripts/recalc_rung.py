"""按真实几何重算中段四段附件布局：边缘偏移改用踏棍宽度 W_Rung/2（对照旧 W_LADDER_I/2）。
忠实复刻 TowerDesignEngine.accessoryLayout 的 DP 口径。"""

W_RUNG = 85.0          # 布局表「中间段」r14 踏棍宽度（五段均 85）
W_LADDER_I = 910.0     # 布局表「中间段」r5 爬梯支撑宽度（旧口径）

FIRST = 980.0
STEP = 280.0
CAND = {6, 7}                 # 引擎现值：增量 {1680,1960}
CAND3 = {5, 6, 7}             # 备查：{1400,1680,1960}
SECOND_LAST_MIN, SECOND_LAST_MAX = 1400.0, 1960.0
ACCESSORY_TO_WELD_MIN = 100.0

SEC = [
    ("中段第1段(第2段)", 18480, 17230, [215, 2800, 5600, 8400, 11200, 14000, 16680, 18305, 18480]),
    ("中段第2段(第3段)", 19600, 18350, [175, 2800, 5600, 8400, 11200, 14000, 16800, 19455, 19600]),
    ("中段第3段(第4段)", 24920, 23670, [145, 2800, 5600, 8400, 11200, 14000, 16800, 19600, 22400, 24800, 24920]),
    ("中段第4段(第5段)", 24640, 23390, [120, 2800, 5600, 8400, 11200, 14000, 16800, 19600, 22400, 24530, 24640]),
]


def edge_net(h, welds, off):
    return min(abs(h - w) for w in welds) - off


def edge_safe(h, welds, off):
    return edge_net(h, welds, off) > ACCESSORY_TO_WELD_MIN


def solve(platH, welds, off, cand):
    band_lo, band_hi = platH - SECOND_LAST_MAX, platH - SECOND_LAST_MIN
    if not edge_safe(FIRST, welds, off):
        return None
    max_k = int((band_hi - FIRST) // STEP + 1e-9)
    if max_k < min(cand):
        return None
    reach = {(1, 0)}
    parent = {}
    for c in range(2, max_k + 1):
        any_new = False
        for (cc, k) in list(reach):
            if cc != c - 1:
                continue
            for m in cand:
                k2 = k + m
                if k2 > max_k or (c, k2) in reach:
                    continue
                h2 = FIRST + k2 * STEP
                if not edge_safe(h2, welds, off):
                    continue
                reach.add((c, k2))
                parent[(c, k2)] = k
                any_new = True
        if not any_new:
            break
    best, best_c, best_k = None, None, None
    for (c, k) in reach:
        h = FIRST + k * STEP
        if h < band_lo - 1e-6 or h > band_hi + 1e-6:
            continue
        score = abs(1960 - 1960) if c == 1 else abs((h - FIRST) / (c - 1) - 1960)
        if best is None or score < best - 1e-9:
            best, best_c, best_k = score, c, k
    if best_c is None:
        return None
    hs, c, k = [], best_c, best_k
    while c >= 1:
        hs.insert(0, FIRST + k * STEP)
        if c == 1:
            break
        k = parent[(c, k)]
        c -= 1
    return hs


def report(off, cand, tag):
    print("#" * 80)
    print("边缘偏移 off=%s（%s）  候选=%s" % (off, tag, sorted(x * 280 for x in cand)))
    print("判据: |h-w| - %s > %s  ⇒ |h-w| > %s" % (off, ACCESSORY_TO_WELD_MIN, off + ACCESSORY_TO_WELD_MIN))
    for name, secH, platH, welds in SEC:
        hs = solve(platH, welds, off, cand)
        if hs is None:
            print("  %s: 无解 (n=0)" % name)
        else:
            incs = [FIRST] + [hs[i] - hs[i - 1] for i in range(1, len(hs))]
            net = min(edge_net(h, welds, off) for h in hs)
            print("  %s: n=%d 位置=%s 增量=%s 末组到平台=%.0f 最小边缘净距=%.1f"
                  % (name, len(hs), [int(h) for h in hs], [int(x) for x in incs],
                     platH - hs[-1], net))
    print()


def second_group(off):
    """诊断：首组 980 之后各组中心到最近焊缝的净距（以第 2 段焊 2800 为例）。"""
    print("#" * 80)
    print("第2段 焊缝=2800 附近，附件网格位置(≡140 mod 280) 的边缘净距 off=%s:" % off)
    for k in range(1, 12):
        h = FIRST + k * STEP
        print("   h=%5.0f (980+%d×280)  |h-2800|=%5.0f  净距=%6.1f  %s"
              % (h, k, abs(h - 2800), edge_net(h, [2800], off),
                 "OK" if edge_net(h, [2800], off) > 100 else "冲突"))


report(42.5, CAND, "新口径 W_Rung/2")
report(455.0, CAND, "旧口径 W_LADDER_I/2")
report(42.5, CAND3, "新口径 W_Rung/2")
second_group(42.5)
second_group(455.0)
