#!/usr/bin/env bash
# 启动 TMSDApplication（塔架中段设计 JobWorker）。临时脚本，位于 HWCodeArtsDir。
# classpath 过长（373 条依赖）超过命令行上限，改用 Java @argfile（JDK 9+）。
set -uo pipefail
APP_DIR="D:/work/Ontology/GoldenWind/OntologyMachine/OntologyFramework"
LOG="D:/work/Ontology/GoldenWind/HWCodeArtsDir/logs/tmsd-app.log"
ARGFILE="D:/work/Ontology/GoldenWind/HWCodeArtsDir/scripts/tmsd-app.args"
cd "$APP_DIR" || exit 1
DEPS="D:/work/Ontology/GoldenWind/HWCodeArtsDir/scripts/tmsd-deps.cp"
CP="target/classes;../OpenlletResolver/target/classes;../OntopOBDAHandler/target/classes;$(tr '\\' '/' < "$DEPS")"
{
  echo '--enable-native-access=ALL-UNNAMED'
  echo '--sun-misc-unsafe-memory-access=allow'
  echo '-cp'
  printf '%s\n' "$CP"
} > "$ARGFILE"
: > "$LOG"
nohup java "@$ARGFILE" com.ocean.ontologyframework.TMSDApplication > "$LOG" 2>&1 &
echo "started pid=$! log=$LOG"
