import openpyxl, numpy as np, math

wb = openpyxl.load_workbook('D:/work/Ontology/OntologyMachine/HWCodeArtsDir/_old_hh108.xlsx', data_only=True)
ws = wb.worksheets[0]
N = {}
for r in ws.iter_rows(min_row=2, values_only=True):
    if r[0] is not None and r[1] is not None:
        N[int(r[0])] = np.array([float(r[1])*1000, float(r[2])*1000, float(r[3])*1000])  # mm

# 塔柱 a 中心线: 由 node1(底) 与 node10(顶) 拟合 x = a0 + a1*z, y=0
n1, n10 = N[1], N[10]
a1 = (n10[0]-n1[0])/(n10[2]-n1[2]); a0 = n1[0]-a1*n1[2]
print("col_a line: x = %.4f + %.8f z   (y=0)"%(a0,a1))

def closest_on_line(p):
    t = (a1*(p[0]-a0)+p[2])/(1+a1*a1)
    return np.array([a0+a1*t, 0.0, t])

def line_line_closest(p0,d0,p1,d1):
    # 最近点对
    n=np.cross(d0,d1); 
    if np.linalg.norm(n)<1e-12:
        t=np.dot(p1-p0,d0)/np.dot(d0,d0); q=p0+t*d0; return q,q
    # solve
    A=np.array([[np.dot(d0,d0),-np.dot(d0,d1)],[np.dot(d0,d1),-np.dot(d1,d1)]])
    b=np.array([np.dot(p1-p0,d0),np.dot(p1-p0,d1)])
    s,t=np.linalg.solve(A,b)
    return p0+s*d0, p1+t*d1

dev1_x = N[42]; dev1_y = N[41]
print("dev_1 (dev_x1)=%s  z=%.4f"%(np.round(N[42],3), N[42][2]))
print("dev_1 (dev_y1)=%s  z=%.4f"%(np.round(N[41],3), N[41][2]))

# 候选首杆: (start, end)  —— quad I 的两条对角斜杆
cands = [("brace a1->b2 (42->60)", 42, 60), ("brace b1->a2 (58->44)", 58, 44)]
dev = 500.0
for name, si, ei in cands:
    s=N[si]; e=N[ei]; d=e-s
    # 求反向延长线与 col_a 线的最近点
    pb, pc = line_line_closest(s, d, np.array([a0,0,0.0]), np.array([a1,0,1.0]))
    dist = np.linalg.norm(pb-pc)
    lam = None
    print("\n== %s =="%name)
    print("  start=%s z=%.4f  end=%s z=%.4f"%(np.round(s,3),s[2],np.round(e,3),e[2]))
    print("  brace ∩ col_a(最近点): %s  (偏离中心线 %.4f mm)"%(np.round(pc,3),dist))
    # 若以最近点 pc 为 P120
    z1 = pc[2]; Delta = s[2]-z1
    print("  P120(z=%.4f) -> z1=%.4f ; Δ=dev_1.z-P120.z=%.4f"%(pc[2],z1,Delta))
    # 120 垂距: P120 到 {face ∩ 底面} 交线 (过 base a-b, x+y=a0, z=0)
    # 交线方向 u=(-1,1,0)/√2, 过点 (a0,0,0)
    p=pc; u=np.array([-1,1,0.0])/math.sqrt(2); q0=np.array([a0,0,0.0])
    v=p-q0; perp=v-np.dot(v,u)*u
    print("  |P120 到 x+y=a0,z=0 交线| = %.4f mm"%np.linalg.norm(perp))
    if lam is None:
        lam=np.dot(pc-s,d)/np.dot(d,d)
    print("  (λ=%.6f 反向延长)"%lam)

print("\n--- 程序当前 ---")
b=(9800.0-25000.0)/(2*115253.0)
t=math.tan(math.radians(47.7))
z1_p=120.0/math.sqrt(1+b*b); eff=t/math.sqrt(1-b*b*t*t); Dp=dev*eff/(1+b*eff)
print("  z1=%.4f  Δ=%.4f  dev_1=%.4f"%(z1_p,Dp,z1_p+Dp))
print("  dev·t/(1+b·t)=%.4f"% (dev*t/(1+b*t)))
print("  客户 dev_1(实跑)=711.9177 ; CAD H_0=744.817")
