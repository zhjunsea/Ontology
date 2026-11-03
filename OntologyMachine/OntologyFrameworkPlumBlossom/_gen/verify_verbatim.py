#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
梅花易数「原文引录」逐字核验（第 30 轮·新增）。

用途：自动比对 PlumBlossomOntologyJobWorker.java 中所有「照录原文」常量与
     全部 `「…」` 引文，是否与底本 design/_epub_dump.txt 逐字相符。

规范化：去脚注标记 [n]、全角空格、空白/换行、校勘括号〔〕；
        引号归并（底本 “ ” ≡ 代码 「 」）。

判定：
  - `private static final String` 常量（照录原文者）须为底本子串（零缺失）。
  - 代码中每一条 `「…」` 引文须为底本子串（自撰标签/代码片段除外）。

用法：python _gen/verify_verbatim.py
退出码：0=全部通过；1=存在偏差。
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "src/main/java/com/ocean/ontologyframework/plumblossom/PlumBlossomOntologyJobWorker.java")
EPUB = os.path.join(ROOT, "design/_epub_dump.txt")

# 自撰提示/标签/代码片段（非底本原文，豁免）
EXEMPT_CONSTS = {"INTERPRET_SYSTEM_PROMPT", "INTERPRET_PLAIN_SYSTEM_PROMPT"}
# 代码自撰标签（非底本原文）：编码式、栏名、存目注等
EXEMPT_QUOTE_KEYS = ("邵康节", "占断", "五行生数", "附录", "动象", "体互", "某卦多", "此处依常理",
                     "上数", "某几卦", "互（上下）", "＋")


def norm(s: str) -> str:
    s = s.replace("&quot;", '"')
    s = re.sub(r"\[\d+\]", "", s)
    s = s.replace("\u3000", "").replace(" ", "")
    s = re.sub(r"[\r\n]", "", s)
    s = re.sub(r"[〔〕]", "", s)
    return s


def strip_comments(src: str) -> str:
    out, inblock = [], False
    for line in src.split("\n"):
        s = line
        if inblock:
            if "*/" in s:
                s = s.split("*/", 1)[1]
                inblock = False
            else:
                continue
        if "/*" in s and "*/" not in s:
            s = s.split("/*", 1)[0]
            inblock = True
        if "//" in s and '"' not in s.split("//", 1)[0]:
            s = s.split("//", 1)[0]
        out.append(s)
    return "\n".join(out)


def main() -> int:
    java = open(JAVA, encoding="utf-8").read()
    epub = norm(open(EPUB, encoding="utf-8").read())
    bad = 0

    # 1) 照录原文常量
    pat = re.compile(r"private\s+static\s+final\s+String\s+([A-Z0-9_]+)\s*=\s*(.*?);", re.S)
    for m in pat.finditer(java):
        name = m.group(1)
        parts = re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(2))
        val = "".join(parts).replace("\\n", "\n")
        if len(val) < 20 or name in EXEMPT_CONSTS:
            continue
        if norm(val) not in epub:
            bad += 1
            print(f"[FAIL] 常量 {name} 与底本不符")
        else:
            print(f"[ OK ] 常量 {name} ({len(val)}字)")

    # 2) 代码中「…」引文
    code = strip_comments(java)
    quotes = []
    for q in re.findall(r"「([^」]{2,60})」", code):
        if q not in quotes:
            quotes.append(q)
    real = [q for q in quotes
            if not re.search(r"[a-zA-Z]|\+|=|\(|\)|/|｜", q)
            and len(q) >= 4
            and not any(k in q for k in EXEMPT_QUOTE_KEYS)]
    for q in real:
        if norm(q) not in epub:
            bad += 1
            print(f"[FAIL] 引文「{q}」与底本不符")
    print(f"[INFO] 引文核验 {len(real)} 条")

    print("RESULT:", "ALL PASS" if bad == 0 else f"{bad} DEVIATION(S)")
    return 0 if bad == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
