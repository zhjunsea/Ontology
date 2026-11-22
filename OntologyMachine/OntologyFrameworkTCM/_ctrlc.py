import ctypes,sys,time,os
pid=int(sys.argv[1])
k=ctypes.windll.kernel32
k.FreeConsole()
if not k.AttachConsole(pid):
    print("AttachConsole failed err",k.GetLastError()); sys.exit(0)
k.SetConsoleCtrlHandler(None,True)
r=k.GenerateConsoleCtrlEvent(0,0)
print("ctrl_c ok=",r,"err=",k.GetLastError())
time.sleep(2)
k.FreeConsole()
d=r"D:\work\Ontology\OntologyMachine\OntologyFrameworkTCM\target"
for f in os.listdir(d):
    if f.endswith('.exec'): print(f,os.path.getsize(os.path.join(d,f)))
