import json, sys, re
from collections import Counter

src = r"D:\work\Ontology\OntologyMachine\HWCodeArtsDir\zwds_hunt_results.json"
dst = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkZiWeiDouShu\src\test\resources\ziweidoushu\hunt_cases.json"
cap = int(sys.argv[1]) if len(sys.argv) > 1 else 150

allrows = json.load(open(src, "r", encoding="utf-8"))

def grade(r):
    g = r.get("patternGrade", "") or ""
    for t in ["中上格", "上格", "下格", "中格", "常格", "贵局", "富局", "贫贱"]:
        if t in g:
            return t
    return "NA"

def tokens(r):
    s = (r.get("patternResult") or "") + ";" + (r.get("patternGrade") or "")
    t = set()
    for seg in re.split("[；;、\n]", s):
        seg = seg.strip()
        m = re.match(r"^([\u4e00-\u9fa5A-Za-z0-9·　 ]{2,14})(?:（|：|\[|$)", seg)
        if m:
            t.add("tok:" + m.group(1).strip())
    for m in re.finditer(r"([\u4e00-\u9fa5]{2,8}格)", s):
        t.add("ge:" + m.group(1))
    for m in re.finditer(r"([\u4e00-\u9fa5]{2,6}论)", s):
        t.add("lun:" + m.group(1))
    return t

def dims(r):
    d = set()
    for h in (r.get("hits") or []):
        d.add("hit:" + str(h))
    d.add("bureau:" + str(r.get("bureau")))
    d.add("ming:" + str(r.get("ming")))
    d.add("yy:" + str(r.get("yinyang_gender")))
    d.add("gender:" + str(r.get("gender")))
    d.add("grade:" + grade(r))
    d.add("alert:" + str(r.get("alert", "")))
    d.add("empty:" + str(r.get("empty_palace")))
    d.add("pair_mb:" + str(r.get("ming")) + "|" + str(r.get("bureau")))
    d.add("pair_yg:" + str(r.get("yinyang_gender")) + "|" + grade(r))
    d.add("pair_bg:" + str(r.get("bureau")) + "|" + grade(r))
    d |= tokens(r)
    return d

allkeys = set()
for r in allrows:
    allkeys |= dims(r)

pool = list(allrows)
covered = set()
chosen = []
while len(chosen) < cap:
    best = None
    bg = 0
    for r in pool:
        g = len(dims(r) - covered)
        if g > bg:
            bg = g
            best = r
    if best is None or bg <= 0:
        break
    chosen.append(best)
    covered |= dims(best)
    pool.remove(best)

# 随机补充更多真实命例（增加对 judge*/格局子条件的覆盖）
import random
target = int(sys.argv[2]) if len(sys.argv) > 2 else len(chosen)
chosen_births = set(str(c.get("birth")) for c in chosen)
extra_pool = [r for r in allrows if str(r.get("birth")) not in chosen_births]
random.seed(42)
random.shuffle(extra_pool)
for r in extra_pool:
    if len(chosen) >= target:
        break
    chosen.append(r)
    chosen_births.add(str(r.get("birth")))

out = []
for i, r in enumerate(chosen):
    st = "成人女" if r.get("gender") == "女" else "成人男"
    depth = "本命+大限+流年" if (i % 6 == 0) else "本命"
    out.append({
        "id": "hunt_" + str(r.get("birth")).replace(" ", "-").replace(":", "-"),
        "birth": r.get("birth"),
        "gender": r.get("gender"),
        "subjectType": st,
        "depth": depth,
        "mode": "直接排盘",
        "expectMing": r.get("ming"),
        "expectBureau": r.get("bureau"),
    })

json.dump(out, open(dst, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
c = Counter()
for k in allkeys:
    c[k.split(":")[0]] += 1
print("total_hunt=%d distinct_dimkeys=%d chosen=%d covered=%d" % (len(allrows), len(allkeys), len(chosen), len(covered)))
print("dim_counts=" + str(dict(c)))
print("uncovered_dim=%d" % len(allkeys - covered))

