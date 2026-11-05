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
# 本机缓存仓库（作为 file:// 镜像源）按以下优先级解析，无需手动导出 M2_REPO：
#   M2_REPO 环境变量 > 项目 .mvn/maven.config 的 -Dmaven.repo.local > $HOME/.m2/repository
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

# 解析「本机 Maven 本地仓库」（即 Maven 实际使用的 ~/.m2/repository 或自定义 localRepository）：
#   1) 环境变量 M2_REPO（显式覆盖，优先级最高）；
#   2) 项目 .mvn/maven.config 中的 -Dmaven.repo.local=<path>（本工程用它指向仓库外的 <repo>/.m2/repository）；
#   3) 缺省 $HOME/.m2/repository。
# 说明：Maven Wrapper（mvnw）会自动读取 .mvn/maven.config，但本脚本内部调用的是普通 mvn，
#   且会显式传 -Dmaven.repo.local=<快照目录>，故这里只需把「本机缓存源」解析正确，
#   以免把仓库设在非 ~/.m2 时误报「未找到本机 Maven 仓库」。
m2=""
if [ -n "${M2_REPO:-}" ]; then
    m2="$M2_REPO"
    repo_src="M2_REPO 环境变量"
else
    cfg="$root/.mvn/maven.config"
    if [ -f "$cfg" ]; then
        m2="$(sed -n 's/.*-Dmaven\.repo\.local=\([^[:space:]]*\).*/\1/p' "$cfg" | head -n1 | tr -d '\r')"
    fi
    if [ -n "$m2" ]; then
        repo_src=".mvn/maven.config (-Dmaven.repo.local)"
    else
        m2="$HOME/.m2/repository"
        repo_src="默认 \$HOME/.m2/repository"
    fi
fi

[ -d "$m2" ] || { echo "[offline-repo] 未找到本机 Maven 仓库: $m2（来源: $repo_src）" >&2; exit 1; }

echo "[offline-repo] 本地仓库来源: $repo_src"
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
        # 容忍瞬时失败：构建期 IDE 的 Maven 服务可能同时占用 .m2 中同一 metadata 文件导致
        # 短暂 Permission denied；单个别名失败不应中断整个发行包生成。
        if cp "$f" "$a" 2>/dev/null; then
            printf '%s\n' "$a" >> "$aliases"
        fi
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
echo "[offline-repo] 步骤 1：收集 Example 及其基础模块依赖与构建插件 ..."
mvn -s "$settings" -Dmaven.repo.local="$repo" \
    -Daether.checksumPolicy=ignore \
    -pl OntologyFrameworkExample -am clean install -DskipTests

# 3b) 其余应用模块各有独有第三方依赖（例如 TMSD / LatticeTower 用到 poi-ooxml、commons-io），
#     仅安装 Example 无法覆盖，会致目标机离线构建这些应用时报缺失。故逐个 resolve（含 test
#     scope）与 resolve-plugins，把独有依赖与构建插件一并装入快照（数据源=本机 ~/.m2 镜像）。
echo "[offline-repo] 步骤 1b：收集其余应用模块依赖与插件 ..."
other_apps="OntologyFrameworkPizza,OntologyFrameworkTCM,OntologyFrameworkTMSD,OntologyFrameworkPlumBlossom,OntologyFrameworkIndustryInsight,OntologyFrameworkLatticeTower,OntologyFrameworkSiZhuBaZi,OntologyFrameworkZiWeiDouShu"
mvn -s "$settings" -Dmaven.repo.local="$repo" -Daether.checksumPolicy=ignore \
    -fae -DincludeScope=test -pl "$other_apps" dependency:resolve \
    || echo "[offline-repo][警告] 其余应用依赖收集存在缺失（请检查本机缓存）"
mvn -s "$settings" -Dmaven.repo.local="$repo" -Daether.checksumPolicy=ignore \
    -fae -pl "$other_apps" dependency:resolve-plugins \
    || echo "[offline-repo][警告] 其余应用插件收集存在缺失（请检查本机缓存）"

# 4) 驱动一次 test 阶段，让 surefire 解析一部分「仅在测试运行期才解析」的依赖，但不实际执行用例：
#    用 -Dtest 占位 + 忽略「无匹配用例」，不启动 Spring、不连接未运行的 Camunda(9080)/SPARQL(8080)，
#    避免刷出大量连接失败堆栈。保留 failure.ignore 作为兜底。
echo "[offline-repo] 步骤 2/3：解析测试期依赖(surefire 占位，不执行用例) ..."
mvn -s "$settings" -Dmaven.repo.local="$repo" \
    -Daether.checksumPolicy=ignore \
    -f OntologyFrameworkExample/pom.xml test \
    -Dtest=__offline_dep_probe__ -Dsurefire.failIfNoSpecifiedTests=false \
    -Dmaven.test.failure.ignore=true -Dsurefire.timeout=90

# 5) 关键补充：surefire 只有在「确有匹配用例要执行」时才会把运行时动态选择的 provider
#    （org.apache.maven.surefire:surefire-junit-platform）及其传递依赖（junit-platform-launcher/engine）
#    解析进本地仓库；用占位 -Dtest 过滤到不存在用例时 surefire 会直接跳过、根本不解析 provider，
#    导致离线快照缺少该 provider——在目标机跑测试时报 "surefire-junit-platform ... absent"。
#    故这里显式用 dependency:get（transitive=true）把 provider 及传递依赖装入快照，
#    provider 版本取自工程实际生效的 surefire 插件版本（经 help:evaluate 求值，避免写死）。
echo "[offline-repo] 步骤 3/3：显式装入 surefire provider 及其传递依赖 ..."
surefireVer="$(mvn -q -s "$settings" -Dmaven.repo.local="$repo" \
    -Daether.checksumPolicy=ignore -f OntologyFrameworkExample/pom.xml \
    help:evaluate -Dexpression=maven-surefire-plugin.version -DforceStdout 2>/dev/null \
    | tr -d '\r' | grep -Eo '^[0-9][0-9.]*$' | tail -n1)"
[ -n "$surefireVer" ] || { echo "[offline-repo] 未能求值 surefire 插件版本" >&2; exit 1; }
echo "[offline-repo]   surefire 版本: ${surefireVer}"
mvn -s "$settings" -Dmaven.repo.local="$repo" \
    -Daether.checksumPolicy=ignore -f OntologyFrameworkExample/pom.xml \
    dependency:get -Dartifact="org.apache.maven.surefire:surefire-junit-platform:${surefireVer}" -Dtransitive=true

# 6) 补齐「常见 Maven 生命周期插件」的多个默认版本（含传递依赖）。
#    背景：default 生命周期的 clean/resources/compiler/surefire/jar/install/deploy/site 插件，
#    其「默认绑定版本」由所用 Maven 版本决定。随包 apache-maven-3.9.16 与 IDEA 内置 Maven /
#    旧版 mvn 的默认版本不同（例如 3.9.16 默认 clean≈3.5.0，而较旧 Maven 默认 clean=3.2.0、
#    deploy/install=3.1.4、site=3.12.1）。步骤 1 只按「生成机所用 Maven」的默认版本装入快照，
#    目标机若用另一版本 Maven（典型：IDEA 内置 Maven），离线(-o)时就会报
#    "Cannot resolve plugin org.apache.maven.plugins:maven-xxx-plugin:<ver>"。
#    故这里把常见默认版本显式装入快照（transitive 连同全部依赖），使不同版本 Maven 均能离线解析。
#    注：maven-site-plugin 不在此循环内——它不属于 clean/package/install/test 生命周期，本工程也
#    未使用；仅由 Maven 3.9.x 内置 super POM 的 pluginManagement 默认绑定，会触发 IDEA 的
#    "Unresolved plugin" 提示。改为在步骤 7 中以「尽力而为」方式补入其 jar+pom+父 pom（其部分
#    传递依赖本机缓存可能缺失、无网络无法补齐，故不作强制）。
echo "[offline-repo] 步骤 4/4：补齐常见 Maven 生命周期插件版本（兼容 IDEA 内置/其它 Maven）..."
for ap in \
    "org.apache.maven.plugins:maven-clean-plugin:3.2.0:maven-plugin" \
    "org.apache.maven.plugins:maven-resources-plugin:3.3.1:maven-plugin" \
    "org.apache.maven.plugins:maven-compiler-plugin:3.13.0:maven-plugin" \
    "org.apache.maven.plugins:maven-surefire-plugin:3.2.5:maven-plugin" \
    "org.apache.maven.plugins:maven-jar-plugin:3.4.1:maven-plugin" \
    "org.apache.maven.plugins:maven-install-plugin:3.1.2:maven-plugin" \
    "org.apache.maven.plugins:maven-install-plugin:3.1.4:maven-plugin" \
    "org.apache.maven.plugins:maven-deploy-plugin:3.1.2:maven-plugin" \
    "org.apache.maven.plugins:maven-deploy-plugin:3.1.4:maven-plugin" \
    "org.apache.maven.plugins:maven-dependency-plugin:3.8.1:maven-plugin" \
    "org.apache.maven.plugins:maven-antrun-plugin:3.1.0:maven-plugin" ; do
    echo "[offline-repo]   + ${ap}"
    mvn -s "$settings" -Dmaven.repo.local="$repo" \
        -Daether.checksumPolicy=ignore -f OntologyFrameworkExample/pom.xml \
        dependency:get -Dartifact="${ap}" -Dtransitive=true \
        || echo "[offline-repo][警告] 未能装入 ${ap}（本机缓存可能缺失），已跳过"
done

# 7) 补入 maven-site-plugin（Maven 3.9.x 内置 super POM 的 pluginManagement 默认绑定版本，如
#    3.12.1；IDEA 导入工程时会解析它，缺失会在 Maven 面板显示 "Unresolved plugin"）。
#    用 dependency:get -Dtransitive=true 把 site 插件及其全部传递依赖（doxia/jetty/plexus 等）
#    一并装入快照，保证离线可完整解析（此前仅复制 jar+pom 会因缺传递依赖仍无法解析）。
#    数据源=本机 ~/.m2 镜像；若本机缓存缺 site 的传递依赖 jar，需先在联网机补入 ~/.m2 再重建。
echo "[offline-repo] 补入 maven-site-plugin:3.12.1（含全部传递依赖）..."
mvn -s "$settings" -Dmaven.repo.local="$repo" -Daether.checksumPolicy=ignore \
    -f OntologyFrameworkExample/pom.xml \
    dependency:get -Dartifact=org.apache.maven.plugins:maven-site-plugin:3.12.1 -Dtransitive=true \
    || echo "[offline-repo][警告] site 插件或其传递依赖未能全部装入（本机缓存可能缺失）"

# 8) 关键修复：删除 Maven Resolver 的 `_remote.repositories` 与 `*.lastUpdated` 来源标记。
#    原理：`_remote.repositories` 记录了每个 artifact 的来源仓库 id（本脚本以镜像 id=central
#    生成，故都被记作 "central"）。目标机 / IDEA 若使用不同仓库 id（例如安装器写入的
#    file:///-o 镜像 id=packaged-offline），离线(-o)解析时会因
#    "cached from a remote repository ID that is unavailable in current build context"
#    把「文件明明存在」的 artifact 判为未下载而拒用，最终报 "Unresolved plugin"。
#    删除后 Resolver 将其视为「本地已安装」，对任意仓库 id 均可解析，彻底消除该问题。
#    `*.lastUpdated` 是失败下载的时间戳标记，一并清除以免误判「近期已尝试、不再重试」。
echo "[offline-repo] 清理 _remote.repositories 与 *.lastUpdated 来源标记 ..."
find "$out" -name '_remote.repositories' -delete 2>/dev/null || true
find "$out" -name '*.lastUpdated' -delete 2>/dev/null || true

# 9) 关键修复：为「版本区间(range)依赖」补齐 mirror id 匹配的 maven-metadata。
#    背景：Jena/OWLAPI 等以版本区间声明依赖（如 org.antlr:antlr-runtime:[3.5.2,3.5.99]、
#    net.sourceforge.owlapi:owlapi-*:[5.0.0,5.9.9]、log4j:log4j:[1.2.0,1.3)）。Maven 在
#    离线(-o)解析版本区间时，会按「当前仓库 id」查找 maven-metadata-<id>.xml 里的 <versioning>
#    候选版本列表；本脚本以镜像 id=central 生成快照，只产出 maven-metadata-central.xml。
#    而安装器生成的 <安装目录>/maven-settings.xml 把镜像 id 硬编码为 packaged-offline，
#    二者 id 不匹配 → 离线解析这些 range 依赖时报
#    "No versions available for xxx:jar:[a,b] within specified range" → 整棵依赖树解析失败
#    → IDEA / Maven 面板出现满屏 "Unresolved dependency"（命令行用无镜像的用户级 settings，
#    id 恰为 central 才能通过，故该问题只在 IDEA / 发布包 settings 下暴露）。
#    修复：为快照内每个 maven-metadata-central.xml 额外复制一份 maven-metadata-packaged-offline.xml，
#    使「发布包 settings(id=packaged-offline)」与「用户级无镜像 settings(id=central)」两种场景
#    都能离线解析版本区间。注：无后缀的 maven-metadata.xml 不会被 Maven 的 range 解析采用。
echo "[offline-repo] 为版本区间依赖生成 packaged-offline 版 metadata（匹配安装器 mirror id）..."
find "$out" -name 'maven-metadata-central.xml' -print0 2>/dev/null | while IFS= read -r -d '' f; do
    d="$(dirname "$f")"
    [ -e "$d/maven-metadata-packaged-offline.xml" ] || cp "$f" "$d/maven-metadata-packaged-offline.xml" 2>/dev/null || true
done

echo "[offline-repo] 完成: $out"
du -sh "$out" 2>/dev/null || true
