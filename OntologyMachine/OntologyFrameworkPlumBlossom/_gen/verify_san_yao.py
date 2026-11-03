#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
三要灵应篇（卷之二）逐兆比对：底本「类应」兆象 ↔ Java SAN_YAO_LING_YING（第 36 轮）。

底本区段：design/_epub_dump.txt 之「三要灵应篇」正文（类应诸兆，L2319–2639）。
  - 依 C12，「拆字之应」一段（若见物形 … 右为拆字之应）不纳入本核（属相字别系）。
  - 序 / 导论 / 篇末总论 / 跋为散文，非「兆象」，另核（见 SAN_YAO_LING_YING_GAILUN / SAN_YAO_XU）。

判定：底本每「兆象」句（按 。； 分句、再按 、， 分词组，词组 CJK 归一后去前导连词）
      须能在 Java SAN_YAO_LING_YING 之值串（CJK 归一）中找到；找不到者列「漏」。

用法：python _gen/verify_san_yao.py
"""
import os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
EPUB = os.path.join(ROOT, "design/_epub_dump.txt")
JAVA = os.path.join(ROOT, "src/main/java/com/ocean/ontologyframework/plumblossom/PlumBlossomOntologyJobWorker.java")

CJK = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]")
# 前导连词/助语，去之再核（底本多用，代码多略）
LEAD = ["是以", "故", "至于", "及夫", "若逢", "若见", "若", "或", "虽云", "如", "而", "又", "夫", "其", "至于"]


def cjk(s):
    return "".join(CJK.findall(s))


def strip_lead(t):
    for p in LEAD:
        if t.startswith(p) and len(t) > len(p):
            return t[len(p):]
    return t


def load_epub_body():
    lines = open(EPUB, encoding="utf-8").read().split("\n")
    start = next(i for i, l in enumerate(lines) if l.strip() == "三要灵应篇")
    seg = "".join(lines[start:start + 340])
    seg = re.sub(r"\[\d+\]", "", seg)
    seg = seg.replace("三要灵应篇", "")
    # 去拆字之应段（C12 不纳）
    seg = re.sub(r"若见物形.*?右为拆字之应", "", seg, flags=re.S)
    # 去序/导论/总论/跋/类结语等非兆象行
    seg = re.sub(r"三要者，.*?故曰三要灵应也。", "", seg, flags=re.S)
    seg = re.sub(r"又推五行.*?诚此理之无异。", "", seg, flags=re.S)
    seg = re.sub(r"右[^。]*。", "", seg)
    return seg


def load_java_vals(include_prose=True):
    java = open(JAVA, encoding="utf-8").read().replace("\n", " ")
    vals = "".join(re.findall(r'SAN_YAO_LING_YING\.put\("[^"]*",\s*"((?:[^"\\]|\\.)*)"\)', java))
    if include_prose:
        for const in ("SAN_YAO_LING_YING_GAILUN", "SAN_YAO_LING_YING_ZONGLUN", "SAN_YAO_XU"):
            mm = re.search(const + r"\s*=\s*((?:\"(?:[^\"\\]|\\.)*\"\s*\+?\s*)+);", java)
            if mm:
                vals += "".join(re.findall(r'"((?:[^"\\]|\\.)*)"', mm.group(1)))
    return vals


def main():
    seg = load_epub_body()
    j_map = cjk(load_java_vals(include_prose=False))
    j_all = cjk(load_java_vals(include_prose=True))
    miss_map, miss_all, seen = [], [], set()
    for sent in re.split(r"[。；？]", seg):
        for tok in re.split(r"[、，]", sent):
            t = cjk(tok)
            if len(t) < 3 or t in seen:
                continue
            seen.add(t)
            ts = strip_lead(t)
            if t not in j_all and ts not in j_all:
                miss_all.append(t)
            if t not in j_map and ts not in j_map:
                miss_map.append(t)
    # map-only 未录者，多为「引言/结语/总论/诫语」等非兆象散文（已由 GAILUN/ZONGLUN 照录）
    print(f"[INFO] 兆象未录（map 断法索引）= {len(miss_map)}：{miss_map}")
    print(f"[INFO] 原著文字未录（map＋正文照录 合并）= {len(miss_all)}")
    for m in miss_all:
        print("   ✗ 漏:", m)
    return 0 if not miss_all else 1


if __name__ == "__main__":
    sys.exit(main())
