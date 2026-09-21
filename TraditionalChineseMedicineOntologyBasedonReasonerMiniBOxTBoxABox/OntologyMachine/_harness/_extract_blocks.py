#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从套件日志中按「测试显示名」抽取该用例的完整输出块。日志为 GBK。"""
import sys, re

LOG = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\OntologyMachine\_harness\_suite_fz2.log"

def load(path):
    for enc in ("gbk", "utf-8", "gb18030"):
        try:
            with open(path, "r", encoding=enc, errors="strict") as f:
                return f.read()
        except Exception:
            continue
    with open(path, "r", encoding="gbk", errors="replace") as f:
        return f.read()

def main():
    keys = sys.argv[1:]
    text = load(LOG)
    lines = text.splitlines()
    # 找到每个 [通过]/[失败] 行，向前回溯到 "===== xxx =====" 块首
    for k in keys:
        print("=" * 90)
        print("### KEY:", k)
        for i, ln in enumerate(lines):
            if k in ln and ("[失败]" in ln or "[通过]" in ln):
                # 回溯块首
                start = i
                for j in range(i, max(0, i - 60), -1):
                    if lines[j].startswith("====="):
                        start = j
                        break
                for m in range(start, min(len(lines), i + 1)):
                    print(lines[m])
                print("-" * 60)

if __name__ == "__main__":
    main()
