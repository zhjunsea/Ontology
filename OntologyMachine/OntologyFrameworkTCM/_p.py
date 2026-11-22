import socket,subprocess,psutil,os
def up(p):
    s=socket.socket();s.settimeout(0.4)
    try:s.connect(("127.0.0.1",p));return True
    except Exception:return False
    finally:s.close()
for p,n in [(8080,"Ontop"),(26500,"Zeebe"),(9082,"Worker")]:
    print(p,n,"UP" if up(p) else "down")
r=subprocess.run(["netstat","-ano","-p","tcp"],capture_output=True,text=True,encoding='utf-8',errors='replace')
pid=None
for l in r.stdout.splitlines():
    if ":9082" in l and "LISTENING" in l: pid=l.split()[-1];break
print("worker pid:",pid)
if pid:
    p=psutil.Process(int(pid)); c=" ".join(p.cmdline())
    print("has jacoco:", "org.jacoco.agent" in c)
    print("cmdline:", c[:260])
d=r"D:\work\Ontology\OntologyMachine\OntologyFrameworkTCM\target"
for f in os.listdir(d):
    if f.endswith('.exec'): print(f, os.path.getsize(os.path.join(d,f)))
