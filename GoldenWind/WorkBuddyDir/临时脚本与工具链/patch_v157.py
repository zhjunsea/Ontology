# -*- coding: utf-8 -*-
"""v15.7：术语统一 —— 本体中「配件」与「附件」指同一事物，统一为「附件」。

范围（用户确认：本体 + 配套文档 + BPMN）：
  1) ontology/TowerMidSection.owl
  2) design/塔架中段设计本体.md
  3) design/塔架中段本体-Openllet推理适配规范.md
  4) ontology/bpmn/塔架中段定制化设计流程.bpmn
  5) ontology/bpmn/_build_ui.py   （index.html 由其重新生成）

不改：design/塔架中段设计流程.md、design/流程访谈.txt（流程原文）、
      design/ 下的历史审视/审核/核查报告（历史留档）。
"""
import io
import os
import sys

ROOT = r"D:\work\Ontology\GoldenWind"

owl_p = os.path.join(ROOT, "ontology", "TowerMidSection.owl")
doc_p = os.path.join(ROOT, "design", "塔架中段设计本体.md")
spec_p = os.path.join(ROOT, "design", "塔架中段本体-Openllet推理适配规范.md")
bpmn_p = os.path.join(ROOT, "ontology", "bpmn", "塔架中段定制化设计流程.bpmn")
build_p = os.path.join(ROOT, "ontology", "bpmn", "_build_ui.py")

TOTAL = 0


def load(p):
    with io.open(p, "r", encoding="utf-8") as f:
        return f.read()


def save(p, s):
    with io.open(p, "w", encoding="utf-8", newline="") as f:
        f.write(s)


def rep(s, old, new, tag):
    """唯一性校验替换：命中必须恰好 1 次。"""
    n = s.count(old)
    if n != 1:
        print("[FAIL] %s : 命中 %d 次（应为 1）" % (tag, n))
        sys.exit(1)
    print("[OK] %s" % tag)
    return s.replace(old, new)


def rep_all(s, old, new, tag, expect=None):
    """全局替换：报告命中次数。"""
    n = s.count(old)
    if expect is not None and n != expect:
        print("[FAIL] %s : 命中 %d 次（应为 %d）" % (tag, n, expect))
        sys.exit(1)
    if n == 0:
        print("[FAIL] %s : 命中 0 次" % tag)
        sys.exit(1)
    print("[OK] %s : 替换 %d 处" % (tag, n))
    return s.replace(old, new)


# ============================================================
# 1) 本体
# ============================================================
s = load(owl_p)

# 1-1 术语统一（全局）
s = rep_all(s, "配件", "附件", "1-1 本体 配件→附件", expect=19)
s = rep_all(s, "Fitting", "Accessory", "1-2 本体 Fitting→Accessory", expect=4)
s = rep_all(s, "fitting", "accessory", "1-3 本体 fitting→accessory", expect=2)

# 1-4 版本号
s = rep(s, "<owl:versionInfo>v15.6</owl:versionInfo>",
        "<owl:versionInfo>v15.7</owl:versionInfo>", "1-4 版本号 v15.6→v15.7")

# 1-5 头部注释追加 v15.7 变更条目
s = rep(
    s,
    "**I** :支撑宽度 改名 :宽度，并集域扩至 :电缆托架。</rdfs:comment>",
    "**I** :支撑宽度 改名 :宽度，并集域扩至 :电缆托架。"
    "v15.7 术语统一：本体中「配件」与「附件」指同一事物，统一为「附件」——"
    "类 :配件类型 → :附件类型（AccessoryType）、对象属性 :选用配件类型 → :选用附件类型（selectsAccessoryType）、"
    ":配件类型适用于 → :附件类型适用于（accessoryTypeUsedByConnectionType），相关注释与历史条目中的旧名一并改写；"
    "配套文档（塔架中段设计本体.md、Openllet 推理适配规范）与 BPMN/UI 同步。"
    "（注：design/塔架中段设计流程.md 步骤 8.1 标题仍沿用流程原文「替换配件类型」，未改。）</rdfs:comment>",
    "1-5 头部注释追加 v15.7")

save(owl_p, s)

# ============================================================
# 2) 设计本体配套文档
# ============================================================
s = load(doc_p)
n = s.count("配件")
s = rep_all(s, "配件", "附件", "2-1 设计本体.md 配件→附件", expect=n)

# 追加 v15.7 变更记录：插到文档末尾「变更记录」区（v15.6 验证结果行之后）
anchor = ("*验证结果：958 三元组、28 具名类、57 对象属性、53 数据属性、20 Restriction、"
          "21 inverseOf、6 hasKey、37 FunctionalProperty、7 AllDisjointClasses、2 个体；"
          "悬空引用 0、Restriction invalid = 0、不相交组内无父子同组。*")
s = rep(s, anchor, anchor + "\n\nPLACEHOLDER_V157", "2-2a 定位 v15.6 验证结果行")
v157 = ("\n\n*v15.7 主要变更（术语统一：「配件」→「附件」）：*\n"
        "*① 本体中「配件」与「附件」指同一事物，统一为「附件」："
        "类 `:配件类型` → `:附件类型`（AccessoryType）；"
        "对象属性 `:选用配件类型` → `:选用附件类型`（selectsAccessoryType）、"
        "`:配件类型适用于` → `:附件类型适用于`（accessoryTypeUsedByConnectionType）；"
        "全文注释与历史条目中的旧名一并改写；*\n"
        "*② 实例化示例中个体 `:配件_焊接式` / `:配件_粘贴式` → `:附件类型_焊接式` / `:附件类型_粘贴式`；*\n"
        "*③ BPMN 与 UI（步骤 8「配件类型替换」→「附件类型替换」）同步；"
        "`design/塔架中段设计流程.md` 步骤 8.1 标题仍沿用流程原文，未改。*\n"
        "*验证结果：958 三元组、28 具名类、57 对象属性、53 数据属性、20 Restriction、"
        "21 inverseOf、6 hasKey、37 FunctionalProperty、7 AllDisjointClasses、2 个体；"
        "悬空引用 0、Restriction invalid = 0、不相交组内无父子同组。*")
s = rep(s, "PLACEHOLDER_V157", v157, "2-2b 设计本体.md 追加 v15.7 变更记录")

# 个体改名（在全局替换后，:配件_ 已变成 :附件_）
s = rep_all(s, ":附件_焊接式", ":附件类型_焊接式", "2-3 个体 :附件_焊接式→:附件类型_焊接式")
s = rep_all(s, ":附件_粘贴式", ":附件类型_粘贴式", "2-4 个体 :附件_粘贴式→:附件类型_粘贴式")

save(doc_p, s)

# ============================================================
# 3) Openllet 推理适配规范
# ============================================================
s = load(spec_p)
n = s.count("配件")
s = rep_all(s, "配件", "附件", "3-1 Openllet 规范 配件→附件", expect=n)
# 该规范中 :配件类型名称 已被 v15.6 删除，此处一并改写为 :附件类型名称（历史引用）
save(spec_p, s)

# ============================================================
# 4) BPMN
# ============================================================
s = load(bpmn_p)
n = s.count("配件")
s = rep_all(s, "配件", "附件", "4-1 BPMN 配件→附件", expect=n)
save(bpmn_p, s)

# ============================================================
# 5) _build_ui.py
# ============================================================
s = load(build_p)
n = s.count("配件")
s = rep_all(s, "配件", "附件", "5-1 _build_ui.py 配件→附件", expect=n)
save(build_p, s)

print("=== v15.7 术语统一完成 ===")
