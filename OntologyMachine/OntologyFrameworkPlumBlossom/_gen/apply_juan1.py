#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""第 38 轮：把卷之一·占例十则（先天五例＋后天五例）以「照录常量」写入核心 Java 类，并接线节点 16。"""
import json, io, sys, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "src/main/java/com/ocean/ontologyframework/plumblossom/PlumBlossomOntologyJobWorker.java")
ZL = os.path.join(ROOT, "_gen/zl38.json")

d = json.load(open(ZL, encoding="utf-8"))
s1, s2 = d["XIAN_TIAN"], d["HOU_TIAN"]


def chunk(s, w=46):
    return [s[i:i + w] for i in range(0, len(s), w)]


def esc(t):
    return t.replace("\\", "\\\\").replace('"', '\\"')


def emit(name, s, comment):
    parts = chunk(s)
    lines = [f"    private static final String {name} ="]
    for i, p in enumerate(parts):
        pre = "        " if i == 0 else "      + "
        suf = ";" if i == len(parts) - 1 else ""
        lines.append(f'{pre}"{esc(p)}"{suf}')
    lines[-1] += f"   // {comment}"
    return "\n".join(lines)


BLOCK = """    // ------------------------------------------------------------------
    // 第 38 轮·「相对原著之遗漏」补录（B119–B120）：卷之一·占例十则（照录）。
    //   卷之一「占例」原为先天五例（观梅占/牡丹占/邻夜扣门借物占/今日动静如何/西林寺牌额占）
    //   ＋后天五例（老人有忧色占/少年有喜色占/牛哀鸣占/鸡悲鸣占/枯枝坠地占），共十则；
    //   此前仅以转述散见断法（占牡丹/马践等），成篇原文未录，今照录归位（接入节点 16「卷之一占例」）。
    //   L 行号依底本单制（`wc -l design/_epub_dump.txt` = 5203）。
    // ------------------------------------------------------------------
{CONST1}

{CONST2}

    /** 卷之一·占例十则（先天五例＋后天五例；照录；接入节点 16）。 */
    private static String juanYiZhanLiNote() {{
        return "卷之一·占例（先天之数）——" + JUAN_YI_XIAN_TIAN_LI
                + "；卷之一·占例（后天之数）——" + JUAN_YI_HOU_TIAN_LI;
    }}


""".format(
    CONST1=emit("JUAN_YI_XIAN_TIAN_LI", s1, "卷之一·先天五占例（L375–460，照录）"),
    CONST2=emit("JUAN_YI_HOU_TIAN_LI", s2, "卷之一·后天五占例（L463–520，照录）"),
)

src = open(JAVA, encoding="utf-8").read()

ANCHOR = "    // ------------------------------------------------------------------\n    // 卷首·梅花易数序（第 28 轮补录 B74）"
if src.count(ANCHOR) != 1:
    print("ANCHOR count=", src.count(ANCHOR)); sys.exit(1)
if "JUAN_YI_XIAN_TIAN_LI" in src:
    print("already applied"); sys.exit(1)

src = src.replace(ANCHOR, BLOCK + ANCHOR, 1)

WIRE_OLD = '            appendLine(sb, "洞玄歌序跋", dongXuanYinBaNote());'
WIRE_NEW = WIRE_OLD + '\n            appendLine(sb, "卷之一占例", juanYiZhanLiNote());'
if src.count(WIRE_OLD) != 1:
    print("WIRE count=", src.count(WIRE_OLD)); sys.exit(1)
src = src.replace(WIRE_OLD, WIRE_NEW, 1)

open(JAVA, "w", encoding="utf-8").write(src)
print("OK: constants inserted + wired at node 16")
