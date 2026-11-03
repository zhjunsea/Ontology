# -*- coding: utf-8 -*-
import io, re
CJK=re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]")
def cjk(x): return "".join(CJK.findall(x))
NC=cjk(io.open('design/_epub_dump.txt',encoding='utf-8').read())
# (off, ln)  —— ln 为长度
cands=[(0,435),(953,521),(6331,4100),(10445,55),(10534,12),(10560,14),(10610,84),
(17329,129),(22852,38),(34306,164),(45438,183),(60907,16),(61191,34),(61431,26),
(61977,34),(63120,43),(65334,20)]
for off,ln in cands:
    a=max(0,off-35); b=min(len(NC),off+ln+35)
    G=NC[off:off+ln]
    g=(G[:90]+"……"+G[-90:]) if ln>200 else G
    print("="*100)
    print(f"OFF={off} LEN={ln}")
    print(f"  LEFT : ...{NC[a:off]}")
    print(f"  GAP  : 【{g}】")
    print(f"  RIGHT: {NC[off+ln:b]}...")
