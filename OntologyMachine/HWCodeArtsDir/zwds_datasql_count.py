# -*- coding: utf-8 -*-
import re, io
p = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkZiWeiDouShu\ontology\database\data.sql"
with io.open(p, encoding="utf-8") as f:
    lines = [l.rstrip("\n") for l in f]

cur = None
counts = {}
order = []
in_insert = False
for l in lines:
    m = re.match(r"^INSERT INTO `([a-z_]+)`", l)
    if m:
        cur = m.group(1)
        order.append(cur)
        counts[cur] = 0
        in_insert = True
        # count values on same line after VALUES (rare here)
        continue
    if in_insert and cur:
        s = l.strip()
        if s.startswith("("):
            counts[cur] += 1
        if s.endswith(";"):
            in_insert = False

expected = {
 "ganzhi_stem":10,"ganzhi_branch":12,"sexagenary_cycle":60,"nayin":30,
 "bureau_stage":None,"true_solar_time":None,"leap_month":None,
 "star_layout":150,"brightness_map":252,"sihua_map":40,"palace_stem_map":None,
 "deity_rule":None,"tongyuan_rule":258,"star_character":14,"star_xingxing":25,
 "star_qa_character":20,"verify_star_traits":14,"verify_bureau_traits":2,
 "taisui_xiaoxian":12,"palace_zhi_ge":12,"palace_zhi_po":12,"palace_fugui":12,
 "palace_pinjian":10,"idle_verse":9,"idle_star":8,"brightness_variant":2,
 "limit_taboo":12,"sihua_qa":4,"star_block_all":36,"classic_corpus":813,
 "pattern_verse":27,
}
print("%-22s %8s %8s  %s" % ("table","rows","expect","OK?"))
for t in order:
    e = expected.get(t)
    ok = "" if e is None else ("OK" if counts[t]==e else "MISMATCH!")
    print("%-22s %8d %8s  %s" % (t, counts[t], str(e), ok))
