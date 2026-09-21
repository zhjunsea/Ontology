# -*- coding: utf-8 -*-
import io, re, sys

LOG = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\OntologyMachine\app_run_fuzheng.log"
lines = io.open(LOG, encoding="utf-8", errors="replace").read().splitlines()

# 目标用例的「症状映射」特征串
cases = {
 "1_Fuzijingmitang": ["Futong_instance", "Fuzhongleiming_instance", "Xiongxiekuman_instance", "Outu_instance"],
 "2_Zhulingsan": ["Ou_instance", "Tu_instance", "Xiongman_instance", "Housishui_instance"],
 "3_Gansuibanxia": ["Xinxiapi_instance", "Xiali_instance", "Touxuan_instance"],
 "4_Banxiaxiexin": ["Xinxiapi_instance", "Ou_instance", "Xiali_instance", "Fuman_instance", "Buke_instance", "Shibuxia_instance"],
 "5_Gualouqumai": ["Xiaobianbuli_instance", "Kouke_instance"],
 "6_Neibudanggui": ["Citong_instance", "Shaoqi_instance", "Shaofujuji_instance", "Huoyinyaoji_instance", "Bunengyinshi_instance"],
 "7_Mahuang": ["Fare_instance", "Ehan_instance", "Wuhan_instance"],
 "8_Xiayuxue": ["Futong_instance", "Shaofujijie_instance", "Citong_instance"],
 "9_Baihexi": ["Kouke_instance"],
 "10_Painong": ["Jinchuang_instance", "Yantong_instance"],
}

def find_blocks(syms):
    res = []
    for i, ln in enumerate(lines):
        if "症状映射[第0轮]" not in ln: continue
        if all(s in ln for s in syms):
            res.append(i)
    return res

out = io.open(r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\OntologyMachine\_harness\_cases.txt","w",encoding="utf-8")
for name, syms in cases.items():
    idxs = find_blocks(syms)
    out.write("#"*90+"\n")
    out.write("CASE %s  matches=%d  lines=%s\n" % (name, len(idxs), idxs))
    out.write("#"*90+"\n")
    for i in idxs:
        # 从该行向后到下一个「症状映射」行
        j = i+1
        while j < len(lines) and "症状映射[第0轮]" not in lines[j]:
            j += 1
        blk = lines[i:j]
        for ln in blk:
            # 只保留关键行
            if any(k in ln for k in ["症状映射[第0轮]","八纲来源","八纲完成","六经来源","六经完成",
                                     "realize 命中","realize 无匹配","方证完成","方证打分",
                                     "命中路径","诊断解释","物化] 断言到患者","物化] 八纲判据"]):
                out.write("  "+ln.split(" - ",1)[-1]+"\n")
        out.write("  " + "-"*60 + "\n")
out.close()
print("ok")
