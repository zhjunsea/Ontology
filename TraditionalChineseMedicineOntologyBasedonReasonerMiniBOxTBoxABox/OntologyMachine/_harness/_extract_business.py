# -*- coding: utf-8 -*-
"""从 SKILL.md 提取「业务相关」铁律，生成独立 md。"""
import re

SRC = r"C:\Users\ocean\.workbuddy\skills\tcm-ontology-build-verify\SKILL.md"
DST = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\业务铁律.md"

src = open(SRC, encoding="utf-8").read()

pat = re.compile(r'^## 铁律 (\d+)：', re.M)
ms = list(pat.finditer(src))
mile = src.find('\n## 里程碑')
if mile < 0:
    mile = len(src)

sections = {}
for i, m in enumerate(ms):
    n = int(m.group(1))
    start = m.start()
    end = ms[i + 1].start() if i + 1 < len(ms) else mile
    sections[n] = src[start:end].rstrip()

# 业务相关（剔除编译/构建/调试/测试运行/性能/工具链/工程约束类）
business = [15, 16, 18, 19, 21, 22, 23, 24, 25, 26, 27, 28, 30, 31, 32,
            35, 36, 37, 39, 41, 43, 46, 48, 49, 52, 53, 55, 56, 58, 60]

# 用户明确下达的硬约束（★）
star = {43, 46, 49, 52, 53, 60}

out = []
out.append("# 经方本体工程 · 业务铁律（30 条）\n")
out.append("> **来源**：`~/.workbuddy/skills/tcm-ontology-build-verify/SKILL.md`（铁律唯一权威源）")
out.append("> **筛选口径**：仅保留与 **中医本体建模 / 辨证逻辑 / 医理依据 / 诊断结论正确性** 相关的条目；")
out.append("> 剔除编译、构建、调试、测试运行、性能调优、工具链、工程约束等技术类条目。")
out.append("> **★** 标记为用户明确下达的硬约束。")
out.append("> 生成日期：2026-09-20\n")
out.append("## 目录\n")
for n in business:
    title = sections[n].split('\n', 1)[0].replace('## ', '')
    mark = "★ " if n in star else ""
    out.append(f"- {mark}{title}")
out.append("\n---\n")
for n in business:
    out.append(sections[n])
    out.append("\n---\n")

open(DST, "w", encoding="utf-8").write("\n".join(out))
print("OK ->", DST)
print("条数 =", len(business))
