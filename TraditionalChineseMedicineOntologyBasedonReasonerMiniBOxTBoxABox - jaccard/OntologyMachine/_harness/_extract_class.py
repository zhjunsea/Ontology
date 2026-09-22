#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从 OWL 文件中提取指定 class 的完整块（深度感知），用于对比改动前后。"""
import re
import sys


def extract_blocks(text, name):
    """返回所有 rdf:about="#name" 的顶层块。"""
    out = []
    for m in re.finditer(r'<owl:Class\s+rdf:about="#' + re.escape(name) + r'"', text):
        start = text.rfind('<owl:Class', 0, m.start() + 1)
        if start < 0:
            start = m.start()
        # 深度扫描到匹配的 </owl:Class>
        i = start
        depth = 0
        while i < len(text):
            if text.startswith('<owl:Class', i):
                depth += 1
                i += len('<owl:Class')
            elif text.startswith('</owl:Class>', i):
                depth -= 1
                i += len('</owl:Class>')
                if depth == 0:
                    out.append(text[start:i])
                    break
            else:
                i += 1
    return out


def main():
    path, name = sys.argv[1], sys.argv[2]
    with open(path, encoding='utf-8') as f:
        text = f.read()
    blocks = extract_blocks(text, name)
    print(f"### {path} :: {name}  ({len(blocks)} 个块)")
    for b in blocks:
        print(b)
        print('-' * 70)


if __name__ == '__main__':
    main()
