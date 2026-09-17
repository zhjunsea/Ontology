import os, re, glob

root = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology"
files = glob.glob(os.path.join(root, "**", "*.owl"), recursive=True)

texts = {}
for f in files:
    texts[f] = open(f, encoding="utf-8").read()

targets = """Sinijiarenshentangzheng Baitongtangzheng Ganjiangfuzitangzheng Gancaotangzheng
Jiegengtangzheng Banxiasanjitangzheng Gancaoganjiangtangzheng
Guizhijiashaoyaotangzheng Yuebijiabanxiatangzheng Tongmaisinitangzheng
Tongmaisinijiazhudanzhitangzheng Fulingsinitangzheng Baitongjiazhudanzhitangzheng
Fuzitangzheng Taohuatangzheng Huanglianejiaotangzheng Zhufutangzheng Kujiutangzheng
Fulingzexietangzheng Guizhijialonggumulitangzheng Chishizhiyuyuliangtangzheng
Lingganwuweijiangxintangzheng Lingganwuweijiangxinxiatangzheng
Lingganwuweijiajiangxinbanxingdahuangtangzheng Guilingwuweigancaotangzheng
Huangqiguizhiwuwutangzheng Huangqijianzhongtangzheng Neibudangguijianzhongtangzheng
Xiaojianzhongtangzheng Dajianzhongtangzheng Gancaoganjiangfulingbaizhutangzheng
Lingguizhugantangzheng Fulinggancaotangzheng""".split()

class_start = re.compile(r'<owl:Class rdf:about="#([^"]+)"')
for f in files:
    txt = texts[f]
    rel = os.path.relpath(f, root)
    marks = [(m.start(), m.group(1)) for m in class_start.finditer(txt)]
    for i, (pos, name) in enumerate(marks):
        if name not in targets:
            continue
        end = marks[i+1][0] if i+1 < len(marks) else len(txt)
        body = txt[pos:end]
        fillers = re.findall(r'<owl:onProperty rdf:resource="#(you_zhengzhuang|you_maixiang|you_shexiang)"\s*/>\s*<owl:someValuesFrom rdf:resource="#([^"]+)"', body)
        lj = re.findall(r'belongsToLiujing rdf:resource="#([^"]+)"', body)
        print(f"[{rel}] {name}  liujing={lj}")
        for prop, val in fillers:
            print(f"     {prop} -> {val}")
        print()
