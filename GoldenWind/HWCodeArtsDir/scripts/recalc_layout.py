"""按主体表真实筒节数据重算中段四段的附件/灯布局（复刻引擎口径）。"""

SEC = {
    "中段第1段(第2段)": dict(secH=18480, platH=17230,
        welds=[215,2800,5600,8400,11200,14000,16680,18305,18480]),
    "中段第2段(第3段)": dict(secH=19600, platH=18350,
        welds=[175,2800,5600,8400,11200,14000,16800,19455,19600]),
    "中段第3段(第4段)": dict(secH=24920, platH=23670,
        welds=[145,2800,5600,8400,11200,14000,16800,19600,22400,24800,24920]),
    "中段第4段(第5段)": dict(secH=24640, platH=23390,
        welds=[120,2800,5600,8400,11200,14000,16800,19600,22400,24530,24640]),
}

FIRST = 980.0
SECOND_LAST_MIN, SECOND_LAST_MAX = 1400.0, 1960.0


def edge_net(h, welds, off=455.0):
    """附件(半高off)边缘到最近焊缝的净间隙（可为负=重叠）。"""
    return min(abs(h - w) for w in welds) - off


def enumerate_layouts(platH, welds, steps, off=455.0):
    bandLo, bandHi = platH - SECOND_LAST_MAX, platH - SECOND_LAST_MIN
    valid, invalid = [], []

    def rec(seq):
        h = seq[-1]
        if h >= bandLo and h <= bandHi:
            (valid if all(edge_net(x, welds, off) > 100 for x in seq) else invalid).append(list(seq))
        for s in steps:
            nh = h + s
            if nh <= bandHi:
                rec(seq + [nh])

    rec([FIRST])
    return valid, invalid


for name, d in SEC.items():
    platH, welds = d["platH"], d["welds"]
    print("=" * 70)
    print(f"{name}  secH={d['secH']}  platH={platH}")
    print(f"  welds={welds}")
    print(f"  平台下终止带 lastToPlatform∈[{SECOND_LAST_MIN},{SECOND_LAST_MAX}] ⇒ 末组 h∈[{platH-SECOND_LAST_MAX},{platH-SECOND_LAST_MIN}]")
    for label, steps in [("候选{1680,1960}(引擎现值)", [1680.0, 1960.0]),
                         ("候选{1400,1680,1960}", [1400.0, 1680.0, 1960.0])]:
        v, iv = enumerate_layouts(platH, welds, steps)
        print(f"  [{label}] 可行布局数={len(v)}  违规布局数={len(iv)}")
        # 展示第2组的边缘净间隙
        print("    第2组尝试：", end="")
        for s in steps:
            h2 = FIRST + s
            print(f"h2={h2:.0f}(+{s:.0f}) 净距={edge_net(h2, welds):.0f}  ", end="")
        print()
        if v:
            best = max(v, key=len)
            print(f"    最优(组数最多): {best}")

print("\n" + "=" * 70)
print("灯布置（逐螺柱 |灯位±250 − w| > 100，首灯∈[2600,3000]，间距首选 10000、≥5000）")
LED_OFF = 250.0
for name, d in SEC.items():
    platH, welds = d["platH"], d["welds"]

    def stud_ok(led):
        return all(abs(led - LED_OFF - w) > 100 and abs(led + LED_OFF - w) > 100 for w in welds)

    first = None
    h = 2600.0
    while h <= 3000.0:
        if stud_ok(h):
            first = h
            break
        h += 10
    lights = []
    if first is not None:
        lights.append(first)
        cur = first
        while True:
            nxt = cur + 10000
            if nxt > platH:
                break
            chosen = None
            c = nxt
            while c >= cur + 5000:
                if stud_ok(c):
                    chosen = c
                    break
                c -= 10
            if chosen is None:
                break
            lights.append(chosen)
            cur = chosen
    print(f"  {name}: 首灯={first}  灯位={lights}")
