# -*- coding: utf-8 -*-
import re, io, os

BASE = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology"
FZ = os.path.join(BASE, 'fangzheng')
out = io.StringIO()

classes = """Shizaotangzheng Guadisanzheng Mijiandaofangzheng Zhudanzhifangzheng Yiwuguaditangzheng
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
Zeqitangzheng Xumingtangzheng""".split()

# load all fangzheng files
files = {}
for f in os.listdir(FZ):
    if f.endswith('.owl'):
        files[f] = open(os.path.join(FZ, f), encoding='utf-8').read()

def find_block(txt, cls):
    m = re.search(r'<owl:Class rdf:about="#' + re.escape(cls) + r'">', txt)
    if not m: return None
    start = m.start()
    # find matching close: next '</owl:Class>' at same indent
    end = txt.find('</owl:Class>', start)
    return txt[start:end]

for cls in classes:
    owner = None
    for f, txt in files.items():
        if f'<owl:Class rdf:about="#{cls}">' in txt:
            owner = f; break
    out.write(f"\n{'='*70}\n## {cls}   [文件: {owner}]\n")
    if not owner:
        out.write("  !!! 未在任何 fangzheng 文件中找到\n")
        continue
    blk = find_block(files[owner], cls)
    syms = re.findall(r'<owl:onProperty rdf:resource="#you_zhengzhuang"/>\s*<owl:someValuesFrom rdf:resource="#([^"]+)"/>', blk)
    mxs  = re.findall(r'<owl:onProperty rdf:resource="#you_maixiang"/>\s*<owl:someValuesFrom rdf:resource="#([^"]+)"/>', blk)
    ljs  = re.findall(r'<belongsToLiujing rdf:resource="#([^"]+)"/>', blk)
    out.write(f"  belongsToLiujing: {ljs}\n")
    out.write(f"  症状约束({len(syms)}): {syms}\n")
    out.write(f"  脉象约束({len(mxs)}): {mxs}\n")

open(os.path.join(BASE, '_duli_fz.txt'), 'w', encoding='utf-8').write(out.getvalue())
print("done")
