#!/usr/bin/env bash
# 编译 OntologyFramework 测试源码到 target/test-classes
# （build.sh 只编主源码，测试需单独编译；口径与 run_suite.sh 一致）
export PATH="/usr/bin:/bin:/c/Windows/System32:/c/Windows:$PATH"
ROOT="/d/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/OntologyMachine"
WIN="D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/OntologyMachine"
JAVAC="/c/Program Files/Java/jdk-26.0.1/bin/javac"
MOD="$ROOT/OntologyFramework"
H="$ROOT/_harness"

DEPS=$(cat "$MOD/target/test-classpath.txt" | tr -d '\r\n')
CP="$WIN/OntologyFramework/target/classes;$WIN/OntopOBDAHandler/target/classes;$WIN/OpenlletResolver/target/classes;$DEPS"

mkdir -p "$H/_build"
find "$ROOT/OntologyFramework/src/test/java" -name '*.java' \
  | sed -E 's#^/d/#D:/#' > "$H/_build/t_src.txt"
{
  echo '-encoding'; echo 'UTF-8'
  echo '-nowarn'
  echo '-d'; echo "\"$WIN/OntologyFramework/target/test-classes\""
  echo '-cp'; echo "\"${CP//\\/\\\\}\""
  cat "$H/_build/t_src.txt"
} > "$H/_build/t.args"

echo "== javac tests ($(wc -l < "$H/_build/t_src.txt") files) =="
"$JAVAC" "@$WIN/_harness/_build/t.args" 2>&1 | head -60
echo "TEST_EXIT=${PIPESTATUS[0]}"
