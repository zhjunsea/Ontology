# -*- coding: utf-8 -*-
import re, os, glob, shutil

base = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox'
ont_root = os.path.join(base, 'ontology')
java_root = os.path.join(base, 'OntologyMachine', 'OntologyFramework', 'src')

# OWL 侧：#ID 形式的替换（方剂本体ID + 方证引用ID + 无冲突方证类名）
owl_map = {
    # --- 方剂本体 ID 修正（tcm-fangji-abox.owl）---
    'Gansuibanxiantang': 'Gansuibanxiatang',          # 甘遂半夏汤
    'Gegenjiabanxiantang': 'Gegenjiabanxiatang',      # 葛根加半夏汤
    'Xiaobanxiantang': 'Xiaobanxiatang',              # 小半夏汤
    'Guizhijiahoupoxingrentang': 'Guizhijiahoupoxingzitang',  # 桂枝加厚朴杏子汤
    'Jupizhuratang': 'Jupizhurutang',                 # 橘皮竹茹汤
    'Tongmaisijiazhudanzhitang': 'Tongmaisinijiazhudanzhitang',  # 通脉四逆加猪胆汁汤
    'Sinisansan': 'Sinisan',                          # 四逆散
    # --- 方证引用 ID 修正（fangzheng/*.owl 的 #you_chufang hasValue）---
    'Sinisang': 'Sinisan',                            # 四逆散（duli）
    'Houpouqiwutang': 'Houpoqiwutang',                # 厚朴七物汤（hebing）
    'Houpousanwutang': 'Houposanwutang',              # 厚朴三物汤（shaoyang）
    'Houpoushengjiangbanxiagancaorenshentang': 'Houposhengjiangbanxiagancaorenshentang',  # 厚朴生姜半夏甘草人参汤（shaoyang）
    # --- 方证类名修正（无冲突）---
    'Houpousanwutangzheng': 'Houposanwutangzheng',    # 厚朴三物汤证（shaoyang）
}
# 冲突项：Houpoushengjiangbanxiagancaorenshentangzheng -> Houposhengjiangbanxiagancaorenshentangzheng
# 目标已存在于 zabing.owl（重复方证），本次跳过类名替换，仅改其方剂引用。

java_map = {
    'Guizhijiahoupoxingrentangzheng': 'Guizhijiahoupoxingzitangzheng',
    'Guizhijiahoupoxingrentang': 'Guizhijiahoupoxingzitang',
    'Houpouqiwutangzheng': 'Houpoqiwutangzheng',
    'Houpouqiwutang': 'Houpoqiwutang',
    'Houpousanwutangzheng': 'Houposanwutangzheng',
    'Houpousanwutang': 'Houposanwutang',
    'Houpoushengjiangbanxiagancaorenshentangzheng': 'Houposhengjiangbanxiagancaorenshentangzheng',
    'Houpoushengjiangbanxiagancaorenshentang': 'Houposhengjiangbanxiagancaorenshentang',
    'Gansuibanxiantangzheng': 'Gansuibanxiatangzheng',
    'Gansuibanxiantang': 'Gansuibanxiatang',
    'Jupizhuratangzheng': 'Jupizhurutangzheng',
    'Jupizhuratang': 'Jupizhurutang',
}

owl_order = sorted(owl_map, key=len, reverse=True)
java_order = sorted(java_map, key=len, reverse=True)

bak_dir = os.path.join(base, 'OntologyMachine', '_fangji_pinyin_backup')
os.makedirs(bak_dir, exist_ok=True)

owl_files = [fp for fp in glob.glob(os.path.join(ont_root, '**', '*.owl'), recursive=True) if '_rename_backup' not in fp]
java_files = glob.glob(os.path.join(java_root, '**', '*.java'), recursive=True)

for fp in owl_files + java_files:
    shutil.copy2(fp, os.path.join(bak_dir, os.path.basename(fp)))
print(f'已备份 {len(owl_files)} owl + {len(java_files)} java 到 {bak_dir}')

def repl_owl(txt):
    for old in owl_order:
        new = owl_map[old]
        txt = re.sub(r'#' + re.escape(old) + r'(?=["<>])', '#' + new, txt)
    return txt

def repl_java(txt):
    for old in java_order:
        new = java_map[old]
        txt = re.sub(r'\b' + re.escape(old) + r'\b', new, txt)
    return txt

print('\n===== OWL 修改 =====')
for fp in owl_files:
    orig = open(fp, encoding='utf-8').read()
    new = repl_owl(orig)
    if new != orig:
        open(fp, 'w', encoding='utf-8', newline='').write(new)
        print('  ', os.path.relpath(fp, ont_root))

print('\n===== Java 修改 =====')
for fp in java_files:
    orig = open(fp, encoding='utf-8').read()
    new = repl_java(orig)
    if new != orig:
        open(fp, 'w', encoding='utf-8', newline='').write(new)
        print('  ', os.path.relpath(fp, java_root))
print('\n完成')
