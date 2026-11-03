#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
梅花易数「周易通用语料」（yijing_* 二表）外部核校（第三十三轮·新增）。

目的：以两部独立善本核 `data.sql` 之
        - yijing_line_dictum   (384 爻辞)  ← 武英殿本《御纂周易折中》爻辞（wuying-lines.json）
        - yijing_hexagram_dictum (64 卦辞) ← ctext.org《周易》卦辞（ctext_zhouyi_64gua.json）

规范化：一律 繁→简（zhconv）；去爻位/卦名前缀（至首个「：」）；只保留 CJK 字，
        去全部标点空白。（周易标点为编者所加，故只校「字」，不校句读。）

判定：字序列须与善本全等；异者列出。

用法：python _gen/verify_zhouyi.py
退出码：0=全合；1=有异。
"""
import json
import os
import re
import sqlite3  # noqa (unused, kept for symmetry)
import sys

from zhconv import convert

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SQL = os.path.join(ROOT, "ontology/database/data.sql")
WUYING = os.path.join(ROOT, "_gen/zy_ext/wuying-lines.json")     # 武英殿本 384 爻辞
CTEXT = os.path.join(ROOT, "_gen/zy_ext/ctext_zhouyi_64gua.json")  # ctext 卦辞+爻辞
BOLL = os.path.join(ROOT, "_gen/zy_ext/zhouyi_64gua.json")         # bollwarm 卦辞+爻辞

CJK = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]")
# 历代异体/通假 归并（周易传世本常见；左右等价，非正误之别）
#   彚/彙→汇  祐→佑  畬→畲  沬→沫  已/巳→己  它/他→它
VARIANTS = {"彚": "汇", "彙": "汇", "祐": "佑", "畬": "畲",
            "沬": "沫", "巳": "己", "已": "己", "他": "它"}


def norm(s: str) -> str:
    s = convert(s, "zh-cn")            # 繁 → 简
    s = re.sub(r"^[^：:]*[：:]", "", s)  # 去爻位/卦名前缀
    for a, b in VARIANTS.items():
        s = s.replace(a, b)
    return "".join(CJK.findall(s))       # 只留汉字


def parse_insert_rows(sql: str, table: str):
    m = re.search(r"INSERT INTO\s+`?" + table + r"`?\s*\(([^)]*)\)\s*VALUES\s*(.*?);\s*\n",
                  sql, re.S)
    if not m:
        return None, []
    cols = [c.strip().strip("`") for c in m.group(1).split(",")]
    body = m.group(2)
    rows, fields, buf = [], [], ""
    i, n, in_str = 0, len(body), False
    while i < n:
        ch = body[i]
        if in_str:
            if ch == "'":
                if i + 1 < n and body[i + 1] == "'":
                    buf += "''"; i += 2; continue
                in_str = False; buf += ch
            else:
                buf += ch
        elif ch == "'":
            in_str = True; buf += ch
        elif ch == "(":
            fields = []; buf = ""
        elif ch == ")":
            fields.append(buf.strip()); rows.append(list(fields)); fields = []; buf = ""
        elif ch == ",":
            fields.append(buf.strip()); buf = ""
        else:
            buf += ch
        i += 1
    out = []
    for r in rows:
        vals = [(f[1:-1].replace("''", "'") if f.startswith("'") and f.endswith("'") else f)
                for f in r]
        out.append(vals)
    return cols, [r for r in out if len(r) == len(cols)]


def ctext_guaci(hex_item):
    """从 ctext content_zh 抽取卦辞：跳过仅卦名/符名行、彖象传、以及爻辞行。"""
    for line in hex_item["content_zh"]:
        s = line.replace(" ", "")
        if "傳" in s or "彖" in s or "象" in s:
            continue
        if re.match(r"^[䷀-䷿]?[\u4e00-\u9fff]{1,2}[：:]\s*$", s):  # 仅「卦名：」
            continue
        if re.match(r"^(初|上|用)?[九六][：，,、]", s):             # 爻辞行
            continue
        if len(CJK.findall(s)) >= 2:
            return line
    return ""


def strip_name(gd: str, name: str, ndb: str) -> str:
    ns = norm(name)
    if ns and gd.startswith(ns) and not ndb.startswith(ns):
        return gd[len(ns):]
    return gd


def main() -> int:
    sql = open(SQL, encoding="utf-8").read()
    bad = 0

    wuying = sorted(json.load(open(WUYING, encoding="utf-8"))["hexagrams"], key=lambda h: h["number"])
    ct = sorted(json.load(open(CTEXT, encoding="utf-8"))["hexagrams"], key=lambda h: h["metadata"]["number"])
    boll = sorted(json.load(open(BOLL, encoding="utf-8"))["hexagrams"], key=lambda h: h["number"])
    H = {h["number"]: h for h in boll}

    # ctext 爻辞（按卦聚合）
    ct_yao = {}
    for h in ct:
        num = h["metadata"]["number"]
        ys = [l for l in h["content_zh"]
              if re.match(r"^(初|上|用)?[九六][：，,、]", l.replace(" ", ""))
              and "象" not in l and "傳" not in l]
        ct_yao[num] = [norm(y) for y in ys]

    # ---------- 384 爻辞 vs 武英殿本 ／ ctext ／ bollwarm（任一相符即通过）----------
    cols, rows = parse_insert_rows(sql, "yijing_line_dictum")
    idx = {c: i for i, c in enumerate(cols)}
    print(f"== yijing_line_dictum: {len(rows)} 行")
    byhex = {}
    for r in rows:
        m = re.match(r"LineDictum_(\d+)_(\d+)", r[idx["name"]])
        if m:
            byhex.setdefault(int(m.group(1)), {})[int(m.group(2))] = r[idx["dictum_text"]]
    for h in wuying:
        num = h["number"]
        dbl = byhex.get(num, {})
        wys = [norm(x) for x in h["lines"]]
        cts = ct_yao.get(num, [])
        blys = [norm(y["text"]) for y in H[num].get("yaoci", [])]
        for li in range(1, 7):
            nd = norm(dbl.get(li, ""))
            cands = {wys[li - 1]}
            if len(cts) >= li: cands.add(cts[li - 1])
            if len(blys) >= li: cands.add(blys[li - 1])
            if nd not in cands:
                bad += 1
                print(f"   [异] 卦{num} 爻{li}  DB={nd}\n        武英={wys[li-1]}\n        ctext={cts[li-1] if len(cts)>=li else '-'}\n        boll={blys[li-1] if len(blys)>=li else '-'}")

    # ---------- 64 卦辞 vs ctext ／ bollwarm（任一相符即通过）----------
    cols2, rows2 = parse_insert_rows(sql, "yijing_hexagram_dictum")
    idx2 = {c: i for i, c in enumerate(cols2)}
    print(f"== yijing_hexagram_dictum: {len(rows2)} 行")
    for r in rows2:
        num = int(re.match(r"Dictum_Hex_(\d+)", r[idx2["name"]]).group(1))
        ndb = norm(r[idx2["dictum_text"]])
        g_ct = strip_name(norm(ctext_guaci(ct[num - 1])), ct[num - 1]["title_zh"], ndb)
        g_bo = strip_name(norm(H[num]["guaci"]), H[num]["name"], ndb)
        if ndb not in (g_ct, g_bo):
            bad += 1
            print(f"   [异] 卦{num} 辞  DB={ndb}\n        ctext={g_ct}\n        boll ={g_bo}")

    print("RESULT:", "ALL PASS" if bad == 0 else f"{bad} DEVIATION(S)")
    return 0 if bad == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
