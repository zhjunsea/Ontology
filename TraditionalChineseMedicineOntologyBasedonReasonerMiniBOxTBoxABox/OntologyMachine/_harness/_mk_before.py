# -*- coding: utf-8 -*-
"""构建「改动前」本体快照目录，用于归因验证。"""
import os, shutil, io, sys

ONT = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology'
TMP = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\OntologyMachine\_harness\_ont_before'

if os.path.exists(TMP):
    shutil.rmtree(TMP)
os.makedirs(TMP)

# 复制全部 .owl（含子目录）
n = 0
for dp, dn, fn in os.walk(ONT):
    rel = os.path.relpath(dp, ONT)
    dst = os.path.join(TMP, rel) if rel != '.' else TMP
    os.makedirs(dst, exist_ok=True)
    for f in fn:
        if f.endswith('.owl'):
            shutil.copy2(os.path.join(dp, f), os.path.join(dst, f))
            n += 1

# 用备份覆盖三个被改文件
pairs = [('tcm-core.owl.bak-before-A8C6D8', 'tcm-core.owl'),
         ('tcm-zhengzhuang.owl.bak-before-whitelist', 'tcm-zhengzhuang.owl'),
         ('tcm-maixiang.owl.bak-before-whitelist', 'tcm-maixiang.owl')]
for bak, live in pairs:
    shutil.copy2(os.path.join(ONT, bak), os.path.join(TMP, live))

print(f'复制 {n} 个 .owl 到 {TMP}，并用备份覆盖 3 个文件')
