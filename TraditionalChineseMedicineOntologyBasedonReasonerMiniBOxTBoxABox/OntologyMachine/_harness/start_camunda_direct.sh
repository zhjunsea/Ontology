#!/usr/bin/env bash
# 直接启动 Camunda（StandaloneCamunda）并保持前台运行，避免 c8run 退出后子进程被回收。
# 工作目录固定为 c8run 根，保证 H2 相对路径 ./camunda-data/h2db 落在预期位置。
#
# 两个坑：
#  1) Java @argfile 中反斜杠是转义字符，Windows 路径必须写成正斜杠；
#     且 argfile 里的 classpath 通配符实测不生效 —— 本脚本 classpath 很短，
#     直接走命令行（bash 加引号阻止自身 glob，交给 Java 展开 lib/*）。
#  2) classpath 与 bin/camunda.bat 一致：config + lib/*（driver-lib 不存在则不加）。
export PATH="/usr/bin:/bin:/c/Windows/System32:/c/Windows:$PATH"
C8="/d/work/Ontology/c8run-8.9.6"
CZ="$C8/camunda-zeebe-8.9.6"
JAVA="/c/Program Files/Java/jdk-26.0.1/bin/java"

cd "$C8" || exit 1

CP="D:/work/Ontology/c8run-8.9.6/camunda-zeebe-8.9.6/config;D:/work/Ontology/c8run-8.9.6/camunda-zeebe-8.9.6/lib/*"

exec "$JAVA" \
  -XX:+ExitOnOutOfMemoryError \
  -Dfile.encoding=UTF-8 \
  -Xshare:auto \
  --add-opens=java.base/java.io=ALL-UNNAMED \
  -cp "$CP" \
  -Dapp.name=camunda \
  -Dapp.repo=D:/work/Ontology/c8run-8.9.6/camunda-zeebe-8.9.6/lib \
  -Dapp.home=D:/work/Ontology/c8run-8.9.6/camunda-zeebe-8.9.6 \
  -Dbasedir=D:/work/Ontology/c8run-8.9.6/camunda-zeebe-8.9.6 \
  io.camunda.application.StandaloneCamunda \
  --server.port=9080 \
  --spring.config.additional-location=file:D:/work/Ontology/c8run-8.9.6/configuration/
