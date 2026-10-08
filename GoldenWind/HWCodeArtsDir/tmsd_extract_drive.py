import io
p = r"D:/work/Ontology/GoldenWind/HWCodeArtsDir/tmsd-baseline-before/用例0/项目法兰尺寸_骨架关系式/第1段/第1段筒体信息关系式.txt"
s = io.open(p, encoding="gbk").read()
marker = "\r\n/***************筒体驱动尺寸"
i = s.index("/***************筒体驱动尺寸")
# include the single CRLF that precedes it (that CRLF is part of the block's leading \n)
j = i - 2
block = s[j:]
# normalize CRLF->LF for storage
block_lf = block.replace("\r\n", "\n")
print("len=%d" % len(block_lf))
print(repr(block_lf[:120]))
io.open(r"D:/work/Ontology/GoldenWind/HWCodeArtsDir/drive_size.txt", "w", encoding="utf-8", newline="").write(block_lf)
print("saved")
