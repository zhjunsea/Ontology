#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""第 38 轮·续 2：卷之二·体用总诀 / 十八占 / 复明诸应 / 观物洞玄歌正文 / 起卦加数例·屋宅占·器物占（照录）。"""
import json, os, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "src/main/java/com/ocean/ontologyframework/plumblossom/PlumBlossomOntologyJobWorker.java")
d = json.load(open(os.path.join(ROOT, "_gen/zl38c.json"), encoding="utf-8"))


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


CONSTS = [
    ("JUAN_ER_TI_YONG_ZONG_JUE", "卷之二·体用总诀（照录）"),
    ("JUAN_ER_SHI_BA_ZHAN", "卷之二·十八占（照录）"),
    ("JUAN_ER_FU_MING_ZHU_YING", "卷之二·复明诸应（照录）"),
    ("JUAN_ER_DONG_XUAN_GE", "卷之二·观物洞玄歌正文（照录）"),
    ("JUAN_ER_QI_GUA_JIA_SHU", "卷之二·起卦加数例·屋宅占·器物占（照录）"),
]
body = "\n\n".join(emit(n, d[n], c) for n, c in CONSTS)

BLOCK = f"""    // ------------------------------------------------------------------
    // 第 38 轮·续 2（B123–B127）：卷之二·体用总诀/十八占/复明诸应/观物洞玄歌正文/起卦加数例+屋宅占（照录）。
    //   此前或仅以断法/片段引文散见（体用总诀 L1023–1051、天时占第一…），成篇原文未录；
    //   今一律照录归位（接入节点 16）。L 行号依底本单制。
    // ------------------------------------------------------------------
{body}

    /** 卷之二·体用总诀（照录；接入节点 16）。 */
    private static String juanErTiYongZongJueNote() {{
        return "卷之二·体用总诀（照录）——" + JUAN_ER_TI_YONG_ZONG_JUE;
    }}

    /** 卷之二·十八占（照录；接入节点 16）。 */
    private static String juanErShiBaZhanNote() {{
        return "卷之二·十八占（照录）——" + JUAN_ER_SHI_BA_ZHAN;
    }}

    /** 卷之二·复明诸应（照录；接入节点 16）。 */
    private static String juanErFuMingNote() {{
        return "卷之二·复明诸应（照录）——" + JUAN_ER_FU_MING_ZHU_YING;
    }}

    /** 卷之二·观物洞玄歌正文（照录；接入节点 16）。 */
    private static String juanErDongXuanGeNote() {{
        return "卷之二·观物洞玄歌（照录）——" + JUAN_ER_DONG_XUAN_GE;
    }}

    /** 卷之二·起卦加数例·屋宅占·器物占（照录；接入节点 16）。 */
    private static String juanErJiaShuWuZhaiNote() {{
        return "卷之二·起卦加数例·屋宅占·器物占（照录）——" + JUAN_ER_QI_GUA_JIA_SHU;
    }}


"""

src = open(JAVA, encoding="utf-8").read()
ANCHOR = "    // ------------------------------------------------------------------\n    // 卷首·梅花易数序（第 28 轮补录 B74）"
if src.count(ANCHOR) != 1:
    print("ANCHOR", src.count(ANCHOR)); sys.exit(1)
if "JUAN_ER_TI_YONG_ZONG_JUE" in src:
    print("already applied"); sys.exit(1)
src = src.replace(ANCHOR, BLOCK + ANCHOR, 1)

WIRE_OLD = '            appendLine(sb, "卷之一风觉鸟占", juanYiFengJueNiaoNote());'
WIRE_NEW = (WIRE_OLD
            + '\n            appendLine(sb, "卷之二体用总诀", juanErTiYongZongJueNote());'
            + '\n            appendLine(sb, "卷之二十八占", juanErShiBaZhanNote());'
            + '\n            appendLine(sb, "卷之二复明诸应", juanErFuMingNote());'
            + '\n            appendLine(sb, "卷之二洞玄歌", juanErDongXuanGeNote());'
            + '\n            appendLine(sb, "卷之二加数屋宅", juanErJiaShuWuZhaiNote());')
if src.count(WIRE_OLD) != 1:
    print("WIRE", src.count(WIRE_OLD)); sys.exit(1)
src = src.replace(WIRE_OLD, WIRE_NEW, 1)

open(JAVA, "w", encoding="utf-8").write(src)
print("OK: 卷之二 五篇 inserted & wired")
