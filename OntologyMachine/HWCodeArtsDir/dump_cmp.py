import sys, zipfile, openpyxl, os

def dump_xlsx(path, maxrows=40):
    print("="*80)
    print("FILE:", path, "exists=", os.path.exists(path))
    try:
        wb = openpyxl.load_workbook(path, data_only=True)
    except Exception as e:
        print("  openpyxl failed:", e)
        if zipfile.is_zipfile(path):
            z = zipfile.ZipFile(path)
            print("  zip members:", z.namelist()[:20])
        else:
            with open(path, 'rb') as f:
                print("  raw head:", f.read(400))
        return
    for ws in wb.worksheets:
        print("-"*70)
        print("SHEET:", ws.title, "dims=", ws.dimensions, ws.max_row, "x", ws.max_column)
        for i, row in enumerate(ws.iter_rows(values_only=True)):
            if i >= maxrows:
                print("   ... (%d rows total)" % ws.max_row)
                break
            print("  ", row)

for p in sys.argv[1:]:
    dump_xlsx(p)
