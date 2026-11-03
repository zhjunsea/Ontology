#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
梅花易数「全部中文字面量」逐字核验 · 二段（最长公共子串版，第 34 轮）。

对每个字面量取其 CJK 串 nc，求 nc 与底本 CJK 串的**最长公共子串** L。
  - L ≥ 8 且覆盖 nc 主体   → 「原文＋自撰包装」（引文已合底本，包装自撰，正常）
  - L < 8                  → 「纯自撰」（提示/日志/栏名/存目注，正常）
可疑者 = L 介于 2..(len(nc)-1) 且占比高，或**含文言句式却 L 很小**之类——
本器只列「最长命中 ≤ len(nc)-1 且 len(nc)≥10」者，供人工瞄一眼。

用法：python _gen/verify_all_literals2.py
"""
import os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "src/main/java/com/ocean/ontologyframework/plumblossom/PlumBlossomOntologyJobWorker.java")
EPUB = os.path.join(ROOT, "design/_epub_dump.txt")
CJK = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]")
MINLEN = 8


def cjk_only(s):
    return "".join(CJK.findall(s))


def extract_literals(java):
    out, i, n, line = [], 0, len(java), 1
    while i < n:
        ch = java[i]
        if ch == "\n":
            line += 1; i += 1; continue
        if ch == "/" and i + 1 < n and java[i + 1] == "/":
            j = java.find("\n", i); i = n if j < 0 else j; continue
        if ch == "/" and i + 1 < n and java[i + 1] == "*":
            j = java.find("*/", i); line += java[i:j].count("\n") if j > 0 else 0
            i = n if j < 0 else j + 2; continue
        if ch == '"':
            j, buf = i + 1, ""
            while j < n:
                c = java[j]
                if c == "\\":
                    buf += java[j:j + 2]; j += 2; continue
                if c == '"' or c == "\n":
                    break
                buf += c; j += 1
            out.append((line, buf)); i = j + 1; continue
        i += 1
    return out


def longest_common(nc, hay):
    """返回 nc 中在 hay 里出现的最长子串（长度, 文本）。"""
    best, bt = 0, ""
    L = len(nc)
    for a in range(L):
        if L - a <= best:
            break
        # 贪心扩展
        b = a + best + 1
        while b <= L and nc[a:b] in hay:
            best = b - a; bt = nc[a:b]; b += 1
    return best, bt


def main():
    java = open(JAVA, encoding="utf-8").read()
    hay = cjk_only(open(EPUB, encoding="utf-8").read())
    checked, seen = 0, set()
    rows = []
    for ln, lit in extract_literals(java):
        s = lit.replace('\\"', '"').replace("\\n", "\n")
        nc = cjk_only(s)
        if len(nc) < MINLEN or nc in seen:
            continue
        seen.add(nc); checked += 1
        if nc in hay:
            continue
        L, bt = longest_common(nc, hay)
        rows.append((L / len(nc), L, len(nc), ln, s, bt))
    rows.sort()
    print(f"[INFO] 去重字面量 {checked}；cjk 未全合 {len(rows)}")
    print("[INFO] 按「最长命中占比」升序（低者最可疑为原文讹误/自撰）：")
    for ratio, L, tot, ln, s, bt in rows:
        print(f"   r={ratio:.2f} L={L}/{tot} L{ln}: {s[:70]!r}  ‖match‖={bt[:40]!r}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
