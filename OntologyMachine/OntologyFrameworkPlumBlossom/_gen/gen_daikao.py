# -*- coding: utf-8 -*-
"""第二十五轮「待考全部落地」——卷之四·字画指迷 / 卷之五·拆字杂编 残卷长文。
自语料 design/_epub_dump.txt 逐字抽取（照录），去除编者脚注标号 [NN]，
生成 data.sql 追加段与 Java 静态表条目（相字·类目，单一真源）。
"""
import re, json, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
L = open(os.path.join(ROOT, 'design', '_epub_dump.txt'), encoding='utf-8').read().split('\n')

def seg(a, b):
    """抽取 a..b 行（1-based，含端点），去脚注标号，拼接。"""
    out = []
    for i in range(a, b + 1):
        t = L[i - 1]
        t = re.sub(r'\[\d+\]', '', t)          # 编者脚注标号
        t = t.strip()
        if not t:
            continue
        if re.fullmatch(r'[①-⑳]', t):          # 独立编者注号
            continue
        out.append(t)
    return ''.join(out)

# (类目, 条键, 出处, 起始行, 结束行)；正文自标题后一行起（含标题行则另处理）
SECTIONS = [
    ("六十四卦次第歌", "总诀", "卷之五·拆字杂编", 7205, 7217),
    ("系辞八卦类象歌", "总诀", "卷之五·拆字杂编", 7221, 7235),
    ("八反格",        "总诀", "卷之五·拆字杂编", 7295, 7297),
    ("四言独步",      "总诀", "卷之五·拆字杂编", 7301, 7417),
    ("五言作用歌",    "总诀", "卷之五·拆字杂编", 7421, 7459),
    ("别理篇",        "总诀", "卷之五·拆字杂编", 7463, 7485),
    ("六言剖断歌",    "总诀", "卷之五·拆字杂编", 7489, 7563),
    ("格物章",        "总诀", "卷之五·拆字杂编", 7569, 7601),
    ("物理论",        "总诀", "卷之五·拆字杂编", 7607, 7637),
    ("五行六神辨别论", "总诀", "卷之五·拆字杂编", 7641, 7657),
    ("金声章",        "总诀", "卷之五·拆字杂编", 7661, 7693),
]

# 正文本例作图象、文字阙如者——存目（照实标注，不臆补）
STUB = [
    ("浑天甲子定局", "总诀", "卷之五·拆字杂编", "（本版正文为图象，文字阙如；据正文『以上四宫属阳，皆从顺数／以上四宫属阴，皆从逆数』『右诀从下念上，一如点画卦爻法』存目。）"),
    ("后天时方",     "总诀", "卷之五·拆字杂编", "（本版正文阙如，仅存校勘记『①「败」，堂本作「散」②「生」，校本作「位」』；存目。）"),
]

# 卷之四·干支辨（五行歌并式内，五段）——正文＋举例
GANZHI_BIAN = [
    ("甲乙", "卷之四·字画指迷·干支辨",
     "直长为甲亦为寅，细短均为乙卯身。孤直心钩兼湿木，干支无位不须论。"
     "假如车字中央一直，彻上彻下，强健无损，则属阳，所以为甲木、寅木。余皆仿此。"
     "如幸字上一直下一直，皆短弱属阴，所以作乙木、卯木论也。凡一直，细弱不健，即长如车之直，亦作乙卯木看。"
     "其心钩舟船木，并三横两短木，一概不在干支论，因其不正故也。"),
    ("丙丁", "卷之四·字画指迷·干支辨",
     "撇长丙巳短为丁，午火同居短撇中。八字腾蛇兼四点，天干不合地支冲。"
     "假如庐字撇长，则取为丙火、巳火用。丙巳属阳，故用撇长者当之。余仿此。"),
    ("戊己", "卷之四·字画指迷·干支辨",
     "横中有直戊居中，画短横轻作己身。末点勾陈皆丑未，长而粗者戊辰同。"),
    ("庚辛", "卷之四·字画指迷·干支辨",
     "口字为金亦作申，挑从酉用捺从辛。空头顽钝囊金妙，不在干支数内寻。"),
    ("壬癸", "卷之四·字画指迷·干支辨",
     "点在当头作癸称，腹中为子要分明。点足为壬腰在亥，余皆野水不同群。"
     "假如「文」字一点，即为癸水。癸水乃雨露之源，因在上故也。余仿此。"),
]

rows = []
for cat, key, src, a, b in SECTIONS:
    rows.append((cat, key, seg(a, b), src))
for cat, key, src, txt in STUB:
    rows.append((cat, key, txt, src))
for key, src, txt in GANZHI_BIAN:
    rows.append(("干支辨", key, txt, src))

# 序号续 XiangZi_19_xx 起（18 类已占）；类目序号 + 类目内条目序号
sql_lines = []
java_lines = []
json_rows = []
cat_idx = {}
cat_item = {}
for cat, key, txt, src in rows:
    ci = cat_idx.setdefault(cat, len(cat_idx) + 19)
    cat_item[cat] = cat_item.get(cat, 0) + 1
    name = "XiangZi_%02d_%02d" % (ci, cat_item[cat])
    label = "相字·%s·%s" % (cat, key)
    sql_lines.append("('%s', '%s', '%s', '%s', '%s', '%s')" % (
        name, cat.replace("'", "''"), key.replace("'", "''"), txt.replace("'", "''"),
        src.replace("'", "''"), label.replace("'", "''")))
    jtxt = txt.replace("\\", "\\\\").replace('"', '\\"')
    java_lines.append('        putXz("%s", "%s", "%s");' % (cat, key, jtxt))
    json_rows.append({"name": name, "category": cat, "key": key, "text": txt, "source": src, "label": label})

out = os.path.join(ROOT, '_gen', 'out')
os.makedirs(out, exist_ok=True)
open(os.path.join(out, 'daikao_sql.txt'), 'w', encoding='utf-8').write(",\n".join(sql_lines))
open(os.path.join(out, 'daikao_java.txt'), 'w', encoding='utf-8').write("\n".join(java_lines))
json.dump(json_rows, open(os.path.join(ROOT, '_gen', 'daikao.json'), 'w', encoding='utf-8'),
          ensure_ascii=False, indent=1)
print("rows:", len(rows), "cats:", sorted(set(r[0] for r in rows)))
for r in json_rows:
    print(r["name"], r["category"], r["key"], len(r["text"]))
