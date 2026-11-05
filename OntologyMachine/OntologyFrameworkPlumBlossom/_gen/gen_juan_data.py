#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""由 /tmp/juan.json 生成 `juan_text` 数据，幂等写入 ontology/database/data.sql（标记区替换）。
用法：python _gen/gen_juan_data.py
"""
import io, json, re

JAVA_JSON = 'C:/Users/ocean/AppData/Local/Temp/juan.json'
SQL = 'ontology/database/data.sql'
BEGIN = '-- >>> JUAN_TEXT BEGIN'
END = '-- <<< JUAN_TEXT END'


def q(s):
    """MySQL 字面量转义（单引号加倍；反斜杠加倍）。"""
    return s.replace('\\', '\\\\').replace("'", "''")


def build_block(rows):
    lines = []
    lines.append('-- ============================================================================')
    lines.append('-- 23) 五卷照录正文（58 章；卷之一至卷之五）——divination 域外置真源（第 52 轮 C-2 下沉）')
    lines.append('--     Java 侧 JUAN_* 照录常量仅「同源兜底」，运行期由本表覆盖；_gen/verify_new_tables 逐字证 Java≡DB。')
    lines.append(BEGIN)
    lines.append('DELETE FROM `juan_text`;')
    lines.append('INSERT INTO `juan_text` (`code`, `juan`, `title`, `content`) VALUES')
    vals = []
    for r in rows:
        title = r['title'].lstrip('；;· ').strip()
        vals.append("('%s', '%s', '%s', '%s')" % (q(r['code']), q(r['juan']), q(title), q(r['content'])))
    lines.append(',\n'.join(vals) + ';')
    lines.append(END)
    return '\n'.join(lines)


def main():
    rows = json.load(io.open(JAVA_JSON, encoding='utf-8'))
    block = build_block(rows)
    sql = io.open(SQL, encoding='utf-8').read()
    if BEGIN in sql and END in sql:
        pre = sql[:sql.index(BEGIN)]
        post = sql[sql.index(END) + len(END):]
        # 移除标记块前的说明注释行（以「-- ====」到标记间最后一段）以幂等
        pre = re.sub(r'-- =+\n-- 23\)[^\n]*\n-- +Java 侧 JUAN_[^\n]*\n$', '', pre)
        sql = pre + block + post
        print('[INFO] 替换既有 juan_text 标记区')
    else:
        if not sql.endswith('\n'):
            sql += '\n'
        sql += '\n' + block + '\n'
        print('[INFO] 追加 juan_text 数据块')
    io.open(SQL, 'w', encoding='utf-8', newline='').write(sql)
    print('[INFO] 写入 %d 行到 %s' % (len(rows), SQL))


if __name__ == '__main__':
    main()
