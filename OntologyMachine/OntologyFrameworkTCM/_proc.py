import psutil,os,time
print("=== TCM worker-ish java procs ===")
for p in psutil.process_iter(['pid','cmdline','memory_info']):
    try:
        c=" ".join(p.info['cmdline'] or [])
        if 'TCMApplication' in c or 'OntologyFrameworkTCM-0.0.1-SNAPSHOT.jar' in c:
            print(p.info['pid'], "jacoco" if 'jacoco.agent' in c else "NO-jacoco", p.info['memory_info'].rss//1048576,"MB",c[:120])
    except Exception: pass
d=r"D:\work\Ontology\OntologyMachine\OntologyFrameworkTCM\target"
for f in ["jacoco.exec","jacoco-worker.exec"]:
    pth=os.path.join(d,f)
    if os.path.exists(pth): print(f, os.path.getsize(pth), time.ctime(os.path.getmtime(pth)))
csv=os.path.join(d,"site","jacoco","jacoco.csv")
print("csv mtime:", time.ctime(os.path.getmtime(csv)))
