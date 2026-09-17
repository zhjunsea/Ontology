# -*- coding: utf-8 -*-
import re, glob, os

root = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology"
files = glob.glob(os.path.join(root, "**", "*.owl"), recursive=True)

targets = [
    "Baihedihuangtangzheng","Baihejizitangzheng","Baihezhimutangzheng",
    "Baihexifangzheng","Baihehuashisanzheng",
    "Shaoyaogancaotangzheng","Shaoyaogancaofuzitangzheng",
    "Dahuangzhechongwanzheng","Baiyetangzheng","Xiexintangzheng",
    "Huashidaizhetangzheng","Fanshitangzheng","Xiayuxuetangzheng",
    "Suanzaorentangzheng","Shenqiwanzheng","Honglanhuajiuzheng",
    "Zhishutangzheng","Renshentangzheng","Gancaofenmitangzheng",
    "Houshiheisanzheng","Fengyintangzheng","Fangjidihuangtangzheng",
    "Sanhuangtangzheng","Wangbuliuxingsanzheng","Painongsanzheng",
    "Painongtangzheng","Huanglianfenzheng","Jishibaisanzheng",
    "Toufengmosanzheng","Tuguagensanzheng","Fanshiwanzheng",
    "Jiaoaitangzheng","Baizhusanzheng","Zhupidawanzheng",
    "Sanwuhuangqintangzheng","Guizhifulingwanzheng",
    "Houposhengjiangbanxiagancaorenshentangzheng","Xuanfudaizhetangzheng",
    "Kuizifulingsanzheng","Yiyifuzibaijiangsanzheng",
    "Dangguisanzheng","Dangguibeimukushenwanzheng","Dangguishaoyaosanzheng",
    "Wenjingtangzheng","Gualouxiebaibanxiatangzheng","Zhishixiebaiguizhitangzheng",
]

def find_class(name):
    for f in files:
        t = open(f, encoding="utf-8", errors="replace").read()
        m = re.search(r'<owl:Class rdf:about="#'+re.escape(name)+r'">(.*?)</owl:Class>', t, re.S)
        if m:
            return os.path.basename(f), m.group(1)
    return None, None

for name in targets:
    f, body = find_class(name)
    if body is None:
        print(f"{name}: NOT FOUND")
        continue
    lj = re.findall(r'<belongsToLiujing rdf:resource="#([^"]+)"', body)
    # 等价类里的命名类（六经）
    liujing_in_eq = re.findall(r'<owl:Class rdf:about="#((?:Taiyang|Shaoyang|Yangming|Taiyin|Shaoyin|Jueyin)bing)"', body)
    fills = re.findall(r'<owl:onProperty rdf:resource="#(you_\w+)"/>\s*<owl:someValuesFrom rdf:resource="#([^"]+)"', body, re.S)
    print(f"{name} [{f}]")
    print(f"    belongsToLiujing={lj}  eq六经={liujing_in_eq}")
    print(f"    filler={fills}")
