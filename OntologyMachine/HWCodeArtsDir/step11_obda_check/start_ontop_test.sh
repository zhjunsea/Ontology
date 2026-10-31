#!/usr/bin/env bash
ONTOP_HOME="D:/work/Ontology/ontop-cli-5.5.0"
B="D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi"
OUT="D:/work/Ontology/OntologyMachine/HWCodeArtsDir/step11_obda_check"
PROPS="$1"
PORT="$2"
TAG="$3"
MAP="file:///$B/ontology/database/sizhubazi.obda"
CATALOG="$B/ontology/catalog-v001.xml"
ONTO="$B/ontology/sizhubazi-abox.owl"
echo "CMD: $PROPS port=$PORT" > "$OUT/ontop_$TAG.log"
"$ONTOP_HOME/ontop.bat" endpoint --properties "$PROPS" --xml-catalog "$CATALOG" --port "$PORT" -m "$MAP" -t "$ONTO" >> "$OUT/ontop_$TAG.log" 2>&1
