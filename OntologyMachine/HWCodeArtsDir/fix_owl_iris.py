#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""方案甲：将 ziweidoushu-all.owl 中 rdf:about / rdf:resource 属性值里的
prefixed name 展开为完整绝对 IRI（元素名保持不变）。"""
import re
import sys

PATH = "OntologyFrameworkZiWeiDouShu/ontology/ziweidoushu-all.owl"

NS = {
    "core": "http://example.org/ziweidoushu/core#",
    "calendar": "http://example.org/ziweidoushu/calendar#",
    "chart": "http://example.org/ziweidoushu/chart#",
    "star": "http://example.org/ziweidoushu/star#",
    "layout": "http://example.org/ziweidoushu/layout#",
    "state": "http://example.org/ziweidoushu/state#",
    "palace": "http://example.org/ziweidoushu/palace#",
    "pattern": "http://example.org/ziweidoushu/pattern#",
    "fortune": "http://example.org/ziweidoushu/fortune#",
    "reading": "http://example.org/ziweidoushu/reading#",
    "diagnosis": "http://example.org/ziweidoushu/diagnosis#",
    "rules": "http://example.org/ziweidoushu/rules#",
}
PREFIXES = "|".join(NS.keys())

pattern = re.compile(r'(rdf:(?:about|resource))="(' + PREFIXES + r'):([^"]*)"')


def repl(m):
    attr, prefix, local = m.group(1), m.group(2), m.group(3)
    return '%s="%s%s"' % (attr, NS[prefix], local)


with open(PATH, "r", encoding="utf-8") as f:
    data = f.read()

result, n = pattern.subn(repl, data)
print("替换处数:", n)

with open(PATH, "w", encoding="utf-8") as f:
    f.write(result)

# 校验残留
remaining = pattern.findall(result)
print("残留(prefixed)处数:", len(remaining))
for r in remaining[:10]:
    print("  残留:", r)
