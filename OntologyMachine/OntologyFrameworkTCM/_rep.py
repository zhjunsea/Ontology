import os,shutil
d=r"D:\work\Ontology\OntologyMachine\OntologyFrameworkTCM\target"
w=os.path.join(d,"jacoco-worker.exec")
print("worker exec:",os.path.getsize(w))
shutil.copyfile(w,os.path.join(d,"jacoco.exec"))
print("copied")
