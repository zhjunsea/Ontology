#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""第 39 轮·卷之三·断占总诀 逐篇照录（照录原文，非抽样）。"""
import json, os, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "src/main/java/com/ocean/ontologyframework/plumblossom/PlumBlossomOntologyJobWorker.java")
d = json.load(open(os.path.join(ROOT, "_gen/zl39.json"), encoding="utf-8"))


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
    ("JUAN_SAN_BA_GUA_DING_YIN_YANG", "卷之三·八卦定阴阳次序（乾统三男、坤统三女＋相生比和＋图象残注存目）"),
    ("JUAN_SAN_BIAN_GUA_SHI_BA_ZE", "卷之三·变卦式八则（逐例照录）"),
    ("JUAN_SAN_ZHAN_GUA_JUE", "卷之三·占卦诀（生体迟速＋克体阻日）"),
    ("JUAN_SAN_TI_YONG_HU_BIAN", "卷之三·体用互变之诀"),
    ("JUAN_SAN_TI_YONG_SHENG_KE", "卷之三·体用生克之诀（真生真克轻重）"),
    ("JUAN_SAN_TI_YONG_SHUAI_WANG", "卷之三·体用衰旺之诀"),
    ("JUAN_SAN_TI_YONG_DONG_JING", "卷之三·体用动静之诀"),
    ("JUAN_SAN_ZHAN_BU_ZUO_DUAN", "卷之三·占卜坐端之诀"),
    ("JUAN_SAN_WAN_WU_FU", "卷之三·万物赋"),
    ("JUAN_SAN_YIN_SHI_PIAN", "卷之三·饮食篇"),
    ("JUAN_SAN_GUAN_WU_XUAN_MIAO_GE", "卷之三·观物玄妙歌诀"),
    ("JUAN_SAN_ZHU_SHI_XIANG_YING_GE", "卷之三·诸事响应歌"),
    ("JUAN_SAN_ZHU_GUA_FAN_DUI", "卷之三·诸卦反对性情"),
    ("JUAN_SAN_ZHAN_WU_LEI_LI", "卷之三·占物类例"),
    ("JUAN_SAN_TI_YONG_DUAN_WU", "卷之三·体用断物之妙"),
    ("JUAN_SAN_WU_SHU_WEI_TI", "卷之三·物数为体诀"),
    ("JUAN_SAN_GUAN_WU_ZHU_JUE", "卷之三·观物诸诀（有体互变/看变爻/克应法/趣时诀/用易例）"),
    ("JUAN_SAN_WAN_WU_XI_NIAN", "卷之三·万物戏念"),
    ("JUAN_SAN_ZHAN_BU_SHI_YING_JUE", "卷之三·占卜十应诀全篇（序头＋正/互/变/方/日/刻/外/天时/地理/人事应＋右十应之理）"),
    ("JUAN_SAN_LUN_SHI_SHI_DA_YING", "卷之三·论事十大应（论日辰秘文）"),
    ("JUAN_SAN_GUA_YING", "卷之三·卦应全篇（八纯卦说卦引文＋性情论＋天文地理…策轨表）"),
]
body = "\n\n".join(emit(n, d[n], c) for n, c in CONSTS)

def method(n):
    parts = n.split("_")
    head = parts[0].lower()
    for p in parts[1:]:
        head += p[0].upper() + p[1:].lower()
    return head + "Note"


NOTE_METHODS = "\n".join(
    f'''    /** {c}（照录；接入节点 16）。 */
    private static String {method(n)}() {{
        return "{c}——" + {n};
    }}'''
    for n, c in CONSTS
)

WIRE = "\n".join(
    f'            appendLine(sb, "{c.split("·")[1][:12]}", {method(n)}());'
    for n, c in CONSTS
)

BLOCK = f"""    // ------------------------------------------------------------------
    // 第 39 轮·卷之三·断占总诀 逐篇照录（B128–B148，共 21 篇）：
    //   此前卷之三仅「观梅数诀序／占卜克应之诀／占卜十应诀逐应」三处成篇照录，
    //   余多沉于断法片段（bianGuaShiText/ZHAN_WU_YAO_CI/GUAN_WU_YONG_YI/GUA_YING map 等转述或表型载体），
    //   成篇原文未录。今依底本逐篇照录归位（接入节点 16「卷之三断占总诀」），与断法/表型并存不悖。
    //   L 行号依底本单制；凡「又诀」等底本自有之过渡题并录。
    // ------------------------------------------------------------------
{body}

{NOTE_METHODS}

"""
# 接线：逐篇 appendLine（在卷之二接线之后）
WIRE_ANCHOR = '            appendLine(sb, "卷之二加数屋宅", juanErJiaShuWuZhaiNote());'
WIRE_NEW = WIRE_ANCHOR + "\n" + WIRE

src = open(JAVA, encoding="utf-8").read()
ANCHOR = "    // ------------------------------------------------------------------\n    // 卷首·梅花易数序（第 28 轮补录 B74）"
if src.count(ANCHOR) != 1:
    print("ANCHOR", src.count(ANCHOR)); sys.exit(1)
if "JUAN_SAN_BA_GUA_DING_YIN_YANG" in src:
    print("already applied"); sys.exit(1)
src = src.replace(ANCHOR, BLOCK + ANCHOR, 1)

if src.count(WIRE_ANCHOR) != 1:
    print("WIRE", src.count(WIRE_ANCHOR)); sys.exit(1)
src = src.replace(WIRE_ANCHOR, WIRE_NEW, 1)

open(JAVA, "w", encoding="utf-8").write(src)
print("OK: 卷之三 21 篇 inserted & wired")
print("WIRE snippet:\n", WIRE[:600])
