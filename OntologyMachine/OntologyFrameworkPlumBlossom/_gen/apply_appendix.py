# -*- coding: utf-8 -*-
"""幂等地把 appendix_text 的 DELETE/INSERT 写入 ontology/database/data.sql。"""
import json, io

rows = json.load(open('_gen/appendix.json', encoding='utf-8'))['rows']
p = 'ontology/database/data.sql'
sql = open(p, encoding='utf-8').read()

DEL = 'DELETE FROM appendix_text;\n'
if DEL not in sql:
    anchor = 'DELETE FROM calendar_heavenly_stem;\n'
    assert anchor in sql, 'DELETE anchor not found'
    sql = sql.replace(anchor, anchor + DEL, 1)

MARK = '-- 11. 附录·渔樵对问 / 宋史·邵雍传（附录文献；第26轮补录）'
if MARK not in sql:
    def sq(s):
        return s.replace("'", "''")
    lines = []
    for (n, w, idx, tp, tx, sr, lb) in rows:
        lines.append("('%s', '%s', %d, '%s', '%s', '%s', '%s')" % (n, w, idx, sq(tp), sq(tx), sr, lb))
    block = ('\n\n-- ============================================================================\n'
             + MARK + '\n'
             + '--    依《本体顶层设计》原著范围（附录：一撮金·渔樵对问·宋史·邵雍传）；\n'
             + '--    一撮金承载于 yicuo_jin_yao（第 8b 表），此表承载余二篇全文（唯一真源）。\n'
             + '-- ============================================================================\n'
             + 'INSERT INTO appendix_text (name, work_name, section_index, section_topic, text_body, source_ref, label) VALUES\n'
             + ',\n'.join(lines) + ';\n')
    sql = sql.rstrip('\n') + block

open(p, 'w', encoding='utf-8').write(sql)
print('appendix_text rows written =', len(rows))
