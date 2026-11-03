#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
梅花易数「原文引录」逐字核验·DB 层（第 31 轮·新增）。

用途：把第 30 轮对 Java 常量的机器逐字核验，扩展到底本语料落盘层
      （ontology/database/data.sql 的三张「原文承载表」）：
        - yicuo_jin_yao  （附录·一撮金爻诀 192 首）
        - appendix_text  （附录·渔樵对问 98 + 宋史·邵雍传 10；+八卦诀 8 = 116）
        - xiangzi_rule   （卷四/五·相字 57 类 263 条）

规范化：去脚注标记 [n]、全角空格、空白/换行、校勘括号〔〕；
        引号归并（底本 “ ” ≡ 代码 「 」 皆去）。

判定（与 verify_verbatim.py 同口径）：
  - 每条文本字段须在底本中「成段命中」——即去空白后存在 ≥MINM 字之
    连续子串命中（MINM=4），否则计为可疑。
  - 自撰结构标注（栏名/存目注/部首列举/图象阙如注）计入白名单，非底本引文。

用法：python _gen/verify_db_corpus.py
退出码：0=无实质偏差；1=存在实质偏差。
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SQL = os.path.join(ROOT, "ontology/database/data.sql")
EPUB = os.path.join(ROOT, "design/_epub_dump.txt")

MINM = 4          # 「成段命中」所需最短连续匹配
MINLEN = 6        # 参与判定的最短字段长度

WHITE_KEYS = ("主事：", "笔式：", "存目", "图象阙如", "阙如", "见原书",
              "编目", "此处依", "待考")


def norm(s: str) -> str:
    s = s.replace("&quot;", '"')
    s = re.sub(r"\[\d+\]", "", s)
    s = s.replace("\u3000", "").replace(" ", "")
    s = re.sub(r"[\r\n]", "", s)
    s = re.sub(r"[〔〕]", "", s)
    s = re.sub(r"[“”「」]", "", s)
    return s


def max_consecutive(a: str, b: str, cap: int = 80) -> int:
    """a 与 b 的最长公共连续子串长度（朴素扫描，输入已去空白）。"""
    la = len(a)
    best = 0
    for i in range(la):
        j = best
        while i + j < la and a[i:i + j + 1] in b:
            j += 1
        if j > best:
            best = j
        if best >= cap:
            break
    return best


def parse_insert_rows(sql: str, table: str):
    """从多行 INSERT 中解析出该表的每一行元组（字符串字段，'' 转义）。"""
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
                    buf += "''"
                    i += 2
                    continue
                in_str = False
                buf += ch
            else:
                buf += ch
        elif ch == "'":
            in_str = True
            buf += ch
        elif ch == "(":
            fields = []
            buf = ""
        elif ch == ")":
            fields.append(buf.strip())
            rows.append(list(fields))   # 必须复制：行尾逗号会再 mutate fields
            fields = []
            buf = ""
        elif ch == ",":
            fields.append(buf.strip())
            buf = ""
        else:
            buf += ch
        i += 1
    # 拆字符串字段（含 '' 转义）
    out = []
    for r in rows:
        vals = []
        for f in r:
            f = f.strip()
            if f.startswith("'") and f.endswith("'"):
                f = f[1:-1].replace("''", "'")
            vals.append(f)
        out.append(vals)
    valid = [r for r in out if len(r) == len(cols)]
    return cols, valid


# 各表之「引文列」——须逐字合底本（label/key/topic 等为自撰辅助字段，不在核验列）
TARGETS = {
    "yicuo_jin_yao": ["dictum_text"],
    "appendix_text": ["text_body"],
    "xiangzi_rule": ["rule_text"],
}

# 已知「枚举分隔符归一」字段（底本以全角空格分列单字，本表以顿号归一；字集合底本，
# 非逐字引文）：照实记注，不整改，核验豁免。
ENUM_EXEMPT = {
    ("xiangzi_rule", "XiangZi_07_01"),   # 贵神：中、上、贝、日、月、大、人
    ("xiangzi_rule", "XiangZi_07_04"),   # 文星：二、乂、曰、子
}


def main() -> int:
    sql = open(SQL, encoding="utf-8").read()
    epub = norm(open(EPUB, encoding="utf-8").read())
    bad = 0
    for table, fields in TARGETS.items():
        cols, rows = parse_insert_rows(sql, table)
        if cols is None:
            print(f"[FAIL] 未找到表 {table} 的 INSERT")
            bad += 1
            continue
        idx = {c: i for i, c in enumerate(cols)}
        print(f"== {table}: {len(rows)} 行 (列：{','.join(cols)})")
        flagged = 0
        for r in rows:
            if (table, r[0]) in ENUM_EXEMPT:
                continue
            for f in fields:
                if f not in idx:
                    continue
                val = r[idx[f]]
                nv = norm(val)
                if len(nv) < MINLEN:
                    continue
                if any(k in val for k in WHITE_KEYS):
                    continue
                mc = max_consecutive(nv, epub)
                if mc < MINM:
                    flagged += 1
                    bad += 1
                    print(f"   [可疑] {r[0]}.{f}: {val[:50]!r} (maxMatch={mc})")
        print(f"   -> 可疑 {flagged} 条")
    print("RESULT:", "ALL PASS" if bad == 0 else f"{bad} SUSPECT(S)")
    return 0 if bad == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
