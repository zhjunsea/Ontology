#!/usr/bin/env bash
ONTOP_HOME="D:/work/Ontology/ontop-cli-5.5.0"
B="D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi"
D="D:/work/Ontology/OntologyMachine/HWCodeArtsDir/step11_obda_check"
echo "CMD: endpoint -m real.obda -t mini.ttl port=8091" > "$D/ontop_x1.log"
"$ONTOP_HOME/ontop.bat" endpoint --properties "file:///$D/mini.properties" --port 8091 -m "file:///$B/ontology/database/sizhubazi.obda" -t "$D/mini.ttl" >> "$D/ontop_x1.log" 2>&1
