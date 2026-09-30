import sys, os, difflib

old_root, tmsd_root = sys.argv[1], sys.argv[2]

def read(p):
    with open(p, 'r', encoding='gbk', errors='replace') as f:
        return f.read().splitlines()

def walk(root):
    out = set()
    for dp, dn, fn in os.walk(root):
        for f in fn:
            if f.endswith('.txt'):
                out.add(os.path.relpath(os.path.join(dp, f), root).replace('\\', '/'))
    return out

old_files = walk(old_root)
tmsd_files = walk(tmsd_root)

only_old = sorted(old_files - tmsd_files)
only_tmsd = sorted(tmsd_files - old_files)
both = sorted(old_files & tmsd_files)

print("OLD_FILES=%d TMSD_FILES=%d ONLY_OLD=%d ONLY_TMSD=%d" % (len(old_files), len(tmsd_files), len(only_old), len(only_tmsd)))
for f in only_old:
    print("ONLY_OLD:", f)
for f in only_tmsd:
    print("ONLY_TMSD:", f)

for rel in both:
    a = read(os.path.join(old_root, rel))
    b = read(os.path.join(tmsd_root, rel))
    if a == b:
        continue
    print("=" * 70)
    print("DIFF_FILE:", rel)
    d = list(difflib.unified_diff(a, b, lineterm=''))
    for line in d[2:]:
        print(line)
