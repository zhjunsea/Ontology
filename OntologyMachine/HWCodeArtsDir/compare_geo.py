import openpyxl, sys

def load(path):
    wb = openpyxl.load_workbook(path, data_only=True)
    ws = wb.worksheets[0]
    nodes = {}
    elems = []
    for r in ws.iter_rows(min_row=2, values_only=True):
        nid = r[0]
        if nid is None:
            continue
        x, y, z = r[1], r[2], r[3]
        if x is not None:
            nodes[int(nid)] = (float(x), float(y), float(z))
        eid = r[6]
        if eid is not None:
            elems.append((int(eid), r[7], r[8], r[9], r[10]))
    return nodes, elems

old_nodes, old_elems = load(sys.argv[1])
new_nodes, new_elems = load(sys.argv[2])
print("old nodes=%d elems=%d | new nodes=%d elems=%d" % (len(old_nodes), len(old_elems), len(new_nodes), len(new_elems)))

ids = sorted(set(old_nodes) | set(new_nodes))
print("\n--- NODE COMPARISON (mm) ---")
big = []
for i in ids:
    o = old_nodes.get(i); n = new_nodes.get(i)
    if o is None or n is None:
        print(i, "MISSING", o, n); continue
    ox, oy, oz = o[0]*1000, o[1]*1000, o[2]*1000
    nx, ny, nz = n[0]*1000, n[1]*1000, n[2]*1000
    dx, dy, dz = ox-nx, oy-ny, oz-nz
    flag = "" if max(abs(dx),abs(dy),abs(dz)) <= 2.0 else "  <<<"
    print("%4d | %11.3f %11.3f %11.3f | %11.3f %11.3f %11.3f | %8.3f %8.3f %8.3f%s" % (i, ox,oy,oz, nx,ny,nz, dx,dy,dz, flag))
    if abs(dz) > 5: big.append(i)
print("\nBIG-diff node ids:", big)

print("\n--- ELEMENT COMPARISON ---")
for k in range(max(len(old_elems), len(new_elems))):
    o = old_elems[k] if k < len(old_elems) else None
    n = new_elems[k] if k < len(new_elems) else None
    if o != n:
        print("elem[%d] old=%s new=%s" % (k, o, n))
