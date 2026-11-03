#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""第十一器：附录·一撮金爻诀（64 卦×3 爻＝192 首）↔ 交付物 双向核。
判据：
  1) Java 静态 `putYicuoJin(卦, 爻位, 等级, 爻题, 断辞)` 之 (卦, 爻位) 集合
     ≡ DB `yicuo_jin_yao` 表之 (hexagram_name, line_position) 集合；
  2) 同键之 (等级, 爻题, 断辞) 逐字相等；
  3) 计数 192（64×3）与代码/DB 实况相符。
"""
import io, re

JAVA = 'src/main/java/com/ocean/ontologyframework/plumblossom/PlumBlossomOntologyJobWorker.java'
SQL = 'ontology/database/data.sql'


def java_yicuo(path):
    src = io.open(path, encoding='utf-8').read()
    out = {}
    pat = re.compile(
        r'putYicuoJin\(\s*"((?:[^"\\]|\\.)*)"\s*,\s*(\d+)\s*,\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*\)\s*;'
    )
    for m in pat.finditer(src):
        hexa, pos, grade, yao, text = m.group(1), int(m.group(2)), m.group(3), m.group(4), m.group(5)
        clean = lambda s: s.replace('\\"', '"').replace('\\\\', '\\')
        out[(hexa, pos)] = (grade, yao, clean(text))
    return out


def split_sql_fields(s):
    """按顶层逗号切分一条 VALUES 记录，尊重单引号（'' 转义）。"""
    out, buf, i, n, ins = [], '', 0, len(s), False
    while i < n:
        ch = s[i]
        if ins:
            if ch == "'":
                if i + 1 < n and s[i + 1] == "'":
                    buf += "'"; i += 2; continue
                ins = False
            else:
                buf += ch
        elif ch == "'":
            ins = True
        elif ch == ',':
            out.append(buf.strip()); buf = ''
        else:
            buf += ch
        i += 1
    out.append(buf.strip())
    return out


def sql_yicuo(path):
    sql = io.open(path, encoding='utf-8').read().replace('\r', '')
    m = re.search(r"INSERT INTO yicuo_jin_yao[^;]*?VALUES\s*(.*?);\s*\n", sql, re.S)
    body = m.group(1)
    out = {}
    for line in body.split('\n'):
        line = line.strip()
        fm = re.match(r"^\((.*)\),?$", line)
        if not fm:
            continue
        fields = split_sql_fields(fm.group(1))
        # name, hexagram_name, hexagram_seq, line_position, yao_label, grade, grade_iri, dictum_text, label
        if len(fields) < 9:
            continue
        hexa = fields[1]
        pos = int(fields[3])
        yao, grade, text = fields[4], fields[5], fields[7].replace("''", "'")
        out[(hexa, pos)] = (grade, yao, text)
    return out


def main():
    j = java_yicuo(JAVA)
    d = sql_yicuo(SQL)
    print(f"[INFO] Java putYicuoJin 键数 = {len(j)}")
    print(f"[INFO] DB yicuo_jin_yao 键数 = {len(d)}")
    bad = 0
    for k in sorted(set(j) - set(d)):
        print(f"  !! 仅 Java：{k}"); bad += 1
    for k in sorted(set(d) - set(j)):
        print(f"  !! 仅 DB：{k}"); bad += 1
    for k in sorted(set(j) & set(d)):
        if j[k] != d[k]:
            bad += 1
            print(f"  !! 不符：{k}")
            print(f"       Java: {j[k]}")
            print(f"       DB  : {d[k]}")
    claim_ok = (len(j) == 192 and len(d) == 192)
    print(f"[INFO] 计数声称「192 首」核对：{'相符' if claim_ok else '不符'}")
    print("RESULT:", "ALL PASS（一撮金爻诀 Java≡DB，192 首逐字相等）" if bad == 0 and claim_ok else f"HAS DIFF（{bad}）")


if __name__ == '__main__':
    main()
