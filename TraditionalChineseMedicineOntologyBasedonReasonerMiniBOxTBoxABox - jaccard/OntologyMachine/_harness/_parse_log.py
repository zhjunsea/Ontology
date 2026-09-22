#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从 app_run_v3.log 抽取每个患者的：症状、八纲、六经、方证、方剂。"""
import re, sys, io

LOG = sys.argv[1] if len(sys.argv) > 1 else \
    r"D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/OntologyMachine/app_run_v3.log"

pat_sym = re.compile(r"症状映射\[第0轮\]: 已识别 \d+ 项.*?症状=\[(.*?)\] 脉象=\[(.*?)\] 舌象=\[(.*?)\]")
pat_bg  = re.compile(r"八纲来源: 患者推理=\[(.*?)\]")
pat_lj  = re.compile(r"六经来源: 患者推理=\[(.*?)\]")
pat_fz  = re.compile(r"方证完成: (\S+) 匹配数=(\d+)")
pat_dx  = re.compile(r"诊断解释: 六经：(\S+?)，方证：(\S+?)，推荐方剂：(\S+?)。")

blocks = []
cur = None
with io.open(LOG, encoding="utf-8", errors="replace") as f:
    for line in f:
        m = pat_sym.search(line)
        if m:
            if cur: blocks.append(cur)
            cur = {"sym": m.group(1), "pulse": m.group(2), "tongue": m.group(3),
                   "bg": None, "lj": None, "fz": None, "fzscore": None, "dx": None}
            continue
        if cur is None: continue
        m = pat_bg.search(line)
        if m and cur["bg"] is None: cur["bg"] = m.group(1)
        m = pat_lj.search(line)
        if m and cur["lj"] is None: cur["lj"] = m.group(1)
        m = pat_fz.search(line)
        if m: cur["fz"] = m.group(1); cur["fzscore"] = m.group(2)
        m = pat_dx.search(line)
        if m: cur["dx"] = (m.group(1), m.group(2), m.group(3))
if cur: blocks.append(cur)

def clean(s):
    return s.replace("_instance", "").replace("http://www.tcm-classics.org/jingfang#", "")

for i, b in enumerate(blocks):
    dx = b["dx"] or ("?", "?", "?")
    print(f"[{i:03d}] 症状={clean(b['sym'])} | 脉={clean(b['pulse'])} | 舌={clean(b['tongue'])}")
    print(f"      八纲={b['bg']}  六经={b['lj']}")
    print(f"      方证={b['fz']}  诊断={dx[0]}/{dx[1]}/{dx[2]}")
print(f"\n共 {len(blocks)} 个患者块", file=sys.stderr)
