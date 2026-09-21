#!/usr/bin/env bash
# 编译并运行 AliasProbe —— 对比同义词来源改造前后的口语映射效果
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

{
  echo '-encoding'
  echo 'UTF-8'
  echo '-nowarn'
  echo '-d'
  echo '"_probe_classes"'
  echo '-cp'
  echo "\"${CP//\\/\\\\}\""
  echo 'AliasProbe.java'
} > _probe_classes/alias_javac.args
echo "== javac AliasProbe =="
"$JAVAC" "@_probe_classes/alias_javac.args" 2>&1 | head -40

ABOX="D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology"
{
  echo '-Dfile.encoding=UTF-8'
  echo '-Dstdout.encoding=UTF-8'
  echo '-Dstderr.encoding=UTF-8'
  echo '-cp'
  echo "\"_probe_classes;${CP//\\/\\\\}\""
  echo 'AliasProbe'
  echo "\"$ABOX\""
} > _probe_classes/alias_java.args
echo "== java AliasProbe =="
"$JAVA" "@_probe_classes/alias_java.args" 2>&1 | tee "${1:-_alias_probe.log}" | grep -av "INFO com.ocean"
