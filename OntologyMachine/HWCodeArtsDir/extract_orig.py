import re, os

d = r'C:/Users/ocean/AppData/Local/Temp/mhys_epub/OEBPS'
out = r'D:/work/Ontology/OntologyMachine/HWCodeArtsDir'
os.makedirs(out, exist_ok=True)
for i in range(12):
    p = os.path.join(d, f'text{i:05d}.html')
    if not os.path.exists(p):
        continue
    html = open(p, encoding='utf-8').read()
    html = re.sub(r'<(script|style).*?</\1>', '', html, flags=re.S)
    t = re.sub(r'<[^>]+>', '\n', html)
    t = t.replace('&nbsp;', ' ').replace('&amp;', '&')
    t = re.sub(r'\n\s*\n+', '\n', t).strip()
    open(os.path.join(out, f'orig_{i:05d}.txt'), 'w', encoding='utf-8').write(t)
    print(i, len(t), '|', t[:70].replace('\n', ' '))
