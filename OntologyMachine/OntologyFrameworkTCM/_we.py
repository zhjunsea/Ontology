import os,time
BASE=r"D:\work\Ontology\OntologyMachine\OntologyFrameworkTCM"
done=os.path.join(BASE,"_e2e.done")
for i in range(26):
    if os.path.exists(done):
        print("DONE"); break
    time.sleep(1)
else: print("RUNNING")
