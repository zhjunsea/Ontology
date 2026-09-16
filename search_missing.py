import os

fangzheng_dir = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology\fangzheng"

search_terms = ['Mahuangjiazhuang', 'Yuebijiazhuang']

for filename in os.listdir(fangzheng_dir):
    if filename.endswith('.owl'):
        filepath = os.path.join(fangzheng_dir, filename)
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
            for term in search_terms:
                if term in content:
                    print(f"Found '{term}' in {filename}")
                    # Find context around the match
                    idx = content.find(term)
                    start = max(0, idx - 100)
                    end = min(len(content), idx + 100)
                    print(f"  Context: ...{content[start:end]}...")
                    print()
