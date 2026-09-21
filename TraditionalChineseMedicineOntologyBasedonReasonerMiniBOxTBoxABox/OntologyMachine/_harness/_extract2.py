# -*- coding: utf-8 -*-
import io

LOG = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\OntologyMachine\app_run_fuzheng.log"
lines = io.open(LOG, encoding="utf-8", errors="replace").read().splitlines()

targets = ["Fuzijingmitangzheng","Zhulingsanzheng","Gansuibanxiatangzheng",
           "Banxiaxiexintangzheng","Gualouqumaiwanzheng","Neibudangguijianzhongtangzheng",
           "Mahuangtangzheng","Xiayuxuetangzheng","Baihexifangzheng","Painongtangzheng"]

out = io.open(r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\OntologyMachine\_harness\_cases2.txt","w",encoding="utf-8")

for t in targets:
    out.write("#"*90+"\n### TARGET %s\n" % t)
    for i, ln in enumerate(lines):
        if "方证打分:" not in ln: continue
        if t+"(" not in ln: continue
        # 回溯找 症状映射 / 八纲完成 / 六经完成
        sym = None; bg = None; lj = None
        for k in range(i, max(0,i-120), -1):
            if sym is None and "症状映射[第0轮]" in lines[k]: sym = lines[k]
            if bg is None and "八纲完成:" in lines[k]: bg = lines[k]
            if lj is None and "六经完成:" in lines[k]: lj = lines[k]
            if sym and bg and lj: break
        # 该方证在打分串中的片段
        seg = ln.split(t+"(",1)[1].split(")",1)[0]
        out.write("-"*90+"\n")
        out.write("  line=%d  score=%s(%s)\n" % (i, t, seg))
        if sym: out.write("  " + sym.split(" - ",1)[-1].strip() + "\n")
        if bg:  out.write("  " + bg.split(" - ",1)[-1].strip() + "\n")
        if lj:  out.write("  " + lj.split(" - ",1)[-1].strip() + "\n")
out.close()
print("ok")
