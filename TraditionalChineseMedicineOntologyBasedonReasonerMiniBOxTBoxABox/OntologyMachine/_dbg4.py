# -*- coding: utf-8 -*-
import os
p = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology\fangzheng\shaoyang_yangming.owl'
print('size', os.path.getsize(p))
t = open(p, encoding='utf-8').read()
for k in ['Dahuanggansuitangzheng', 'Houpoqiwutangzheng',
          'Houpoushengjiangbanxiagancaorenshentangzheng',
          'Houposhengjiangbanxiagancaorenshentangzheng']:
    print(k, t.count('#' + k))
