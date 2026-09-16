import re
import os

fangzheng_dir = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology\fangzheng"

# Search for variants
patterns = [
    (r'Gegen.*zheng', 'Gegen variants'),
    (r'Mahuang.*jiazhuang', 'Mahuangjiazhuang variants'),
    (r'Tongmai.*zhudan', 'Tongmai variants'),
    (r'Yuebi.*jiazhuang', 'Yuebi variants')
]

all_zhengs = set()
for filename in os.listdir(fangzheng_dir):
    if filename.endswith('.owl'):
        filepath = os.path.join(fangzheng_dir, filename)
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
            matches = re.findall(r'rdf:about="(#\w+zheng)"', content)
            all_zhengs.update(matches)

for pattern, desc in patterns:
    print(f"\n{desc}:")
    matches = [z for z in all_zhengs if re.search(pattern, z, re.IGNORECASE)]
    if matches:
        for m in sorted(matches):
            print(f"  {m}")
    else:
        print("  No matches found")
