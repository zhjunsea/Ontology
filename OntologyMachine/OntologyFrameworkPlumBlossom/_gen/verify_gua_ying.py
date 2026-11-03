#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
卦应（GUA_YING）逐字段比对：底本 卦应表 ↔ Java GUA_YING 常量（第 34 轮）。

底本区段：design/_epub_dump.txt 之「卦应」章（卷之三 L1847–2132）。
  结构：`乾` 起行（顶格）→ 若干 `［字段］：值` 行（值可跨行/含脚注 [n]）。
Java：PlumBlossomOntologyJobWorker.GUA_YING（八键，值为 `；` 连接的类象串）。

判定：底本每字段之值（CJK 归一、按 、。， 切词）须能在 Java 对应键之 CJK 串中
      找到；找不到者列为「漏」，另列底本有而 Java 无之**字段名**。

用法：python _gen/verify_gua_ying.py
"""
import os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
EPUB = os.path.join(ROOT, "design/_epub_dump.txt")
JAVA = os.path.join(ROOT, "src/main/java/com/ocean/ontologyframework/plumblossom/PlumBlossomOntologyJobWorker.java")

CJK = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]")
TRIGRAMS = ["乾", "坤", "震", "巽", "坎", "离", "艮", "兑"]


def cjk(s):
    return "".join(CJK.findall(s))


def strip_footnote(s):
    return re.sub(r"\[\d+\]", "", s)


def strip_editor(s):
    """去底本编者案语（非类象正文）：如 乾·禽兽「（余备载前）」；及旧误判项。"""
    return re.sub(r"余备载前|上为雨下为露", "", s)


def load_epub_gua_ying():
    lines = open(EPUB, encoding="utf-8").read().split("\n")
    # 定位「卦应」章：从含「卦应（与前八卦属类」到「兑」段末（下一非卦应章）。
    start = None
    for i, l in enumerate(lines):
        if l.strip().startswith("卦应（与前八卦属类"):
            start = i
            break
    assert start is not None, "未找到卦应章"
    block = lines[start:start + 760]
    # 解析
    data = {t: {} for t in TRIGRAMS}
    cur = None
    field = None
    for raw in block:
        l = strip_footnote(raw.rstrip())
        if not l.strip():
            continue
        m = re.match(r"^［(.+?)］[:：]\s*(.*)$", l.strip())
        if m:
            if cur is None:
                continue
            field = m.group(1)
            data[cur][field] = m.group(2)
            continue
        if l.strip() in TRIGRAMS and (l[0] in TRIGRAMS):
            cur = l.strip(); field = None; continue
        # 续行：并入当前 field（遇下一节标题/校勘注则止）
        if re.search(r"此节疑有脱文|^梅花易数卷之|诸事响应歌|观物玄妙歌诀|占卜十应诀", l):
            break
        if cur and field and not re.match(r"^[乾兑离震巽坎艮坤]为", l):
            data[cur][field] += l.strip()
    return data


def load_java_gua_ying():
    java = open(JAVA, encoding="utf-8").read()
    m = re.search(r"GUA_YING\.put\(\"([^\"]+)\",\s*((?:\"[^\"]*\"\s*\+?\s*)+)\);", java)
    # 逐键解析
    out = {}
    for mm in re.finditer(r"GUA_YING\.put\(\"([乾兑离震巽坎艮坤])\",\s*((?:\"[^\"]*\"\s*\+?\s*)+?)\);", java):
        tri = mm.group(1)
        body = "".join(re.findall(r'"((?:[^"\\]|\\.)*)"', mm.group(2)))
        out[tri] = body
    return out


def main():
    data = load_epub_gua_ying()
    jg = load_java_gua_ying()
    bad = 0
    for tri in TRIGRAMS:
        jc = cjk(jg.get(tri, ""))
        fields = data[tri]
        print(f"== {tri} ==  底本字段 {list(fields.keys())}")
        for f, val in fields.items():
            toks = [cjk(t) for t in re.split(r"[、。，；]", strip_editor(val)) if cjk(t)]
            miss = [t for t in toks if t and t not in jc]
            if miss:
                bad += 1
                print(f"   ✗ ［{f}］漏: {miss}   (底本值={val})")
    # 整栏核（第 35 轮）：底本每字段「整值」须为代码卦 CJK 之子串——以捕「错栏/漏栏」
    # （逐词核之盲：某词出现在他栏亦算命中，惟整栏核方见其错置）。
    full_bad = 0
    for tri in TRIGRAMS:
        jc = cjk(jg.get(tri, ""))
        for f, val in data[tri].items():
            full = strip_editor(cjk(val))   # 去底本编者案语
            if len(full) >= 4 and full not in jc:
                full_bad += 1
                print(f"   ⊘ ［{tri}·{f}］整栏未合: {full}")
    print(f"\n[INFO] 漏录字段数 = {bad}；整栏未合 = {full_bad}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
