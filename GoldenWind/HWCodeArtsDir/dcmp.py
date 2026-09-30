import sys, difflib

def read(p):
    with open(p, 'r', encoding='gbk', errors='replace') as f:
        return f.read().splitlines()

a = read(sys.argv[1])
b = read(sys.argv[2])
print("OLD lines:", len(a), " TMSD lines:", len(b))
d = list(difflib.unified_diff(a, b, fromfile='OLD', tofile='TMSD', lineterm=''))
for line in d[:120]:
    print(line)
print("--- total diff lines:", len(d))
