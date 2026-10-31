#!/usr/bin/env bash
ONTOP_HOME="D:/work/Ontology/ontop-cli-5.5.0"
B="D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi"
OUT="D:/work/Ontology/OntologyMachine/HWCodeArtsDir/step11_obda_check"
MAP="file:///$B/ontology/database/sizhubazi.obda"
CATALOG="$B/ontology/catalog-v001.xml"
ONTO="$B/ontology/sizhubazi-abox.owl"
DBURL='jdbc:mysql://localhost:3306/sizhubazidb'
echo "CMD: endpoint --db-url ... port=8088" > "$OUT/ontop_dburl.log"
"$ONTOP_HOME/ontop.bat" endpoint --db-url "$DBURL" --db-user root --db-password "" --db-driver com.mysql.cj.jdbc.Driver --xml-catalog "$CATALOG" --port 8088 -m "$MAP" -t "$ONTO" >> "$OUT/ontop_dburl.log" 2>&1
