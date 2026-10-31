import openpyxl, sys, os

path = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkLatticeTower\design\02_生成几何数据用_V16_185m格构塔钢塔部分.xlsx"
print("exists:", os.path.exists(path))
wb = openpyxl.load_workbook(path, data_only=False)
print("sheets:", wb.sheetnames)
for ws in wb.worksheets:
    print("=" * 70)
    print("SHEET:", ws.title, "dims:", ws.dimensions, "max_row:", ws.max_row, "max_col:", ws.max_column)
    print("merged:", list(ws.merged_cells.ranges)[:40])
    lim = min(ws.max_row, 25)
    for r in range(1, lim + 1):
        vals = []
        for c in range(1, min(ws.max_column, 16) + 1):
            v = ws.cell(r, c).value
            vals.append("" if v is None else str(v))
        print(r, "|", " | ".join(vals))
