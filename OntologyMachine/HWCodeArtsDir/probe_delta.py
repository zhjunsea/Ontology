import math

bw=25000.0; top=9800.0; H=115253.0; dev=500.0; beta=math.radians(47.7)
dis_bott=bw; dis_top=9604.88; high_truss=116733.0
target=592.1777   # = dev_1(711.9177) - z1(119.740)

def report(name,val):
    print("%-46s = %10.4f  (Δ vs target %+.4f)"%(name,val,val-target))

# slopes
b_center=(top-bw)/(2*H)                 # 中心线收分斜率(bw@0 -> 9800@H)
b_hi=(dis_top-bw)/(2*high_truss)        # 用客户 dis_top/high_truss
t=math.tan(beta)
print("b_center=%.8f  b_hi=%.8f  t=%.8f"%(b_center,b_hi,t))
print("z1=120/√(1+b_center^2)=%.4f"% (120/math.sqrt(1+b_center**2)))
print("target Δ = %.4f"%target)
print("-"*70)

for nb,b in [("b_center",b_center),("b_hi",b_hi)]:
    disc=1-b*b*t*t
    eff=t/math.sqrt(disc) if disc>0 else float('nan')
    report(f"[{nb}] dev*t        ", dev*t)
    report(f"[{nb}] dev*t/(1+b t) ", dev*t/(1+b*t))
    report(f"[{nb}] dev*eff/(1+b eff)", dev*eff/(1+b*eff))
    report(f"[{nb}] dev*eff/(1-b eff)", dev*eff/(1-b*eff))
    report(f"[{nb}] dev*eff", dev*eff)
    # sin-based
    report(f"[{nb}] dev*sinβ/(cosβ+b sinβ)", dev*math.sin(beta)/(math.cos(beta)+b*math.sin(beta)))
    report(f"[{nb}] dev*sinβ/(cosβ-b sinβ)", dev*math.sin(beta)/(math.cos(beta)-b*math.sin(beta)))
    # with sqrt(1+b^2)
    s=math.sqrt(1+b*b)
    report(f"[{nb}] dev*t/(1+b t)*s", dev*t/(1+b*t)*s)
    report(f"[{nb}] dev*t/(1+b t)/s", dev*t/(1+b*t)/s)
    report(f"[{nb}] dev*eff/(1+b eff)*s", dev*eff/(1+b*eff)*s)
    report(f"[{nb}] dev*eff/(1+b eff)/s", dev*eff/(1+b*eff)/s)
    print("-"*70)
