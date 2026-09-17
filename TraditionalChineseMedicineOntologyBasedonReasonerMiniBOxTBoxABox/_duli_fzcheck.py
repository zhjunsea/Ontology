# -*- coding: utf-8 -*-
import re, io, os

BASE = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology"
FZ = os.path.join(BASE, 'fangzheng')
out = io.StringIO()

def load_instances(path):
    txt = open(path, encoding='utf-8').read()
    return set(re.findall(r'<owl:NamedIndividual rdf:about="#([^"]+)_instance">', txt))

inst = set()
for f in ['tcm-zhengzhuang-abox.owl', 'tcm-maixiang-abox.owl', 'tcm-shexiang-abox.owl']:
    inst |= load_instances(os.path.join(BASE, f))

files = {}
for f in os.listdir(FZ):
    if f.endswith('.owl'):
        files[f] = open(os.path.join(FZ, f), encoding='utf-8').read()

def block(cls):
    for f, txt in files.items():
        m = re.search(r'<owl:Class rdf:about="#' + re.escape(cls) + r'">', txt)
        if m:
            end = txt.find('</owl:Class>', m.start())
            return f, txt[m.start():end]
    return None, None

def constraints(cls):
    f, blk = block(cls)
    if blk is None: return None, [], [], []
    syms = re.findall(r'<owl:onProperty rdf:resource="#you_zhengzhuang"/>\s*<owl:someValuesFrom rdf:resource="#([^"]+)"/>', blk)
    mxs  = re.findall(r'<owl:onProperty rdf:resource="#you_maixiang"/>\s*<owl:someValuesFrom rdf:resource="#([^"]+)"/>', blk)
    ljs  = re.findall(r'<belongsToLiujing rdf:resource="#([^"]+)"/>', blk)
    return f, syms, mxs, ljs

# all 方证 referenced by the test + the wrong winners
targets = """Shizaotangzheng Guadisanzheng Mijiandaofangzheng Zhudanzhifangzheng Yiwuguaditangzheng
Zhugaofajianzheng Xiaoshifanshisanzheng Jupizhurutangzheng Jupitangzheng Juzhijiangtangzheng
Wengetangzheng Zishentangzheng Helilesanzheng Shengmabiejiatangzheng
Shengmabiejiaquxionghuangshujiaotangzheng Kushentangzheng Xionghuangxunfangzheng
Biejiajianwanzheng Shuqisanzheng Mulitangzheng Mulizexiesanzheng Dahuanggansuitangzheng
Sinisanzheng Jijiaolihuangwanzheng Fuzijingmitangzheng Yiyifuzisanzheng Zhulingtangzheng
Fangjifulingtangzheng Zhulingsanzheng Zexietangzheng Fulingxingrengancaotangzheng
Mufangjitangzheng Mufangjiqushigaojiafulingmangxiaotangzheng Gansuibanxiatangzheng
Fangjihuangqitangzheng Gualoumulisanzheng Shuyuwanzheng Tianxiongsanzheng
Shechuangzisanzheng Langyatangzheng Zhizhusanzheng Bentuntangzheng Xuanfuhuatangzheng
Zaojiawanzheng Qianjinweijingtangzheng Tinglidazaoxiefeitangzheng Maimendongtangzheng
Zeqitangzheng Xumingtangzheng Dabanxiatangzheng Baihexifangzheng""".split()

out.write("## 本体方证约束 vs ABox 实例存在性\n")
out.write("（标记 ✗ 的为本体自身引用了不存在的实例 = 本体缺陷）\n")
for cls in targets:
    f, syms, mxs, ljs = constraints(cls)
    if f is None:
        out.write(f"\n{cls}: 未找到\n"); continue
    miss_s = [s for s in syms if s not in inst]
    miss_m = [m for m in mxs if m not in inst]
    out.write(f"\n{cls} [{f}]")
    out.write(f"\n  lj={ljs}")
    out.write(f"\n  症状: {[(s, '✓' if s in inst else '✗缺失') for s in syms]}")
    if mxs: out.write(f"\n  脉象: {[(m, '✓' if m in inst else '✗缺失') for m in mxs]}")
    if miss_s or miss_m:
        out.write(f"\n  >>> 本体悬空引用: {miss_s + miss_m}")

open(os.path.join(BASE, '_duli_fzcheck.txt'), 'w', encoding='utf-8').write(out.getvalue())
print("done")
