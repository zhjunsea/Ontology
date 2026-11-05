#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""提取 PlumBlossomOntologyJobWorker 中 58 个 `private static String JUAN_*` 五卷照录常量，
输出 JSON（code/juan/title/content）供 data.sql 生成与 verify_new_tables 比对。

用法：python _gen/extract_juan.py [--json out.json]
"""
import io, json, re, sys

JAVA = 'src/main/java/com/ocean/ontologyframework/plumblossom/PlumBlossomOntologyJobWorker.java'
JUAN_MAP = {'JUAN_YI_': '一', 'JUAN_ER_': '二', 'JUAN_SAN_': '三', 'JUAN_SI_': '四', 'JUAN_WU_': '五'}


def parse_strings_from(src, pos):
    """从 pos 起扫描 Java 字符串拼接，返回 (joined, semi_index)。以「字符串外的首个 ;」为语句终止。"""
    out, cur, i, n, ins = [], '', pos, len(src), False
    while i < n:
        c = src[i]
        if ins:
            if c == '\\' and i + 1 < n:
                nxt = src[i + 1]
                cur += {'n': '\n', 't': '\t', '"': '"', '\\': '\\'}.get(nxt, nxt)
                i += 2
                continue
            if c == '"':
                ins = False
                out.append(cur)
                cur = ''
            else:
                cur += c
        else:
            if c == '"':
                ins = True
            elif c == ';':
                return ''.join(out), i
        i += 1
    return ''.join(out), -1


def main():
    src = io.open(JAVA, encoding='utf-8').read()
    pat = re.compile(r'private\s+static\s+String\s+(JUAN_[A-Z0-9_]+)\s*=')
    rows = []
    for m in pat.finditer(src):
        name = m.group(1)
        val, semi = parse_strings_from(src, m.end())
        # 取 ; 后到行尾的注释作 title
        eol = src.find('\n', semi) if semi >= 0 else -1
        tail = src[semi + 1:eol] if semi >= 0 and eol > 0 else ''
        cm = re.search(r'//\s*(.+)$', tail)
        title = cm.group(1).strip() if cm else ''
        juan = next((v for k, v in JUAN_MAP.items() if name.startswith(k)), '')
        rows.append({'code': name, 'juan': juan, 'title': title, 'content': val})

    # 以 provider 方法 return 的「卷之X·标题——」串覆盖 title（更准确，覆盖声明注释缺失者）
    prov = {}
    for mm in re.finditer(r'"((?:[^"\\]|\\.)*?——)"\s*\+\s*(JUAN_[A-Z0-9_]+)', src):
        prov[mm.group(2)] = mm.group(1)[:-2].replace('——', '').strip()  # 去尾「——」
    for r in rows:
        if r['code'] in prov:
            r['title'] = prov[r['code']]

    total = sum(len(r['content']) for r in rows)
    cjk = sum(1 for r in rows for ch in r['content'] if '\u4e00' <= ch <= '\u9fff')
    print(f'[INFO] 提取 {len(rows)} 条；content 总字符 {total}；CJK {cjk}')
    # 异常字符检查（SQL 需转义）
    for r in rows:
        c = r['content']
        for ch, lab in [("'", 'ASCII单引号'), ('\\', '反斜杠'), ('\n', '换行'), ('\x00', 'NUL')]:
            if ch in c:
                print(f"  !! {r['code']} 含 {lab} ×{c.count(ch)}")
    # 长度极值
    mx = max(rows, key=lambda r: len(r['content']))
    print(f"[INFO] 最长 {mx['code']} = {len(mx['content'])} 字；最短 {min(len(r['content']) for r in rows)} 字")
    out = sys.argv[sys.argv.index('--json') + 1] if '--json' in sys.argv else '/tmp/juan.json'
    io.open(out, 'w', encoding='utf-8').write(json.dumps(rows, ensure_ascii=False, indent=1))
    print(f'[INFO] 写出 {out}')


if __name__ == '__main__':
    main()
