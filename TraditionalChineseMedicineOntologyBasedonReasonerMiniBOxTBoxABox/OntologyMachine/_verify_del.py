# -*- coding: utf-8 -*-
import re, os, glob
import xml.etree.ElementTree as ET

ont_root = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology'

print('===== 1. XML 语法校验 =====')
for fp in sorted(glob.glob(os.path.join(ont_root, 'fangzheng', '*.owl'))):
    try:
        ET.parse(fp)
        print(f'  OK   {os.path.basename(fp)}')
    except Exception as e:
        print(f'  FAIL {os.path.basename(fp)}: {e}')

print('\n===== 2. 方证类名唯一性 =====')
cls_map = {}
for fp in glob.glob(os.path.join(ont_root, 'fangzheng', '*.owl')):
    t = open(fp, encoding='utf-8').read()
    for m in re.finditer(r'<owl:Class rdf:about="#([^"]+)">', t):
        cls_map.setdefault(m.group(1), []).append(os.path.basename(fp))
dups = {k: v for k, v in cls_map.items() if len(v) > 1}
if dups:
    for k, v in sorted(dups.items()):
        print(f'  ⚠️ {k}: {v}')
else:
    print('  ✓ 无重复方证类名')

print('\n===== 3. 编码校验 =====')
for fp in sorted(glob.glob(os.path.join(ont_root, '**', '*.owl'), recursive=True)):
    if '_rename_backup' in fp:
        continue
    b = open(fp, 'rb').read()
    try:
        b.decode('utf-8')
    except Exception:
        print(f'  ⚠️ 非UTF-8: {os.path.relpath(fp, ont_root)}')

print('\n===== 4. 4个重复方证当前归属 =====')
for tg in ['Dahuanggansuitangzheng', 'Houpoqiwutangzheng', 'Yuebijiazhutangzheng',
           'Houposhengjiangbanxiagancaorenshentangzheng']:
    print(f'  {tg}: {cls_map.get(tg, "❌ 不存在")}')
