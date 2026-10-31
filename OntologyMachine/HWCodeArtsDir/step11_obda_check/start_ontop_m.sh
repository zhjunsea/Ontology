#!/usr/bin/env bash
ONTOP_HOME="D:/work/Ontology/ontop-cli-5.5.0"
B="D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi"
OUT="D:/work/Ontology/OntologyMachine/HWCodeArtsDir/step11_obda_check"
CATALOG="$B/ontology/catalog-v001.xml"
ONTO="$B/ontology/sizhubazi-abox.owl"
DBURL='jdbc:mysql://localhost:3306/sizhubazidb'
MPATH="$1"      # value passed to -m
PORT="$2"
TAG="$3"
echo "CMD: endpoint -m '$MPATH' port=$PORT" > "$OUT/ontop_$TAG.log"
"$ONTOP_HOME/ontop.bat" endpoint --db-url "$DBURL" --db-user root --db-password "" --db-driver com.mysql.cj.jdbc.Driver --xml-catalog "$CATALOG" --port "$PORT" -m "$MPATH" -t "$ONTO" >> "$OUT/ontop_$TAG.log" 2>&1
