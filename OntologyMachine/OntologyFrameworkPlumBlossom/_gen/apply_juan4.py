#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""第 40 轮·卷之四·字画指迷 成篇照录（赋/歌/叙/论/断法，非抽样）。"""
import json, os, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "src/main/java/com/ocean/ontologyframework/plumblossom/PlumBlossomOntologyJobWorker.java")
d = json.load(open(os.path.join(ROOT, "_gen/zl40.json"), encoding="utf-8"))


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


def method(n):
    parts = n.split("_")
    head = parts[0].lower()
    for p in parts[1:]:
        head += p[0].upper() + p[1:].lower()
    return head + "Note"


CONSTS = [
    ("JUAN_SI_ZI_HUA_ZHI_MI_XU", "卷之四·字画指迷序＋指迷赋"),
    ("JUAN_SI_XUAN_HUANG", "卷之四·玄黄克应歌＋玄黄叙＋玄黄歌"),
    ("JUAN_SI_FU", "卷之四·花押赋＋探玄赋＋齐景至理论"),
    ("JUAN_SI_ZI_HUA_JING_YAN", "卷之四·字画经验＋字体诗诀＋短诀（四季水笔/画有阴阳/八卦断）"),
    ("JUAN_SI_XIANG_ZI_XIN_YI", "卷之四·相字心易＋辨字式＋笔法筌蹄＋奴婢/阴人/隔手"),
    ("JUAN_SI_DUAN_FA", "卷之四·视势/象人/以时/以卦/以禽兽/取类/当添减/偏旁侵客＋问婚姻…行人"),
    ("JUAN_SI_LIU_SHEN", "卷之四·六神笔法＋笔画犯煞＋玄黄笔法歌"),
    ("JUAN_SI_WU_XING", "卷之四·五行体格式＋时辰断＋起六神卦诀＋辨别五行歌＋辨别六神歌＋五行歌并式"),
]
body = "\n\n".join(emit(n, d[n], c) for n, c in CONSTS)

NOTE_METHODS = "\n".join(
    f'''    /** {c}（照录；接入节点 16）。 */
    private static String {method(n)}() {{
        return "{c}——" + {n};
    }}'''
    for n, c in CONSTS
)

WIRE = "\n".join(
    f'            appendLine(sb, "{c.split("·")[1].split("＋")[0][:12]}", {method(n)}());'
    for n, c in CONSTS
)

BLOCK = f"""    // ------------------------------------------------------------------
    // 第 40 轮·卷之四·字画指迷 成篇照录（B149–B156，共 8 组）：
    //   卷之四「相字」术之断法（六神笔式/五行笔式/八卦断/笔画歌诀/相字短诀等）已由 putXz（263 条）
    //   与 DB `xiangzi_rule` 结构化承载（C12 相字别系）；惟**成篇之赋/歌/叙/论/经验/诗诀**（教训⑲）
    //   及未入载体之断法段（问婚姻…行人等）原文未录，今依底本成篇照录归位（接入节点 16）。
    //   L 行号依底本单制；篇末校勘记（「原本无此句，据校本补」以下）为整理者按语，照实排除。
    // ------------------------------------------------------------------
{body}

{NOTE_METHODS}

"""
WIRE_ANCHOR = '            appendLine(sb, "卦应", juanSanGuaYingNote());'
WIRE_NEW = WIRE_ANCHOR + "\n" + WIRE

src = open(JAVA, encoding="utf-8").read()
ANCHOR = "    // ------------------------------------------------------------------\n    // 卷首·梅花易数序（第 28 轮补录 B74）"
if src.count(ANCHOR) != 1:
    print("ANCHOR", src.count(ANCHOR)); sys.exit(1)
if "JUAN_SI_ZI_HUA_ZHI_MI_XU" in src:
    print("already applied"); sys.exit(1)
src = src.replace(ANCHOR, BLOCK + ANCHOR, 1)
if src.count(WIRE_ANCHOR) != 1:
    print("WIRE", src.count(WIRE_ANCHOR)); sys.exit(1)
src = src.replace(WIRE_ANCHOR, WIRE_NEW, 1)
open(JAVA, "w", encoding="utf-8").write(src)
print("OK: 卷之四 8 组 inserted & wired")
