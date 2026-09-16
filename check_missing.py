import re

original_file = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology\extracted\tcm-fangzheng-jianjia.owl"
with open(original_file, 'r', encoding='utf-8') as f:
    content = f.read()
    matches = re.findall(r'rdf:about="(#\w+zheng)"', content)
    for m in sorted(set(matches)):
        if 'Mahuang' in m or 'Yuebi' in m:
            print(m)
