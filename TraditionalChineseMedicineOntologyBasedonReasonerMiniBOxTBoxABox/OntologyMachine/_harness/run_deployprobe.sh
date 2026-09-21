#!/usr/bin/env bash
# 编译并运行 DeployProbe2（用 @argfile 规避 Windows 命令行长度限制）
export PATH="/usr/bin:/bin:/c/Windows/System32:/c/Windows:$PATH"
ROOT="/d/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/OntologyMachine"
WIN="D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/OntologyMachine"
JAVAC="/c/Program Files/Java/jdk-26.0.1/bin/javac"
JAVA="/c/Program Files/Java/jdk-26.0.1/bin/java"
H="$ROOT/_harness"
DEPS=$(cat "$ROOT/OntologyFramework/target/test-classpath.txt" | tr -d '\r\n')
CP="$WIN/OntologyFramework/target/classes;$WIN/OntopOBDAHandler/target/classes;$WIN/OpenlletResolver/target/classes;$DEPS"

{
  echo '-encoding'; echo 'UTF-8'
  echo '-nowarn'
  echo '-d'; echo "\"$WIN/_harness/_satout\""
  echo '-cp'; echo "\"${CP//\\/\\\\}\""
  echo "\"$WIN/_harness/DeployProbe2.java\""
} > "$H/_satout/dp_javac.args"

{
  echo '-Dfile.encoding=UTF-8'
  echo '-cp'
  echo "\"$WIN/_harness/_satout;${CP//\\/\\\\}\""
  echo 'DeployProbe2'
} > "$H/_satout/dp_run.args"

cd "$H"
echo "== javac =="
"$JAVAC" "@$WIN/_harness/_satout/dp_javac.args" 2>&1 | head -20
echo "== run =="
"$JAVA" "@$WIN/_harness/_satout/dp_run.args" 2>&1 | head -40
