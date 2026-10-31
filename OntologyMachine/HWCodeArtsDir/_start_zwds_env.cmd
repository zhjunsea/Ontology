@echo off
chcp 936 >nul
cd /d "D:\work\Ontology\OntologyMachine\OntologyFrameworkZiWeiDouShu"
java -jar EnvPrepare-0.0.1-SNAPSHOT.jar start --yes > "D:\work\Ontology\OntologyMachine\HWCodeArtsDir\_step14_bg.log" 2>&1
