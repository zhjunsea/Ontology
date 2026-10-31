#!/usr/bin/env bash
ONTOP_HOME="D:/work/Ontology/ontop-cli-5.5.0"
D="D:/work/Ontology/OntologyMachine/HWCodeArtsDir/step11_obda_check"
echo "CMD: endpoint mini port=8090" > "$D/ontop_mini.log"
"$ONTOP_HOME/ontop.bat" endpoint --properties "file:///$D/mini.properties" --port 8090 -m "file:///$D/mini.obda" -t "$D/mini.ttl" >> "$D/ontop_mini.log" 2>&1
