# -*- coding: utf-8 -*-
import re, os
ont_root = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology'
fp = os.path.join(ont_root, 'fangzheng', 'shaoyang_yangming.owl')
t = open(fp, encoding='utf-8').read()
cls = 'Dahuanggansuitangzheng'
pat = r'<owl:Class rdf:about="#' + re.escape(cls) + r'">'
print('pattern:', pat)
m = re.search(pat, t)
print('match:', m)
# try simpler
m2 = re.search(re.escape('<owl:Class rdf:about="#' + cls + '">'), t)
print('match2:', m2)
print('literal present:', ('<owl:Class rdf:about="#' + cls + '">') in t)
