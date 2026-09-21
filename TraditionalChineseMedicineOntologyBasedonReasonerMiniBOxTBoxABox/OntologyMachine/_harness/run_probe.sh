#!/usr/bin/env bash
# 编译并运行 PatientProbe（患者模拟探针）—— 用 @argfile 规避命令行过长
export PATH="/usr/bin:/bin:/c/Windows/System32:/c/Windows:$PATH"
ROOT="/d/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/OntologyMachine"
JAVA="/c/Program Files/Java/jdk-26.0.1/bin/java"
JAVAC="/c/Program Files/Java/jdk-26.0.1/bin/javac"
MOD="$ROOT/OntologyFramework"
H="$ROOT/_harness"

DEPS=$(cat "$MOD/target/test-classpath.txt" | tr -d '\r\n')
CP="$MOD/target/classes;$ROOT/OntopOBDAHandler/target/classes;$ROOT/OpenlletResolver/target/classes;$DEPS"
CP=$(echo "$CP" | sed -E 's#(^|;)/([a-zA-Z])/#\1\U\2:/#g')

cd "$H"
mkdir -p _probe_classes

# javac argfile
{
  echo '-encoding'
  echo 'UTF-8'
  echo '-d'
  echo '"_probe_classes"'
  echo '-cp'
  echo "\"${CP//\\/\\\\}\""
  echo 'PatientProbe.java'
} > _probe_classes/javac.args
"$JAVAC" "@_probe_classes/javac.args" 2>&1 | head -30

TBOX="D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology/tcm-all.owl"
ABOX="D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology/tcm-all-abox.owl"
{
  echo '-Dfile.encoding=UTF-8'
  echo '-cp'
  echo "\"_probe_classes;${CP//\\/\\\\}\""
  echo 'PatientProbe'
  echo "\"$TBOX\""
  echo "\"$ABOX\""
  echo "\"_cases.txt\""
} > _probe_classes/java.args
"$JAVA" "@_probe_classes/java.args"
