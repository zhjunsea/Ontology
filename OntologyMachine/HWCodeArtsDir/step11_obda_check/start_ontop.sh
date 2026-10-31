#!/usr/bin/env bash
ONTOP_HOME="D:/work/Ontology/ontop-cli-5.5.0"
BASE="D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi"
PROPS="file:///D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/ontology/database/sizhubazi.properties"
MAP="file:///D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/ontology/database/sizhubazi.obda"
CATALOG="$BASE/ontology/catalog-v001.xml"
ONTO="$BASE/ontology/sizhubazi-abox.owl"
OUT="D:/work/Ontology/OntologyMachine/HWCodeArtsDir/step11_obda_check"
echo "CMD: ontop.bat endpoint --properties $PROPS --xml-catalog $CATALOG --port 8080 -m $MAP -t $ONTO" > "$OUT/ontop_endpoint.log"
"$ONTOP_HOME/ontop.bat" endpoint --properties "$PROPS" --xml-catalog "$CATALOG" --port 8080 -m "$MAP" -t "$ONTO" >> "$OUT/ontop_endpoint.log" 2>&1
