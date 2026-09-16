# -*- coding: utf-8 -*-
import os
ont_root = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology'
for fn, cls in [('shaoyang_yangming.owl', 'Dahuanggansuitangzheng'),
                ('taiyang.owl', 'Yuebijiazhutangzheng')]:
    fp = os.path.join(ont_root, 'fangzheng', fn)
    b = open(fp, 'rb').read()
    try:
        t = b.decode('utf-8')
        print(fn, 'utf-8 OK')
    except Exception as e:
        print(fn, 'utf-8 FAIL', e)
        t = b.decode('gbk')
        print(fn, 'gbk OK')
    idx = t.find(cls)
    print('  find cls at', idx)
    if idx >= 0:
        print('  ctx:', repr(t[max(0,idx-60):idx+60]))
