#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""打印底本规范化串 ND 的区间；用法: python _gen/nd.py a b [step]"""
import io, re, sys
s = io.open('design/_epub_dump.txt', encoding='utf-8').read()
def norm(x):
    x = x.replace("&quot;", '"')
    x = re.sub(r"\[\d+\]", "", x)
    x = x.replace("\u3000", "").replace(" ", "")
    x = re.sub(r"[\r\n]", "", x)
    x = re.sub(r"[〔〕]", "", x)
    return x
ND = norm(s)
if __name__ == "__main__":
    a = int(sys.argv[1]); b = int(sys.argv[2])
    print("ND_LEN=%d" % len(ND))
    print(ND[a:b])
