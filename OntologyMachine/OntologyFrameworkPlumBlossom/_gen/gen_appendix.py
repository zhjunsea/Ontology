# -*- coding: utf-8 -*-
"""第26轮：抽取附录·渔樵对问 / 宋史·邵雍传 全文（_epub_dump.txt），
生成 DB(INSERT)、Java 静态块、JSON 三份产物。
段落合并规则：连续非空行累积，至行尾为句读终结（。！””）时成一段；
跳过纯脚注标记 [NN] 与纯数字行。"""
import json, re, os

L = open('design/_epub_dump.txt', encoding='utf-8').read().split('\n')


def extract(a, b):
    out, buf = [], ''
    for i in range(a, b):
        s = L[i].strip()
        if not s:
            continue
        if re.fullmatch(r'\[\d+\]', s) or re.fullmatch(r'\d+', s):
            continue
        buf += s
        if buf and buf[-1] in '。！””）':
            out.append(buf)
            buf = ''
    if buf:
        out.append(buf)
    return out


# 渔樵对问：标题 L10129，正文 L10130..10355；宋史·邵雍传：标题 L10357，署名 L10361，正文 L10362..10395
yq = extract(10130, 10356)
sy = extract(10362, 10396)

print('渔樵对问 段数 =', len(yq))
print('宋史·邵雍传 段数 =', len(sy))

# 渔樵对问：按发言角色客观标注（对话体，忠实分段）
def yq_topic(s):
    if s.startswith('樵者') or s.startswith('樵者问') or s.startswith('樵者赞'):
        return '樵者'
    if s.startswith('渔者') or s.startswith('钓者'):
        return '渔者'
    if s.startswith('曰：“') or s.startswith('曰：'):
        return '答'
    return '叙'

# 宋史·邵雍传：10 段，逐段标目
SY_TOPIC = [
    '籍贯·早年', '励志游学', '受学李之才·先天之旨', '安乐窝·行窝', '洛中师友·德化',
    '熙宁新法·宽民', '应诏辞官·赐谥康节', '内圣外王之学', '前知·玩世之辨', '临终·著述',
]

rows = []
for i, t in enumerate(yq):
    rows.append(('ATXT_YQ_%02d' % (i + 1), '渔樵对问', i + 1, yq_topic(t), t, '附录·渔樵对问',
                 '附录·渔樵对问·第%d则' % (i + 1)))
for i, t in enumerate(sy):
    topic = SY_TOPIC[i] if i < len(SY_TOPIC) else '续'
    rows.append(('ATXT_SY_%02d' % (i + 1), '宋史·邵雍传', i + 1, topic, t, '附录·宋史·邵雍传',
                 '附录·宋史·邵雍传·第%d段' % (i + 1)))

os.makedirs('_gen/out', exist_ok=True)


def sq(s):
    return s.replace("'", "''")


# SQL
with open('_gen/out/appendix_sql.txt', 'w', encoding='utf-8') as f:
    lines = []
    for (n, w, idx, tp, tx, sr, lb) in rows:
        lines.append("('%s', '%s', %d, '%s', '%s', '%s', '%s')" % (n, w, idx, sq(tp), sq(tx), sr, lb))
    f.write(',\n'.join(lines) + ';\n')

# Java 静态块
with open('_gen/out/appendix_java.txt', 'w', encoding='utf-8') as f:
    for (n, w, idx, tp, tx, sr, lb) in rows:
        jtx = tx.replace('\\', '\\\\').replace('"', '\\"')
        f.write('        putAt("%s", "%s", "%s", "%s");\n' % (n, w, tp, jtx))

json.dump({'rows': [[r[0], r[1], r[2], r[3], r[4], r[5], r[6]] for r in rows]},
          open('_gen/appendix.json', 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print('rows =', len(rows), '（渔樵', len(yq), '/ 邵雍传', len(sy), '）')
print('total chars =', sum(len(r[4]) for r in rows))
