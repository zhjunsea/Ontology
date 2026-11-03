#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
三大原文映射「反向核验」（第 35 轮）：代码 → 底本，查「多字/讹字」。

正向（第 34 轮 verify_gua_ying.py）查「底本有而代码无」＝漏录；
反向（本器）查「代码有而底本无」＝增字/讹字/自撰。

对 GUA_YING：先剥去每段前之「字段名」前缀（天文/地理/…），再按 、；，。切词，
              每词（CJK）须为**底本卦应章** CJK 串之子串，否则列「疑增/讹」。
对 OBJECT_MIAO_JUE / OBJECT_PLAY_NOTE：整键值与对应底本章节比对，切词核之。

用法：python _gen/verify_maps_reverse.py
"""
import os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "src/main/java/com/ocean/ontologyframework/plumblossom/PlumBlossomOntologyJobWorker.java")
EPUB = os.path.join(ROOT, "design/_epub_dump.txt")
CJK = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]")
TRIGRAMS = ["乾", "坤", "震", "巽", "坎", "离", "艮", "兑"]
FIELDS = ["天文", "天气", "地理", "方所", "人物", "凶盗", "官贵", "身体", "生育", "生产",
          "性情", "婚姻", "声音", "信音", "事意", "疾病", "病疾", "附药", "药", "宴会",
          "食物", "谷果", "禽兽", "衣服", "衣物", "器用", "财", "财物", "禄", "字", "色",
          "策", "轨", "五味", "果品", "音信", "饮食", "缠带"]


def cjk(s):
    return "".join(CJK.findall(s))


def section(txt, start_marker, end_marker):
    i = txt.find(start_marker)
    j = txt.find(end_marker, i)
    return txt[i:j if j > 0 else len(txt)]


def java_map(name):
    java = open(JAVA, encoding="utf-8").read()
    out = {}
    for mm in re.finditer(name + r'\.put\("([乾兑离震巽坎艮坤])",\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+?)\);', java):
        tri = mm.group(1)
        body = "".join(re.findall(r'"((?:[^"\\]|\\.)*)"', mm.group(2)))
        out[tri] = body
    return out


def reverse(value, hay, strip_fields):
    bad = []
    for seg in re.split(r"[；;]", value):
        if strip_fields:
            for f in FIELDS:
                if seg.startswith(f):
                    seg = seg[len(f):]
                    break
        for tok in re.split(r"[、，。]", seg):
            t = cjk(tok)
            if len(t) >= 2 and t not in hay:
                bad.append(t)
    return bad


def main():
    txt = open(EPUB, encoding="utf-8").read()
    gy_hay = cjk(section(txt, "卦应（与前八卦属类", "诸事响应歌"))
    mj_hay = cjk(section(txt, "观物玄妙歌诀", "诸事响应歌"))
    pn_hay = cjk(section(txt, "万物戏念数中不可常为之", "占卜十应诀"))

    total = 0
    gy = java_map("GUA_YING")
    for tri in TRIGRAMS:
        bad = reverse(gy.get(tri, ""), gy_hay, True)
        if bad:
            total += len(bad)
            print(f"[GUA_YING {tri}] 疑增/讹 {bad}")
    mj = java_map("OBJECT_MIAO_JUE")
    for tri, val in mj.items():
        bad = reverse(val, mj_hay, False)
        if bad:
            total += len(bad)
            print(f"[OBJECT_MIAO_JUE {tri}] 疑增/讹 {bad}")
    pn = java_map("OBJECT_PLAY_NOTE")
    for tri, val in pn.items():
        bad = reverse(val, pn_hay, False)
        if bad:
            total += len(bad)
            print(f"[OBJECT_PLAY_NOTE {tri}] 疑增/讹 {bad}")
    print(f"\n[INFO] 反向（代码→底本）疑增/讹合计 = {total}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
