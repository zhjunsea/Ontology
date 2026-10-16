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
# 注：file:// 镜像不提供 .sha1/.md5 校验文件，会触发 "Checksum validation failed,
#     no checksums available" 告警。因 metadata 解析走「仓库自身」的 checksumPolicy
#     （全局 -Daether.checksumPolicy 与 settings 的 <mirror> 均不生效），故在临时
#     settings.xml 里用一个激活的 profile 把 central（含 pluginRepository）的
#     release+snapshot checksumPolicy 设为 ignore；并保留 -Daether.checksumPolicy=ignore 双保险。
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
  <!-- settings 的 <mirror> 不支持 checksumPolicy，而 metadata 解析用的是「仓库自身」的
       checksumPolicy（全局 -Daether.checksumPolicy 不覆盖它）。故用一个激活的 profile
       覆盖 central（含 pluginRepository）的 release+snapshot 校验策略为 ignore；
       mirror 会保留被镜像仓库的策略，从而对 metadata 解析一并生效。 -->
  <profiles>
    <profile>
      <id>offline-checksum-ignore</id>
      <repositories>
        <repository>
          <id>central</id>
          <url>${m2url}</url>
          <releases><checksumPolicy>ignore</checksumPolicy></releases>
          <snapshots><checksumPolicy>ignore</checksumPolicy></snapshots>
        </repository>
      </repositories>
      <pluginRepositories>
        <pluginRepository>
          <id>central</id>
          <url>${m2url}</url>
          <releases><checksumPolicy>ignore</checksumPolicy></releases>
          <snapshots><checksumPolicy>ignore</checksumPolicy></snapshots>
        </pluginRepository>
      </pluginRepositories>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>offline-checksum-ignore</activeProfile>
  </activeProfiles>
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
    -Daether.checksumPolicy=ignore \
    -pl OntologyFrameworkExample -am clean install -DskipTests

# 4) 驱动一次 test 阶段以解析 surefire provider 等仅在测试期才解析的依赖，但不实际执行用例：
#    用 -Dtest 占位 + 忽略「无匹配用例」，让 surefire 照常加载 provider（依赖得以入快照），
#    却不启动 Spring、不连接未运行的 Camunda(9080)/SPARQL(8080)，避免刷出大量连接失败堆栈。
#    保留 failure.ignore 作为兜底（万一过滤未生效也不阻断构建）。
echo "[offline-repo] 步骤 2/2：解析测试期依赖(surefire provider 等，不执行用例) ..."
mvn -s "$settings" -Dmaven.repo.local="$repo" \
    -Daether.checksumPolicy=ignore \
    -f OntologyFrameworkExample/pom.xml test \
    -Dtest=__offline_dep_probe__ -Dsurefire.failIfNoSpecifiedTests=false \
    -Dmaven.test.failure.ignore=true -Dsurefire.timeout=90

echo "[offline-repo] 完成: $out"
du -sh "$out" 2>/dev/null || true
