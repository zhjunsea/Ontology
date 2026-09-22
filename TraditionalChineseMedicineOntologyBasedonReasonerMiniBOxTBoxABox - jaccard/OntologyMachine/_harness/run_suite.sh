#!/usr/bin/env bash
# 运行经方方证 JUnit 套件（绕开损坏的 mvn shim）
export PATH="/usr/bin:/bin:/c/Windows/System32:/c/Windows:$PATH"
ROOT="/d/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/OntologyMachine"
JAVA="/c/Program Files/Java/jdk-26.0.1/bin/java"
MOD="$ROOT/OntologyFramework"

DEPS=$(cat "$MOD/target/test-classpath.txt" | tr -d '\r\n')
CP="$MOD/target/test-classes;$MOD/target/classes;$ROOT/OntopOBDAHandler/target/classes;$ROOT/OpenlletResolver/target/classes;$DEPS"
# MSYS 路径 /d/... → Windows 路径 D:/...
CP=$(echo "$CP" | sed -E 's#(^|;)/d/#\1D:/#g')

ARGF="$MOD/target/fangzheng-suite.args"
mkdir -p "$MOD/target"
{
  echo '-Dfile.encoding=UTF-8'
  echo '-Dspring.output.ansi.enabled=never'
  echo '-cp'
  echo "\"${CP//\\/\\\\}\""
  echo 'com.ocean.ontologyframework.tcm.FangzhengSuiteRunner'
  for a in "$@"; do echo "$a"; done
} > "$ARGF"

cd "$MOD"
ARGF_WIN="D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/OntologyMachine/OntologyFramework/target/fangzheng-suite.args"
"$JAVA" "@$ARGF_WIN"
echo "SUITE_EXIT=$?"
