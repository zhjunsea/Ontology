#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
梅花易数「全部中文字面量」逐字核验（第 34 轮·新增）。

前（三十轮）仅核 `private static final String` 常量与 `「…」` 引文；
本轮**扩大至 Java 文件内全部中文字符串字面量**（含 `putXz(...)` 等
方法调用中的内联照录文字），与底本 `_epub_dump.txt` 作子串比对。

三级判定（由宽到严）：
  1) cjk  —— 仅取 CJK 汉字（校字不校标点）：命中即「文字无缺」；
  2) strict —— 去脚注/校勘括号/空白/引号（保留点号）：命中即「引录体例亦合」。
  MINLEN 字以下不参与。

输出：
  - strict 不合、cjk 合  → 「标点/体例差异」（C 类，可留可归底本）
  - strict 不合、cjk 不合 → 「疑讹/自撰」（须人工定性：原文漏校 or 自撰）

用法：python _gen/verify_all_literals.py [--strict]
退出码：恒 0（本器为**定性器**，不阻断）。
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "src/main/java/com/ocean/ontologyframework/plumblossom/PlumBlossomOntologyJobWorker.java")
EPUB = os.path.join(ROOT, "design/_epub_dump.txt")
SNAP = os.path.join(ROOT, "_gen/external_sources")   # 别本快照（图象补录之出处）

MINLEN = 8          # 参与判定的最短 CJK 字数
CJK = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]")


def norm_strict(s: str) -> str:
    s = re.sub(r"\[\d+\]", "", s)
    s = s.replace("\u3000", "").replace(" ", "")
    s = re.sub(r"[\r\n\t]", "", s)
    s = re.sub(r"[〔〕]", "", s)
    s = re.sub(r"[“”「」]", "", s)
    return s


def cjk_only(s: str) -> str:
    return "".join(CJK.findall(s))


def extract_literals(java: str):
    out = []
    i, n = 0, len(java)
    line = 1
    while i < n:
        ch = java[i]
        if ch == "\n":
            line += 1; i += 1; continue
        if ch == "/" and i + 1 < n and java[i + 1] == "/":
            j = java.find("\n", i); i = n if j < 0 else j; continue
        if ch == "/" and i + 1 < n and java[i + 1] == "*":
            j = java.find("*/", i)
            line += java[i:j].count("\n") if j > 0 else 0
            i = n if j < 0 else j + 2; continue
        if ch == '"':
            j = i + 1; buf = ""
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


def main() -> int:
    java = open(JAVA, encoding="utf-8").read()
    epub_raw = open(EPUB, encoding="utf-8").read()
    epub_strict = norm_strict(epub_raw)
    epub_cjk = cjk_only(epub_raw)

    # 别本快照（图象补录之出处）：命中之字面量属「据外源补录·有本」，非自撰。
    import glob as _glob
    bu_raw = "".join(open(p, encoding="utf-8").read()
                     for p in sorted(_glob.glob(os.path.join(SNAP, "*.txt"))))
    bu_cjk = cjk_only(bu_raw)

    checked = 0
    seen = set()
    punct_only, cjk_miss, bu_src = [], [], []
    for ln, lit in extract_literals(java):
        s = lit.replace('\\"', '"').replace("\\n", "\n")
        ns = norm_strict(s)
        nc = cjk_only(s)
        if len(nc) < MINLEN:
            continue
        checked += 1
        if nc in seen:
            continue
        seen.add(nc)
        if ns in epub_strict:
            continue
        if nc in epub_cjk:
            punct_only.append((ln, s))
        elif nc in bu_cjk:
            bu_src.append((ln, s))
        else:
            cjk_miss.append((ln, s))

    print(f"[INFO] 参与核验（CJK≥{MINLEN}）字面量 {checked} 个，去重 {len(seen)}")
    print(f"[INFO] ①体例差异（strict 不合 / cjk 合）{len(punct_only)} 个")
    for ln, s in punct_only:
        print(f"   P L{ln}: {s[:90]!r}")
    print(f"[INFO] ③别本补录（据 _gen/external_sources 外源，有本非自撰）{len(bu_src)} 个")
    for ln, s in bu_src:
        print(f"   S L{ln}: {s[:90]!r}")
    print(f"[INFO] ②疑讹/自撰（strict 不合 / cjk 亦不合且非别本补录）{len(cjk_miss)} 个")
    for ln, s in cjk_miss:
        print(f"   X L{ln}: {s[:90]!r}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
