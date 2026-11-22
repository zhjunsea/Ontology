import sys, fitz, numpy as np, cv2
from rapidocr_onnxruntime import RapidOCR

pdf = sys.argv[1]
a = int(sys.argv[2]); b = int(sys.argv[3]); out = sys.argv[4]
dpi = int(sys.argv[5]) if len(sys.argv) > 5 else 150
engine = RapidOCR()
doc = fitz.open(pdf)
with open(out, "w", encoding="utf-8") as f:
    for i in range(a - 1, b):
        pix = doc[i].get_pixmap(dpi=dpi)
        img = np.frombuffer(pix.samples, dtype=np.uint8).reshape(pix.height, pix.width, pix.n)
        if pix.n == 4:
            img = cv2.cvtColor(img, cv2.COLOR_RGBA2BGR)
        else:
            img = cv2.cvtColor(img, cv2.COLOR_RGB2BGR)
        res, _ = engine(img)
        f.write("\n===== PAGE %d =====\n" % (i + 1))
        if res:
            for line in res:
                f.write(line[1] + "\n")
print("done pages %d-%d" % (a, b))
