import re, os, glob

original = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerTBoxABox\ontology\tcm-fangzheng-jianjia.owl'
split_dir = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology\fangzheng'

with open(original, 'r', encoding='utf-8') as f:
    content = f.read()
orig_classes = set()
for m in re.finditer(r'rdf:about="#(\w+)"', content):
    cls = m.group(1)
    if cls.endswith('zheng'):
        orig_classes.add(cls)
print(f'原始文件方证类数: {len(orig_classes)}')

split_classes = {}
for fpath in sorted(glob.glob(os.path.join(split_dir, '*.owl'))):
    fname = os.path.basename(fpath)
    with open(fpath, 'r', encoding='utf-8') as f:
        content = f.read()
    classes = set()
    for m in re.finditer(r'rdf:about="#(\w+)"', content):
        cls = m.group(1)
        if cls.endswith('zheng'):
            classes.add(cls)
    split_classes[fname] = classes

all_split = set()
for fname, classes in split_classes.items():
    all_split.update(classes)
    print(f'  {fname}: {len(classes)} 个方证')

print(f'\n分割文件总计方证类: {len(all_split)}')

missing = orig_classes - all_split
extra = all_split - orig_classes

print(f'\n========== 分割文件的方证类(原始文件没有的): {len(extra)} ==========')
for cls in sorted(extra):
    for fname, classes in split_classes.items():
        if cls in classes:
            print(f'  {cls} (在 {fname})')
            break

print(f'\n========== 原始文件有但分割文件缺失的方证类: {len(missing)} ==========')
for cls in sorted(missing):
    print(f'  {cls}')
