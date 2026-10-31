import numpy as np

def midpoint_neworder(p1,p2,p3,p4):
    A,B,C,D = map(np.array,[p1,p2,p3,p4])   # Java: A=p1,B=p2,C1=p3,D=p4
    return _solve(A,B,C,D)

def midpoint_oldorder(p1,p2,p3,p4):
    A,B,C,D = map(np.array,[p1,p3,p2,p4])   # 客户脚本: [p1,p3,p2,p4]
    return _solve(A,B,C,D)

def _solve(A,B,C,D):
    AB=B-A; CD=D-C; AC=C-A
    cp=np.cross(AB,CD)
    if np.linalg.norm(cp)==0:
        print("   [degenerate -> mean]")
        return (A+B+C+D)/4
    t=np.dot(np.cross(AC,CD),cp)/np.dot(cp,cp)
    return A+t*AB

# new geo node values (mm)
new={42:(17253.713,354.318,713.850),58:(354.318,17253.713,713.850),
     60:(354.318,14974.055,25159.031),44:(14974.055,354.318,25159.031)}
# old geo node values (mm)
old={42:(17253.893,354.318,711.918),58:(354.318,17253.893,711.918),
     60:(354.318,14974.221,25157.368),44:(14974.221,354.318,25157.368)}

print("NEW nodes, Java(order p1,p2,p3,p4):", midpoint_neworder(new[42],new[58],new[60],new[44]))
print("NEW nodes, script(order p1,p3,p2,p4):", midpoint_oldorder(new[42],new[58],new[60],new[44]))
print("OLD nodes, script(order p1,p3,p2,p4):", midpoint_oldorder(old[42],old[58],old[60],old[44]))
print("file values: new node105 z=12936.440 ; old node105 z=13818.662 ; new md crossing[0]=13820.196")
