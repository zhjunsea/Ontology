import sys, os
sys.path.insert(0, os.getcwd())
from towerdesign.drawing.drawingmain import generate_creo_parameters

geo = sys.argv[1]
lay = sys.argv[2]
zip_path = generate_creo_parameters(geo, lay, None)
print("ZIP_PATH=" + str(zip_path))
