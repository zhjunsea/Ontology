import openpyxl, sys

p = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkLatticeTower\design\HH108_iron_45_横撑横隔_几何数据_GK25m-9.8m(1).csv"
wb = openpyxl.load_workbook(p, data_only=True)
ws = wb.active
print("sheet=", ws.title, "max_row=", ws.max_row, "max_col=", ws.max_column)

def show(r):
    vals = [ws.cell(r, c).value for c in range(1, ws.max_column + 1)]
    print(r, vals)

print("--- header row 1 ---")
show(1)
print("--- first data row 2 ---")
show(2)
print("--- rows 300..end ---")
for r in range(300, ws.max_row + 1):
    show(r)
