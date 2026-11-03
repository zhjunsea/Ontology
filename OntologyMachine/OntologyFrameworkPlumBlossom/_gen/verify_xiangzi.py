#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""第十器：相字表（卷之四·字画指迷／卷之五·拆字杂编）↔ 交付物 双向核。
判据：
  1) Java 静态 `putXz(cat, key, text)` 之 (category, rule_key) 集合
     ≡ DB `xiangzi_rule` 表之 (category, rule_key) 集合（逐键相等）；
  2) 同键之 rule_text 逐字相等（去引号归一）；
  3) 报告所陈「57 类 263 条」之计数与代码/DB 实况相符。
"""
import io, re
from collections import OrderedDict

JAVA = 'src/main/java/com/ocean/ontologyframework/plumblossom/PlumBlossomOntologyJobWorker.java'
SQL = 'ontology/database/data.sql'


def java_putxz(path):
    src = io.open(path, encoding='utf-8').read()
    out = OrderedDict()
    # 单行 putXz("a", "b", "c");  —— 三参皆字面量
    pat = re.compile(r'putXz\(\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*\)\s*;')
    for m in pat.finditer(src):
        cat, key, text = m.group(1), m.group(2), m.group(3)
        text = text.replace('\\"', '"').replace('\\\\', '\\')
        out[(cat, key)] = text
    return out


def sql_rows(path):
    sql = io.open(path, encoding='utf-8').read()
    m = re.search(r"INSERT INTO `xiangzi_rule`[^;]*?VALUES\s*(.*?);\s*\n", sql, re.S)
    body = m.group(1)
    rows = []
    for line in body.split('\n'):
        line = line.strip()
        fm = re.match(r"^\((.*)\),?$", line)
        if not fm:
            continue
        fields = re.findall(r"'((?:[^']|'')*)'", fm.group(1))
        if len(fields) >= 4:
            name, cat, key, text = fields[0], fields[1], fields[2], fields[3]
            rows.append((name, cat, key, text.replace("''", "'")))
    return rows


def main():
    jmap = java_putxz(JAVA)
    rows = sql_rows(SQL)

    jkeys = set(jmap.keys())
    dkeys = set((c, k) for _, c, k, _ in rows)

    print(f"[INFO] Java putXz 键数 = {len(jmap)}")
    print(f"[INFO] DB xiangzi_rule 行数 = {len(rows)}  唯一键数 = {len(dkeys)}")
    jcats = sorted({c for c, _ in jkeys})
    dcats = sorted({c for c, _ in dkeys})
    print(f"[INFO] Java 类别数 = {len(jcats)}；DB 类别数 = {len(dcats)}")

    bad = 0
    only_java = jkeys - dkeys
    only_db = dkeys - jkeys
    for c, k in sorted(only_java):
        print(f"  !! 仅 Java 有：({c}, {k})")
        bad += 1
    for c, k in sorted(only_db):
        print(f"  !! 仅 DB 有：({c}, {k})")
        bad += 1
    dmap = {(c, k): t for _, c, k, t in rows}
    for c, k in sorted(jkeys & dkeys):
        if jmap[(c, k)] != dmap[(c, k)]:
            bad += 1
            print(f"  !! 文本不符：({c}, {k})")
            print(f"       Java: {jmap[(c,k)][:80]}")
            print(f"       DB  : {dmap[(c,k)][:80]}")

    claim_ok = (len(jcats) == 57 and len(jmap) == 263 and len(rows) == 263)
    print(f"[INFO] 计数声称「57 类 263 条」核对：{'相符' if claim_ok else '不符'}")
    print("RESULT:", "ALL PASS（相字表 Java≡DB，逐键逐字相等）" if bad == 0 and claim_ok else f"HAS DIFF（{bad}）")


if __name__ == '__main__':
    main()
