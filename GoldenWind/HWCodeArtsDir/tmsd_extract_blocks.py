import re, sys, io

src = io.open(r"D:/work/Ontology/GoldenWind/OntologyMachine/OntologyFrameworkTMSD/src/main/java/com/ocean/ontologyframework/tmsd/TmsdOutputWriter.java", encoding="utf-8").read()

def extract_const(name):
    # find  private static final String NAME =  ... ;
    m = re.search(r'String\s+' + name + r'\s*=\s*', src)
    if not m:
        return None
    i = m.end()
    # collect string literals until ';'
    out = []
    while i < len(src):
        ch = src[i]
        if ch == '"':
            j = i + 1
            buf = []
            while j < len(src):
                c = src[j]
                if c == '\\':
                    buf.append(src[j:j+2]); j += 2; continue
                if c == '"':
                    break
                buf.append(c); j += 1
            out.append(''.join(buf)); i = j + 1; continue
        if ch == ';':
            break
        i += 1
    s = ''.join(out)
    # java unescape
    s = s.replace('\\n', '\n').replace('\\t', '\t').replace('\\"', '"').replace('\\\\', '\\')
    return s

for n in ["WEIGHT_INFO", "SKEL_NAME", "VFLANGE_INFO", "VFLANGE_TAIL", "SKEL_COMP"]:
    v = extract_const(n)
    print("==== %s (len=%d) ====" % (n, len(v)))
    sys.stdout.write(v)
    print("==== END ====")
    print()
