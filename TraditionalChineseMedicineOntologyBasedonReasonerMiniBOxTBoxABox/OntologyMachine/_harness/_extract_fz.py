import re, glob, sys

ONT = 'D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology'


def block(t, name):
    i = t.find('<owl:Class rdf:about="#%s">' % name)
    if i < 0:
        return None
    depth = 0
    k = i
    while True:
        no = t.find('<owl:Class', k + 1)
        nc = t.find('</owl:Class>', k + 1)
        if nc < 0:
            break
        if no >= 0 and no < nc:
            depth += 1
            k = no
        else:
            if depth == 0:
                return t[i:nc + 12]
            depth -= 1
            k = nc
    return None


frags = sys.argv[1:] if len(sys.argv) > 1 else [
    'Guizhixinjiatangzheng', 'Guizhijiaguitangzheng', 'Guizhijiahuangqitangzheng',
    'Gancaomahuangtangzheng', 'Gancaofuzitangzheng', 'Fulingguizhibaizhugancaotangzheng',
    'Daxianxiongtangzheng', 'Huangqishaoyaoguizhikujiutangzheng', 'Gualouguizhitangzheng',
    'Sanwubaisanzheng', 'Jishibaisanzheng', 'Guizhifulingwanzheng', 'Taohechengqitangzheng',
    'Fulingguizhigancaodazaotangzheng', 'Gancaotangzheng', 'Wutouguizhitangzheng',
    'Jiegengtangzheng', 'Banxiasanjitangzheng', 'Kujiutangzheng',
    'Chaihuguizhiganjiangtangzheng']

files = glob.glob(ONT + '/**/*.owl', recursive=True)
for fr in frags:
    for f in files:
        t = open(f, encoding='utf-8', errors='replace').read()
        b = block(t, fr)
        if b and 'equivalentClass' in b:
            eq = re.search(r'<owl:equivalentClass>(.*?)</owl:equivalentClass>', b, re.S)
            leaves = re.findall(r'someValuesFrom rdf:resource="#(\w+)"', eq.group(1)) if eq else []
            cls = re.findall(r'<owl:Class rdf:about="#(\w+)"', eq.group(1)) if eq else []
            ljs = re.findall(r'belongsToLiujing rdf:resource="#(\w+)"', b)
            print('%-38s file=%-22s lj=%s' % (fr, f.split('/')[-1], ljs))
            print('    classes=%s' % cls)
            print('    leaves =%s' % leaves)
            break
