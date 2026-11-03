#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""第十二器：附录文献「内置同源兜底」↔ DB `appendix_text` 双向核。
判据：
  A) Java 静态 `putAt(name, work, topic, text)`（渔樵对问 98＋宋史·邵雍传 10＝108）
     ↔ DB `appendix_text` 之 ATXT_YQ_* / ATXT_SY_*（108 行）：逐 name 之 (篇名, 段主题, 原文) 相等；
  B) Java 静态 `BA_GUA_JUE`（八宫×八卦＝64）
     ↔ DB `appendix_text` 之 ATXT_BGJ_*（8 行，八宫，各以「、」联八卦）：逐宫相等。
"""
import io, re

JAVA = 'src/main/java/com/ocean/ontologyframework/plumblossom/PlumBlossomOntologyJobWorker.java'
SQL = 'ontology/database/data.sql'


def split_fields(s):
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


def java_putat(path):
    src = io.open(path, encoding='utf-8').read()
    out = {}
    pat = re.compile(
        r'putAt\(\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*\)\s*;'
    )
    for m in pat.finditer(src):
        name, work, topic, text = m.groups()
        cl = lambda s: s.replace('\\"', '"').replace('\\\\', '\\')
        out[name] = (cl(work), cl(topic), cl(text))
    return out


def java_bgj(path):
    src = io.open(path, encoding='utf-8').read()
    out = {}
    for m in re.finditer(r'BA_GUA_JUE\.put\(\s*"([^"]+)"\s*,\s*new\s+String\[\]\s*\{([^}]*)\}\s*\)', src):
        gong = m.group(1)
        items = [x.strip().strip('"') for x in m.group(2).split(',') if x.strip()]
        out[gong] = items
    return out


def db_rows(path):
    sql = io.open(path, encoding='utf-8').read().replace('\r', '')
    m = re.search(r"INSERT INTO appendix_text[^;]*?VALUES\s*(.*?);\s*\n", sql, re.S)
    body = m.group(1)
    out = {}
    for line in body.split('\n'):
        line = line.strip()
        fm = re.match(r"^\((.*)\),?$", line)
        if not fm:
            continue
        f = split_fields(fm.group(1))
        # name, work_name, section_index, section_topic, text_body, source_ref, label
        if len(f) < 5:
            continue
        out[f[0]] = (f[1], f[3], f[4].replace("''", "'"))
    return out


def main():
    j = java_putat(JAVA)
    d = db_rows(SQL)
    bad = 0
    print(f"[INFO] Java putAt = {len(j)}；DB appendix_text = {len(d)}")

    # A) YQ + SY
    jA = {k: v for k, v in j.items()}
    dA = {k: v for k, v in d.items() if k.startswith('ATXT_YQ_') or k.startswith('ATXT_SY_')}
    for k in sorted(set(jA) - set(dA)):
        print(f"  !! 仅 Java 有：{k}"); bad += 1
    for k in sorted(set(dA) - set(jA)):
        print(f"  !! 仅 DB 有：{k}"); bad += 1
    for k in sorted(set(jA) & set(dA)):
        if jA[k] != dA[k]:
            bad += 1
            print(f"  !! A 不符：{k}\n       Java: {jA[k]}\n       DB  : {dA[k]}")

    # B) BGJ
    jb = java_bgj(JAVA)
    dbgj = {k: v for k, v in d.items() if k.startswith('ATXT_BGJ_')}
    for name, (work, topic, text) in sorted(dbgj.items()):
        gong = topic  # 如 乾宫
        jitems = jb.get(gong[:-1]) if gong.endswith('宫') else None
        if jitems is None:
            bad += 1; print(f"  !! B Java 无宫：{gong}（{name}）"); continue
        if '、'.join(jitems) != text:
            bad += 1
            print(f"  !! B 不符：{gong}\n       Java: {'、'.join(jitems)}\n       DB  : {text}")

    claim_ok = (len(jA) == 108 and len(dA) == 108 and len(jb) == 8 and len(dbgj) == 8)
    print(f"[INFO] 计数核对（108＋8）：{'相符' if claim_ok else '不符'}")
    print("RESULT:", "ALL PASS（附录文献 Java≡DB：渔樵/宋史 108＋八卦诀 8）" if bad == 0 and claim_ok else f"HAS DIFF（{bad}）")


if __name__ == '__main__':
    main()
