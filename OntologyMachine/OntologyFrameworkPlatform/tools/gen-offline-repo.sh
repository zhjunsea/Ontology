#!/usr/bin/env bash
#
# 生成「离线 Maven 仓库快照」，供 OntologyInstaller 随安装包分发：
# 目标机无网络时，释放出的 OntologyFrameworkExample 工程仍可用该快照构建并运行测试。
#
# 原理：以本机已缓存的 ~/.m2/repository 作为 file:// 镜像（mirror id=central，使离线构建
# 认可其 _remote.repositories 标记与 maven-metadata），把一个独立目录当作 localRepository，
# 让它从本机缓存“下载”出工程构建/测试所需的全部 jar+pom（含 test 依赖与构建插件），
# 从而得到一个自包含、可离线使用的仓库快照，而不必联网重新下载。
#
# 用法：
#   bash gen-offline-repo.sh [输出目录]
# 默认输出：<OntologyMachine>/build/maven-repo
#
# 构建顺序（本仓库）：
#   mvn -pl EnvPrepare install -DskipTests
#   mvn -pl OntologyFrameworkPlatform package -DskipTests   # 会清空 build/
#   bash OntologyFrameworkPlatform/tools/gen-offline-repo.sh # 生成 build/maven-repo
#   mvn -pl OntologyInstaller package -DskipTests            # 收集进 build/installer/maven-repo.zip
#
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/../.." && pwd)"
out="${1:-$root/build/maven-repo}"
m2="${M2_REPO:-$HOME/.m2/repository}"

[ -d "$m2" ] || { echo "[offline-repo] 未找到本机 Maven 仓库: $m2" >&2; exit 1; }

echo "[offline-repo] 本机缓存: $m2"
echo "[offline-repo] 输出目录: $out"
rm -rf "$out"
mkdir -p "$out"

# 1) 临时把本机缓存中的 maven-metadata-central.xml 别名为 maven-metadata.xml，
#    使 ~/.m2 作为 file:// 镜像时也能提供「版本区间(range)」依赖的候选版本列表。
aliases="$(mktemp)"
find "$m2" -name 'maven-metadata-central.xml' -print0 2>/dev/null | while IFS= read -r -d '' f; do
    d="$(dirname "$f")"
    a="$d/maven-metadata.xml"
    if [ ! -e "$a" ]; then
        cp "$f" "$a"
        printf '%s\n' "$a" >> "$aliases"
    fi
done

# 2) 生成临时 settings.xml：以 ~/.m2 为镜像（id 固定为 central）。
settings="$(mktemp --suffix=.xml)"
m2url="file:///$(cygpath -m "$m2" 2>/dev/null || printf '%s' "$m2" | tr -d '\r')"
cat > "$settings" <<EOF
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
  <mirrors>
    <mirror>
      <id>central</id>
      <name>local ~/.m2 as a file mirror</name>
      <url>${m2url}</url>
      <mirrorOf>*</mirrorOf>
    </mirror>
  </mirrors>
</settings>
EOF

cleanup() {
    if [ -s "$aliases" ]; then
        while IFS= read -r a; do rm -f "$a"; done < "$aliases"
    fi
    rm -f "$aliases" "$settings"
}
trap cleanup EXIT

repo="$(cygpath -m "$out" 2>/dev/null || printf '%s' "$out" | tr -d '\r')"

cd "$root"
# 3) 构建基础模块，并把工程 compile+runtime+test 全部依赖与构建插件装入快照仓库。
#    （用 clean install 以覆盖 clean/resources/compiler/surefire/jar/spring-boot/source/install 等插件）
echo "[offline-repo] 步骤 1/2：收集依赖与构建插件 ..."
mvn -s "$settings" -Dmaven.repo.local="$repo" \
    -pl OntologyFrameworkExample -am clean install -DskipTests

# 4) 运行一次测试（忽略用例失败，只为解析）以补齐 surefire provider 等仅在测试期才解析的依赖。
echo "[offline-repo] 步骤 2/2：补齐测试期依赖(surefire provider 等) ..."
mvn -s "$settings" -Dmaven.repo.local="$repo" \
    -f OntologyFrameworkExample/pom.xml test \
    -Dmaven.test.failure.ignore=true -Dsurefire.timeout=90

echo "[offline-repo] 完成: $out"
du -sh "$out" 2>/dev/null || true
