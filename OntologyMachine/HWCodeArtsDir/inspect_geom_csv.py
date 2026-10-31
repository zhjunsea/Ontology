import openpyxl

path = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkLatticeTower\design\HH108_iron_45_横撑横隔_几何数据_GK25m-9.8m(1).csv"
wb = openpyxl.load_workbook(path, data_only=False)
print("sheets:", wb.sheetnames)
for ws in wb.worksheets:
    print("=" * 80)
    print("SHEET:", ws.title, "dims:", ws.dimensions, "max_row:", ws.max_row, "max_col:", ws.max_column)
    print("merged:", list(ws.merged_cells.ranges)[:40])
    for r in range(1, min(ws.max_row, 30) + 1):
        vals = []
        for c in range(1, min(ws.max_column, 14) + 1):
            v = ws.cell(r, c).value
            vals.append("" if v is None else str(v))
        print(r, "|", " | ".join(vals))
    if ws.max_row > 30:
        print("... tail ...")
        for r in range(ws.max_row - 5, ws.max_row + 1):
            vals = []
            for c in range(1, min(ws.max_column, 14) + 1):
                v = ws.cell(r, c).value
                vals.append("" if v is None else str(v))
            print(r, "|", " | ".join(vals))
