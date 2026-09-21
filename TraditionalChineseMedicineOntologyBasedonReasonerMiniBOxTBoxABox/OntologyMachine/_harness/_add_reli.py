#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
把「热利」正式建为个体：
  1) TBox  tcm-zhengzhuang.owl      → 追加类块 #Reli（⊑ Zhengzhuang，⊑ Xiali 投影边）
  2) ABox  tcm-zhengzhuang-abox.owl → 追加个体 #Reli_instance

医理依据（铁律 53）：
  《伤寒论》371 条「热利下重者，白头翁汤主之。」
  《伤寒论》373 条「下利欲饮水者，以有热故也，白头翁汤主之。」
  → 热利即湿热下注大肠之下利，主症下利（脓血）、里急后重、口渴欲饮水，属厥阴热利。
  → 归入下利族 Xiali（铁律 31「症状层级=投影」：只追加一条 细粒度 ⊑ 粗粒度 边）。

追加式写入（铁律 31 第 4 点）：不改任何原类块，在 </rdf:RDF> 前注入一个块。
回滚：删掉注入块，或还原 .bak-before-Reli-20260921。
"""
import io
import os
import sys

ONT = r"D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology"

TBOX = os.path.join(ONT, "tcm-zhengzhuang.owl")
ABOX = os.path.join(ONT, "tcm-zhengzhuang-abox.owl")

TBOX_BLOCK = """    <!-- 热利 ⊑ 下利（投影边，铁律 31）：371 条「热利下重者，白头翁汤主之」、373 条「下利欲饮水者，以有热故也，白头翁汤主之」。热利即湿热下注大肠之下利，属下利族。 -->
    <owl:Class rdf:about="#Reli">
        <rdfs:subClassOf rdf:resource="#Zhengzhuang"/>
        <rdfs:label xml:lang="zh">热利</rdfs:label>
        <rdfs:subClassOf rdf:resource="#Xiali"/>
        <rdfs:comment xml:lang="zh">《伤寒论》371条「热利下重者，白头翁汤主之」、373条「下利欲饮水者，以有热故也，白头翁汤主之」。湿热下注大肠，主症下利（脓血）、里急后重、口渴欲饮水，属厥阴热利。归下利族（Xiali）。</rdfs:comment>
    </owl:Class>

"""

ABOX_BLOCK = """    <owl:NamedIndividual rdf:about="#Reli_instance"><rdf:type rdf:resource="#Reli"/><rdfs:label xml:lang="zh">热利</rdfs:label></owl:NamedIndividual>

"""


def read(path):
    with io.open(path, "r", encoding="utf-8", newline="") as f:
        return f.read()


def write(path, text):
    with io.open(path, "w", encoding="utf-8", newline="") as f:
        f.write(text)


def inject(path, block, marker):
    text = read(path)
    if marker in text:
        print("SKIP  %s 已含 %s" % (os.path.basename(path), marker))
        return False
    if text.count("</rdf:RDF>") != 1:
        print("FAIL  %s 的 </rdf:RDF> 不唯一" % os.path.basename(path))
        sys.exit(1)
    crlf = "\r\n" if "\r\n" in text else "\n"
    blk = block.replace("\n", crlf)
    # 在 </rdf:RDF> 前插入
    idx = text.rindex("</rdf:RDF>")
    new = text[:idx] + blk + text[idx:]
    write(path, new)
    print("OK    %s 注入完成（换行=%s）" % (os.path.basename(path), repr(crlf)))
    return True


def main():
    inject(TBOX, TBOX_BLOCK, 'rdf:about="#Reli"')
    inject(ABOX, ABOX_BLOCK, 'rdf:about="#Reli_instance"')

    # 校验
    t = read(TBOX)
    a = read(ABOX)
    print()
    print("校验 TBox  : #Reli 类块 =", t.count('rdf:about="#Reli"'))
    print("校验 TBox  : 热利 label =", t.count(">热利</rdfs:label>"))
    print("校验 ABox  : #Reli_instance =", a.count('rdf:about="#Reli_instance"'))
    print("校验 ABox  : 热利 label =", a.count(">热利</rdfs:label>"))
    print("校验 ABox  : NamedIndividual 总数 =", a.count("<owl:NamedIndividual"))


if __name__ == "__main__":
    main()
