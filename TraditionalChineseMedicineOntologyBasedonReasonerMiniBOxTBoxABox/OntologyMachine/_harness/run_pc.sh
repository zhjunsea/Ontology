#!/usr/bin/env bash
# 运行 PrecomputeProbe（分类耗时探针）
export PATH="/usr/bin:/bin:/c/Windows/System32:/c/Windows:$PATH"
ROOT="/d/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/OntologyMachine"
JAVA="/c/Program Files/Java/jdk-26.0.1/bin/java"
MOD="$ROOT/OntologyFramework"
H="$ROOT/_harness"

DEPS=$(cat "$MOD/target/test-classpath.txt" | tr -d '\r\n')
CP="$MOD/target/classes;$ROOT/OntopOBDAHandler/target/classes;$ROOT/OpenlletResolver/target/classes;$DEPS"
CP=$(echo "$CP" | sed -E 's#(^|;)/([a-zA-Z])/#\1\U\2:/#g')

TBOX="D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology/tcm-all.owl"
{
  echo '-Dfile.encoding=UTF-8'
  echo '-cp'
  echo "\"_satout;${CP//\\/\\\\}\""
  echo 'PrecomputeProbe'
  echo "\"$TBOX\""
} > "$H/_satout/pc.args"

cd "$H"
"$JAVA" "@D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/OntologyMachine/_harness/_satout/pc.args"
