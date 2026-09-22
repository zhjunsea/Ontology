# -*- coding: utf-8 -*-
import re, sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

BASE = r"D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology/fangzheng"
TARGETS = {
    "Gancaotangzheng": "shaoyin_taiyin.owl",
    "Daxianxiongtangzheng": "taiyang.owl",
    "Sanwubaisanzheng": "taiyang.owl",
    "Fulingguizhibaizhugancaotangzheng": "taiyang.owl",
    "Guizhifulingwanzheng": "zabing.owl",
    "Jishibaisanzheng": "zabing.owl",
    "Wutouguizhitangzheng": "hebing.owl",
    "Gancaofuzitangzheng": "taiyang.owl",
    "Chaihuguizhiganjiangtangzheng": "shaoyang_yangming.owl",
    # 参考：通过的同类
    "Zhufutangzheng": "shaoyin_taiyin.owl",
    "Daxianxiongwanzheng": "taiyang.owl",
    "Lingguizhugantangzheng": "shaoyin_taiyin.owl",
    "Guizhifuzitangzheng": "taiyang.owl",
    "Gancaofenmitangzheng": "zabing.owl",
}

def block_of(text, name):
    m = re.search(r'<owl:Class rdf:about="#%s">' % re.escape(name), text)
    if not m:
        return None
    start = m.start()
    end = text.find('</owl:Class>', start)
    return text[start:end+len('</owl:Class>')]

for name, f in TARGETS.items():
    p = BASE + "/" + f
    try:
        text = open(p, encoding='utf-8').read()
    except Exception as e:
        print("ERR", name, e); continue
    b = block_of(text, name)
    print("=" * 70)
    print("### %s  (%s)" % (name, f))
    print(b if b else "  <NOT FOUND>")
