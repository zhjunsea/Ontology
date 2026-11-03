#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""第 38 轮·续：卷之一·起卦法（卦数起例…八卦方位图）与风觉鸟占（含风觉/鸟/听声/形物/验色）照录。"""
import json, os, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "src/main/java/com/ocean/ontologyframework/plumblossom/PlumBlossomOntologyJobWorker.java")
d = json.load(open(os.path.join(ROOT, "_gen/zl38b.json"), encoding="utf-8"))


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
    // 第 38 轮·续（B121–B122）：卷之一·起卦法与风觉鸟占（照录）。
    //   ① 起卦法（卦数起例/爻以六除/互卦止用八卦/年月日时起例/物占数比/声音占/字占一字~十一字以上/
    //      丈尺占/尺寸物占/为人占/自己占/占动物/占静物/物卦起例/八卦万物属类/八卦方位图）；
    //   ② 风觉鸟占（风觉鸟占/风觉占/鸟占/听声音占/形物占/验色占/八卦所属内外动静之图）。
    //   此前仅以断法或片段引文散见，成篇原文未录，今照录归位（接入节点 16）。
    //   L 行号依底本单制（`wc -l design/_epub_dump.txt` = 5203）。
    // ------------------------------------------------------------------
{CONST1}

{CONST2}

    /** 卷之一·起卦法全篇（照录；接入节点 16）。 */
    private static String juanYiQiGuaFaNote() {{
        return "卷之一·起卦法（照录）——" + JUAN_YI_QI_GUA_FA;
    }}

    /** 卷之一·风觉鸟占全篇（照录；接入节点 16）。 */
    private static String juanYiFengJueNiaoNote() {{
        return "卷之一·风觉鸟占（照录）——" + JUAN_YI_FENG_JUE_NIAO;
    }}


""".format(
    CONST1=emit("JUAN_YI_QI_GUA_FA", d["JUAN_YI_QI_GUA_FA"], "卷之一·起卦法（照录）"),
    CONST2=emit("JUAN_YI_FENG_JUE_NIAO", d["JUAN_YI_FENG_JUE_NIAO"], "卷之一·风觉鸟占（照录）"),
)

src = open(JAVA, encoding="utf-8").read()
ANCHOR = "    // ------------------------------------------------------------------\n    // 卷首·梅花易数序（第 28 轮补录 B74）"
if src.count(ANCHOR) != 1:
    print("ANCHOR", src.count(ANCHOR)); sys.exit(1)
if "JUAN_YI_QI_GUA_FA" in src:
    print("already applied"); sys.exit(1)
src = src.replace(ANCHOR, BLOCK + ANCHOR, 1)

WIRE_OLD = '            appendLine(sb, "卷之一占例", juanYiZhanLiNote());'
WIRE_NEW = (WIRE_OLD
            + '\n            appendLine(sb, "卷之一起卦法", juanYiQiGuaFaNote());'
            + '\n            appendLine(sb, "卷之一风觉鸟占", juanYiFengJueNiaoNote());')
if src.count(WIRE_OLD) != 1:
    print("WIRE", src.count(WIRE_OLD)); sys.exit(1)
src = src.replace(WIRE_OLD, WIRE_NEW, 1)

open(JAVA, "w", encoding="utf-8").write(src)
print("OK: 卷之一起卦法 + 风觉鸟占 inserted & wired")
