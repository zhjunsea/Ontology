#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""第 41 轮·卷之五·拆字杂编 成篇照录（歌/赋/论/诀/章/占例，非抽样）。"""
import json, os, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "src/main/java/com/ocean/ontologyframework/plumblossom/PlumBlossomOntologyJobWorker.java")
d = json.load(open(os.path.join(ROOT, "_gen/zl41.json"), encoding="utf-8"))


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


# (常量名, 注释, 节点16 接线标签)
CONSTS = [
    ("JUAN_WU_WU_XING_QUAN_BEI", "卷之五·拆字杂编·五行全备＋六神形式＋八卦辨", "五行全备"),
    ("JUAN_WU_SHEN_SHA", "卷之五·神煞诸神（贵神/喜神/福星/文星/印星/马星/禄神/会神/生神/亡神/家神/官符/灾煞/天狗煞/科名星/丧门/空亡/宜神/忌神/主神/用神）", "神煞诸神"),
    ("JUAN_WU_QI_YAN_ZUO_YONG_GE", "卷之五·七言作用歌", "七言作用歌"),
    ("JUAN_WU_BI_LI_GE_XI_JIANG_YUE", "卷之五·比例歌＋西江月", "比例歌·西江月"),
    ("JUAN_WU_ZHAN_LI", "卷之五·易理玄微＋断扇占＋买香占＋古人相字＋断富贵贫贱要诀", "断例诸占"),
    ("JUAN_WU_BIAO", "卷之五·五行四时旺相休囚表＋论八卦性情＋八卦取象＋六十甲子纳音歌＋六十四卦次第歌＋《系辞》八卦类象歌＋浑天甲子定局＋后天时方＋八反格", "卦象诸表"),
    ("JUAN_WU_SI_YAN_DU_BU", "卷之五·四言独步", "四言独步"),
    ("JUAN_WU_WU_YAN_ZUO_YONG_GE", "卷之五·五言作用歌", "五言作用歌"),
    ("JUAN_WU_BIE_LI_PIAN", "卷之五·别理篇", "别理篇"),
    ("JUAN_WU_LIU_YAN_POU_DUAN_GE", "卷之五·六言剖断歌", "六言剖断歌"),
    ("JUAN_WU_GE_WU_ZHANG", "卷之五·格物章", "格物章"),
    ("JUAN_WU_WU_LI_LUN", "卷之五·物理论", "物理论"),
    ("JUAN_WU_WU_XING_LIU_SHEN_LUN", "卷之五·五行六神辨别论", "五行六神辨别论"),
    ("JUAN_WU_JIN_SHENG_ZHANG", "卷之五·金声章", "金声章"),
]
body = "\n\n".join(emit(n, d[n], c) for n, c, _ in CONSTS)

NOTE_METHODS = "\n".join(
    f'''    /** {c}（照录；接入节点 16）。 */
    private static String {method(n)}() {{
        return "{c}——" + {n};
    }}'''
    for n, c, _ in CONSTS
)

WIRE = "\n".join(
    f'            appendLine(sb, "{lab}", {method(n)}());'
    for n, _, lab in CONSTS
)

BLOCK = f"""    // ------------------------------------------------------------------
    // 第 41 轮·卷之五·拆字杂编 成篇照录（B157–B170，共 14 组）：
    //   卷之五「拆字杂编」全卷为「相字」专术，其断法（玄黄克应/六神笔式/五行笔式/笔画歌诀/
    //   相字短诀/分类断法…）已由 putXz（264 条）与 DB `xiangzi_rule` 结构化承载（C12 相字别系）；
    //   惟**成篇之歌/赋/叙/论/诀/章/占例**（教训⑲）须成篇照录，与卷之三、卷之四同例，
    //   今依底本成篇照录归位（接入节点 16）。
    //   底本卷之五＝「拆字杂编」；「五行全备」诸篇起，至「金声章…请明章中之理」止，
    //   下接「附录·一撮金」。篇中「后天时方」后①②校勘二则、及各篇小字夹注，皆底本正文，照录不删。
    // ------------------------------------------------------------------
{body}

{NOTE_METHODS}

"""
WIRE_ANCHOR = '            appendLine(sb, "五行体格式", juanSiWuXingNote());'
WIRE_NEW = WIRE_ANCHOR + "\n" + WIRE

src = open(JAVA, encoding="utf-8").read()
ANCHOR = "    // ------------------------------------------------------------------\n    // 卷首·梅花易数序（第 28 轮补录 B74）"
if src.count(ANCHOR) != 1:
    print("ANCHOR", src.count(ANCHOR)); sys.exit(1)
if "JUAN_WU_WU_XING_QUAN_BEI" in src:
    print("already applied"); sys.exit(1)
src = src.replace(ANCHOR, BLOCK + ANCHOR, 1)
if src.count(WIRE_ANCHOR) != 1:
    print("WIRE", src.count(WIRE_ANCHOR)); sys.exit(1)
src = src.replace(WIRE_ANCHOR, WIRE_NEW, 1)
open(JAVA, "w", encoding="utf-8").write(src)
print("OK: 卷之五 14 组 inserted & wired")
