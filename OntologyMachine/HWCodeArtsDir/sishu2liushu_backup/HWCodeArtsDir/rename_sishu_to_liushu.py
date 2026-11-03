# -*- coding: utf-8 -*-
"""
四柱八字应用：将「四书」全域更名/替换为「六书」。
- 字节级替换（UTF-8），不改动编码与 CRLF/LF。
- 幂等：仅处理含「四书」的文件。
- 备份：逐文件原样复制到 HWCodeArtsDir/sishu2liushu_backup/<相对路径>。
- 排除：target/、logs/、__pycache__/、*_backup/、备份/快照文件(*.bak* / data.sql.pre_*)、*.pyc、*.log
注：目录改名（design/四书全文整理、design/四书原文）由 git mv 单独执行。
"""
import os

ROOT = r"D:\work\Ontology\OntologyMachine"
ROOTS = [
    os.path.join(ROOT, "OntologyFrameworkSiZhuBaZi"),
    os.path.join(ROOT, "HWCodeArtsDir"),
]
BACKUP = os.path.join(ROOT, "HWCodeArtsDir", "sishu2liushu_backup")

TEXT_EXT = {'.md', '.java', '.sql', '.ttl', '.bpmn', '.xml', '.py', '.html',
            '.yaml', '.yml', '.txt', '.properties', '.cmd'}
EXCLUDE_DIR_PARTS = {'target', 'logs', '__pycache__'}

SRC = '四书'.encode('utf-8')
DST = '六书'.encode('utf-8')


def excluded_dir(path):
    parts = os.path.normpath(path).replace('\\', '/').split('/')
    for p in parts:
        if p in EXCLUDE_DIR_PARTS:
            return True
        if p.endswith('_backup') or p == 'sishu2liushu_backup':
            return True
    return False


def excluded_file(name):
    low = name.lower()
    if low.endswith('.pyc') or low.endswith('.log'):
        return True
    if '.bak' in low or '.pre_' in low or low.endswith('.pre'):
        return True
    return False


def collect():
    hits = []
    for root in ROOTS:
        for dp, dns, fns in os.walk(root):
            dns[:] = [d for d in dns if not excluded_dir(os.path.join(dp, d))]
            if excluded_dir(dp):
                continue
            for fn in fns:
                if os.path.splitext(fn)[1].lower() not in TEXT_EXT:
                    continue
                if excluded_file(fn):
                    continue
                fp = os.path.join(dp, fn)
                try:
                    with open(fp, 'rb') as f:
                        data = f.read()
                except OSError:
                    continue
                if SRC in data:
                    hits.append((fp, data.count(SRC)))
    return hits


def main():
    hits = collect()
    total = sum(c for _, c in hits)
    print("命中文件 %d 个，出现次数合计 %d" % (len(hits), total))
    for fp, c in sorted(hits):
        rel = os.path.relpath(fp, ROOT)
        dst = os.path.join(BACKUP, rel)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        with open(fp, 'rb') as f:
            data = f.read()
        with open(dst, 'wb') as f:
            f.write(data)                      # 备份
        with open(fp, 'wb') as f:
            f.write(data.replace(SRC, DST))    # 替换
        print("  [%2d] %s" % (c, rel))
    print("备份目录：%s" % BACKUP)


if __name__ == '__main__':
    main()
