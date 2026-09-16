# -*- coding: utf-8 -*-
import shutil, os
pairs = [
 ('../ontology/database/fangji-yaowu.sql', '_b81_backup/fangji-yaowu.sql.bak'),
 ('../ontology/tcm-fangji-abox.owl', '_b81_backup/tcm-fangji-abox.owl.bak'),
 ('../ontology/tcm-yaowu-abox.owl', '_b81_backup/tcm-yaowu-abox.owl.bak'),
]
for src, dst in pairs:
    shutil.copy2(src, dst)
    print('OK', dst, os.path.getsize(dst))
