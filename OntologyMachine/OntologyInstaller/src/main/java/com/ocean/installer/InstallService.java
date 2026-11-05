package com.ocean.installer;

import java.io.BufferedReader;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * 安装器核心逻辑：
 * <ol>
 *   <li>直接从分发包 softwares/ 就地解压各 zip 到「安装目录」，并记录每个软件的 home（zip 顶层目录）；</li>
 *   <li>把 CodeArts Agent 插件（codearts 包）移入 IDEA（idea/ideaic 包）的 plugins/ 目录，并在 IDEA 的
 *       {@code bin/idea64.exe.vmoptions} 里加 {@code -Didea.plugins.path=<IDE_HOME>/plugins}，使 IDEA 打开即自带插件；</li>
 *   <li>把解压出的 JDK 注册为 IDEA 的 SDK，并设为工作区/新工程的默认 Project SDK；配置写入解压出的
 *       IDEA 环境内（经 {@code bin/idea.properties} 便携化，不依赖用户 profile）；</li>
 *   <li>把释放出的工程各 pom 的 {@code <java.version>} 对齐为安装目录内 JDK 的实际版本，并为示例模块
 *       生成 IDEA Spring Boot 运行配置（照工程内 Example 形态）；</li>
 *   <li>把随包携带的离线 Maven 仓库释放到安装目录，并让释放出的工作区默认使用它（无网也能构建/测试）；</li>
 *   <li>MySQL 在解压出的目录建 data，执行 {@code bin\mysqld.exe --no-defaults --initialize-insecure --basedir=<abs> --datadir=<abs>}
 *       （用绝对路径并忽略系统 {@code my.ini}；失败时回显 mysqld 错误日志并提示常见原因）；</li>
 *   <li>把「工作区聚合工程」解压到「开发工作目录」：工作目录根为父聚合 pom.xml，OntologyFrameworkExample
 *       为其子模块；改写示例模块的 application.yaml 与 start-env.cmd（MySQL 改为便携模式 mysqld --console，
 *       各 home 指向安装目录下实际位置）。</li>
 * </ol>
 */
public final class InstallService {

    /** 工程包内示例模块的子目录名（解压后位于「工作目录」之下，即工作区聚合工程的一个子模块）。 */
    public static final String PROJECT_DIR = "OntologyFrameworkExample";
    private static final String PROJECT_ZIP = "OntologyFrameworkExample-0.0.1-SNAPSHOT-project.zip";
    /** 离线 Maven 仓库（随分发携带的 zip，及安装后释放到的目录名）。 */
    private static final String MAVEN_REPO_ZIP = "maven-repo.zip";
    private static final String MAVEN_REPO_DIR = "maven-repo";
    /** 「本体应用构建」SKILL 在分发目录中的来源子目录。 */
    private static final String SKILL_SRC_DIR = "skills/ontology-app-builder";
    /** SKILL 安装时投放到的项目级技能目录（相对工作区聚合工程根）。 */
    static final String SKILL_DEPLOY_DIR = ".codeartsdoer/skills/ontology-app-builder";
    private static final Charset GBK = Charset.forName("GBK");
    private static final int MYSQL_PORT = 3306;

    private InstallService() {
    }

    public static void install(Path installDir, Path workDir, Consumer<String> log) throws Exception {
        checkWindowsVersion(log);
        checkChinesePath(installDir, log);
        Path resHome = resolveResourceHome();
        Path softwaresSrc = resHome.resolve("softwares");
        Path projectZip = resHome.resolve(PROJECT_ZIP);
        log.accept("资源目录: " + resHome);
        if (!Files.isDirectory(softwaresSrc)) {
            throw new IOException("未找到软件目录: " + softwaresSrc);
        }
        if (!Files.isRegularFile(projectZip)) {
            throw new IOException("未找到工程包: " + projectZip);
        }

        Files.createDirectories(installDir);
        Files.createDirectories(workDir);
        List<Path> zips = listZips(softwaresSrc);
        log.accept("发现 " + zips.size() + " 个软件包。");

        // 0) 磁盘空间预检：解压前估算所需空间，剩余不足则立即失败，避免解压一半留下半成品。
        ensureDiskSpace(installDir, workDir, zips, resHome.resolve(MAVEN_REPO_ZIP), projectZip, log);

        // 1) 解压到安装目录，记录 home。直接读取分发包 softwares/ 下的原始 zip 就地解压，
        //    不再先把 zip 复制到「安装目录/softwares」——该副本从未被读取，纯属冗余，安装后会白占数 GB 空间。
        log.accept("\n[1/3] 解压软件到 " + installDir);
        Map<String, Path> homes = new LinkedHashMap<>();
        for (Path z : zips) {
            String top = commonTopDir(z);
            Path extractRoot = installDir;
            if (top == null) {
                top = stripExt(z.getFileName().toString());
                extractRoot = installDir.resolve(top);
                Files.createDirectories(extractRoot);
            }
            log.accept("  解压 " + z.getFileName()
                    + (extractRoot.equals(installDir) ? "" : " (扁平包 -> " + top + ")"));
            unzip(z, extractRoot);
            Path home = installDir.resolve(top).normalize();
            homes.put(z.getFileName().toString(), home);
            log.accept("    -> " + home);
        }
        // 1.0) 修正「带包装目录」的压缩包 home：部分 vendor 包（如本仓库的 JDK：
        //      jdk-27_windows-x64_bin/jdk-27/bin/java.exe）会多套一层目录，使 commonTopDir 得到的是
        //      包装目录而非真正的软件根；此处下探定位含特征文件的真实 home，否则 JAVA_HOME 等占位符会指错。
        correctNestedHome(homes, "jdk", "bin/java.exe", log);
        // 1.1) 安装 IDEA 插件：CodeArts Agent 插件随软包分发，解压后落在安装根目录，
        //      需移入 IDEA 的 plugins/ 目录，使解压出的 IDEA 打开即自带该插件。
        installIdeaPlugin(homes, log);
        // 1.2) IDEA 2025.2 起 IDE_HOME/plugins 不再被自动扫描（内置插件由预生成清单 plugin-classpath.txt 枚举），
        //      故用 idea.plugins.path 把「用户插件目录」显式指向 IDEA_HOME/plugins，使落在此处的 CodeArts 插件被加载。
        Path ideaHome = findHome(homes, "idea");
        if (ideaHome != null) {
            configureIdeaPluginPath(ideaHome, log);
            // 1.2.1) 便携化：把 IDEA 的配置/缓存目录指向 IDE_HOME 内（写 bin/idea.properties），
            //        使全局 SDK 等设置随解压出的 IDEA 目录走，不再依赖 %APPDATA% 用户 profile。
            enablePortableIdeaConfig(ideaHome, log);
        }
        // 1.3) 释放离线 Maven 仓库快照到安装目录：目标机无网络时，释放出的工程用它构建并运行测试。
        Path repoDir = installDir.resolve(MAVEN_REPO_DIR);
        Path repoZip = resHome.resolve(MAVEN_REPO_ZIP);
        if (Files.isRegularFile(repoZip)) {
            log.accept("\n[1.3] 释放离线 Maven 仓库 -> " + repoDir);
            unzip(repoZip, repoDir);
            log.accept("    -> " + repoDir);
        } else {
            log.accept("\n[1.3] [跳过] 未找到离线仓库包: " + repoZip);
        }
        // 1.3.1) 把随包的独立 Maven（apache-maven-*）的 conf/settings.xml 的 <localRepository>
        //        由缺省（${user.home}/.m2/repository）改为随包释放到安装目录的离线仓库，
        //        使该 Maven 不论在何目录运行都使用随包仓库、无需联网。
        if (Files.isDirectory(repoDir)) {
            configureMavenLocalRepo(homes, repoDir, log);
            // 1.3.1.1) 同上，把离线仓库写入 IDEA「内置 Maven」的 conf/settings.xml，
            //        否则用 IDEA 打开工作区时 IDEA 用自带 Maven、回落到 ~/.m2，离线仓库不生效。
            configureIdeaBundledMavenRepo(ideaHome, repoDir, log);
            // 1.3.1.2) 关键：把离线仓库 & 离线模式写入「默认用户 Maven 设置」~/.m2/settings.xml。
            //        IDEA「用户设置文件」Override 未勾选时用默认位置的该文件，且用户级 localRepository
            //        优先级高于 Maven 自带 conf/settings.xml——不改这里，即便把 Maven 主路径改成随包 Maven，
            //        依赖解析仍会回落到 ~/.m2/repository 并从 repo.maven.apache.org 下载。
            configureMavenUserSettings(repoDir, log);
        }

        // 2) MySQL 初始化
        log.accept("\n[2/3] 初始化 MySQL");
        Path mysqlHome = findMysqlHome(homes);
        if (mysqlHome == null) {
            log.accept("  [跳过] 未在软件包中找到 MySQL");
        } else {
            initMysql(mysqlHome, homes, log);
        }

        // 3) 解压工程 + 生成配置
        //    工程包解压到工作目录后，工作目录即「工作区聚合工程根」（根为父聚合 pom.xml，
        //    OntologyFrameworkExample 为其子模块）。因此用 IDEA 打开工作目录时，示例与后续
        //    新建的应用会作为并列模块同时出现在工程视图中——这正是「打开示例工程后能看到新工程」的关键。
        log.accept("\n[3/3] 解压工程并生成配置");
        unzip(projectZip, workDir);
        Path workspaceRoot = workDir;
        Path moduleRoot = workDir.resolve(PROJECT_DIR);
        if (!Files.isDirectory(moduleRoot)) {
            // 兼容不含子模块目录的旧工程包：退化为工作区根即模块根。
            moduleRoot = workspaceRoot;
        }
        log.accept("  工作区（聚合工程）根: " + workspaceRoot);
        log.accept("  示例模块目录: " + moduleRoot);

        // 3.1) 把解压出的 JDK 注册为 IDEA 的 SDK，并设为工作区/新工程的默认 Project SDK，
        //      否则打开释放出的工作区会因无 SDK 而报「JDK 未设置」。
        if (ideaHome != null) {
            configureIdeaJdk(ideaHome, homes, workspaceRoot, log);
        }
        // 3.1.1) 把释放出的工程各 pom 的 <java.version> 对齐为安装目录内 JDK 的实际主版本，
        //        使工程编译/运行版本跟随随包携带的 JDK，而非写死的 26。
        Path jdkHome = findHome(homes, "jdk");
        if (jdkHome != null) {
            alignPomJavaVersion(workspaceRoot, jdkHome, log);
        }
        // 3.1.2) 参考工程内 Example 的 Run Configuration，为工作区（聚合工程）生成 IDEA Spring Boot 运行配置，
        //        使用户打开工作区后无需手工新建 Run Configuration 即可直接启动。
        //        落点须在工作区根而非示例模块自身：IDEA 仅识别工程根下的 .idea，模块内的 .idea 对聚合工程无效
        //        （与工程内 PlumBlossomApplication 的运行配置落点一致）。
        generateExampleRunConfiguration(workspaceRoot, log);

        // 便携 MySQL 由安装器以 --initialize-insecure 初始化（root 空密码），把工作区内 .properties 的
        // 硬编码 jdbc.password 对齐为空，避免释放后的工程连接便携 MySQL 失败。
        alignJdbcPassword(workspaceRoot, log);

        Map<String, String> ph = buildPlaceholders(homes);
        String yaml = readResource("templates/application.yaml");
        for (Map.Entry<String, String> e : ph.entrySet()) {
            yaml = yaml.replace(e.getKey(), e.getValue());
        }
        // application.yaml 属示例模块自身资源（相对路径以模块根为基准），写入示例模块。
        Path appYaml = moduleRoot.resolve("src/main/resources/application.yaml");
        Files.createDirectories(appYaml.getParent());
        Files.writeString(appYaml, yaml, StandardCharsets.UTF_8);
        log.accept("  已生成 " + appYaml);

        String cmdTpl = readResource("templates/start-env.cmd");
        // Windows cmd 要求 CRLF 行结尾：先把模板换行（LF/CRLF 混用）规整为 CRLF，再替换占位符，
        // 否则纯 LF 的 .cmd 会被 cmd 解析错位（吞字符、set 变量不生效致 start 命令参数为空）。
        String cmdOut = cmdTpl.replace("\r\n", "\n").replace("\n", "\r\n")
                .replace("@{APP_YAML}", appYaml.toAbsolutePath().toString());
        // 同步替换 JAVA_HOME 等软件 home 占位符（指向安装目录下解压出的 jdk 等），
        // 使 start-env.cmd 用随包携带的 JDK 运行 EnvPrepare，不依赖用户机器预装 Java。
        for (Map.Entry<String, String> e : ph.entrySet()) {
            cmdOut = cmdOut.replace(e.getKey(), e.getValue());
        }
        // start-env.cmd 与 EnvPrepare 可执行 jar 同目录（示例模块根，脚本以 %~dp0 定位 jar）。
        Path startCmd = moduleRoot.resolve("start-env.cmd");
        Files.write(startCmd, cmdOut.getBytes(GBK));
        log.accept("  已生成 " + startCmd + " (GBK, CRLF)");

        StringBuilder hb = new StringBuilder();
        hb.append("软件 home 目录清单\r\n安装目录: ").append(installDir).append("\r\n\r\n");
        for (Map.Entry<String, Path> e : homes.entrySet()) {
            hb.append(e.getKey()).append("  ->  ").append(e.getValue()).append("\r\n");
        }
        hb.append("\r\n关键 home:\r\n");
        for (Map.Entry<String, String> e : ph.entrySet()) {
            hb.append("  ").append(e.getKey()).append(" = ").append(e.getValue()).append("\r\n");
        }
        Path homesFile = workspaceRoot.resolve("software-homes.txt");
        Files.writeString(homesFile, hb.toString(), StandardCharsets.UTF_8);
        log.accept("  已记录 home 清单: " + homesFile);

        // 3.2) 让释放出的工作区默认使用随安装包分发的离线 Maven 仓库（无网也能构建/测试）。
        //      写在工作区根，使聚合工程（含各子模块）整体共享该配置。
        if (Files.isDirectory(repoDir)) {
            writeMavenConfig(workspaceRoot, repoDir, log);
            // 3.2.1) 让 IDEA 显式重写「用户设置文件」与「本地仓库」指向随包离线仓库：
            //        写项目级 .idea/workspace.xml 的 MavenImportPreferences（IDEA 2026 起该状态存于
            //        @Storage("$WORKSPACE_FILE$")，即工程 .idea/workspace.xml），并在安装目录内生成专用
            //        maven-settings.xml。否则 IDEA 用缺省位置的 ~/.m2/settings.xml，其 localRepository 优先级
            //        高于 Maven 自带 settings，导致依赖解析仍回落到 ~/.m2/repository 并访问公网。
            //        同时把 Maven 主路径固定为随包 apache-maven（CUSTOM），否则 IDEA 默认「Use Maven wrapper」
            //        会按 maven-wrapper.properties 的 distributionUrl 下载 Maven 本身而访问公网。
            configureIdeaMavenWorkspaceSettings(workspaceRoot, installDir, repoDir,
                    findHome(homes, "apache-maven"), log);
        }
        // 3.2.2) 生成 Maven wrapper：mvnw 转发到随包 apache-maven；且把随包 Maven 发行版 zip 内置到
        //        .mvn/wrapper 下，distributionUrl 指向本地 file 地址——否则 IDEA「Use Maven wrapper」会按
        //        distributionUrl 去 repo.maven.apache.org 下载 Maven 本身（IDEA 不执行 mvnw 脚本）。
        writeMavenWrapper(workspaceRoot, homes, softwaresSrc, log);

        // 3.3) 投放「本体应用构建」SKILL 到工作区根 .codeartsdoer/skills/ontology-app-builder/，
        //      使客户在 IDEA 中打开工作区后，码道（CodeArts）即识别该技能，按 14 步流程协助构建新应用。
        installAppBuilderSkill(resHome, workspaceRoot, log);

        log.accept("\n安装完成。用 IDEA 打开工作区目录 " + workspaceRoot
                + " 即可看到示例与新建应用并列；进入示例模块 " + moduleRoot.getFileName()
                + " 双击 start-env.cmd 一键准备并启动环境。");
    }

    /**
     * 初始化便携式 MySQL 数据目录（{@code mysqld --initialize-insecure}，root 空密码）。
     * <p>加固点（针对「干净机器」上 {@code mysqld} 以 {@code -1073741819}/{@code 0xC0000005}（访问冲突）退出的问题）：
     * <ol>
     *   <li><b>自检二进制</b>：先跑 {@code mysqld --version}，防止解压不完整/磁盘空间不足导致 mysqld 损坏（访问冲突常见诱因）；</li>
     *   <li><b>{@code --no-defaults}</b>：忽略系统 {@code my.ini}/{@code my.cnf}，避免目标机既有 MySQL 配置（basedir/datadir 等）干扰便携实例；</li>
     *   <li><b>绝对路径</b>：{@code --basedir}/{@code --datadir} 用绝对路径，不依赖子进程工作目录；</li>
     *   <li><b>空格加引号</b>：命令（mysqld 路径）与 {@code --basedir}/{@code --datadir} 若含空格，统一用双引号括起
     *       （{@code "D:\my app\...\mysqld.exe" --basedir="D:\my app\..."}），避免空格把命令/路径拆成多个参数导致崩溃；</li>
     *   <li><b>兼容 PATH（继承但剔除 JDK/Java）</b>：mysqld 子进程**继承**系统 PATH，仅剔除随包 JDK 的 {@code bin}
     *       及任何含 {@code java.exe} 的目录——JDK 自带整套 {@code vcruntime140/140_1/msvcp140/ucrtbase} 等 CRT DLL，
     *       若被 mysqld 按 PATH 顺序抢先加载，会因版本不兼容在 {@code --initialize} 深度初始化时引发 {@code 0xC0000005}（访问冲突）。
     *       注意<b>不能</b>把 PATH 整体替换成「仅系统目录」：部分机器的 System32 CRT 较旧，mysqld 实际依赖继承 PATH 中
     *       第三方（如 Python 自带）提供的新版 {@code vcruntime140.dll}，清空 PATH 反而 0xC0000005。
     *       因此保留继承 PATH、只剔除 JDK/Java 目录；若仍崩溃，再回退到「仅系统目录 PATH」重试一次；</li>
     *   <li><b>失败可诊断</b>：初始化失败时回显 mysqld 的 {@code *.err} 错误日志并提示常见原因。</li>
     * </ol>
     */
    private static void initMysql(Path mysqlHome, Map<String, Path> homes, Consumer<String> log)
            throws IOException, InterruptedException {
        Path mysqld = mysqlHome.resolve("bin").resolve("mysqld.exe");
        if (!Files.isRegularFile(mysqld)) {
            throw new IOException("未找到 mysqld.exe: " + mysqld);
        }
        // 修复 VC++ 运行库版本不匹配：把随包 JDK 自带的 msvcp140/vcruntime140(_1)（14.42，app-local）复制到
        // mysqld 同目录（mysql\bin）。Windows 加载 DLL 优先找 exe 同目录，从而不再受目标机 System32 里过旧的
        // msvcp140（如 14.36.32532.0）影响——旧版会在 --initialize 时于 msvcp140.dll 内 0xC0000005 崩溃。
        // 放在 ibdata1 跳过判断之前，确保即使已初始化（运行期亦用此 mysqld）也补齐 DLL。
        ensureVcRuntimeLocal(mysqld.getParent(), homes, log);
        Path dataDir = mysqlHome.resolve("data");
        // 幂等：已初始化的 data（存在 ibdata1）直接跳过，保留既有数据。
        if (Files.isRegularFile(dataDir.resolve("ibdata1"))) {
            log.accept("  data 目录已初始化（存在 ibdata1），跳过初始化（保留原有数据）。");
            return;
        }
        Files.createDirectories(dataDir);

        // (1)(2)(3) 忽略系统配置(--no-defaults) + 绝对路径；命令/路径含空格时用双引号括起（见 quote()）。
        Path absBase = mysqlHome.toAbsolutePath().normalize();
        Path absData = dataDir.toAbsolutePath().normalize();

        // 二进制自检：无法运行说明解压不完整/磁盘不足/缺运行库，直接给出可读原因，避免后续「访问冲突」黑盒。
        log.accept("  [自检] 运行 mysqld --version 检查二进制可用性…");
        Path verCapture = mysqlHome.resolve("init-version.out");
        int vrc = runMysqldCaptured(List.of(mysqld.toString(), "--no-defaults", "--version"),
                mysqlHome, verCapture, log);
        if (vrc != 0) {
            logMysqlFailureDiagnostics(log, absBase, absData, null);
            throw new IOException("mysqld 无法运行（--version 退出码 " + vrc + " / " + hex(rc(vrc)) + "）。"
                    + "请检查：磁盘空间是否充足、软件包解压是否完整、" + hint(rc(vrc)));
        }
        log.accept("  [自检] mysqld 可运行（退出码 0）。");

        // 用 ProcessBuilder 直接传 argv（不经 cmd /c、不手工拼引号），由 CreateProcess 按参数边界正确加引号。
        // 关键改进：用**显式 --log-error=<文件>** 把 mysqld 日志强制写到我们指定的文件——
        // 不再依赖 --console（在存在控制台时会写去控制台、捕获不到）或默认的 datadir\*.err（早期崩溃时来不及建）。
        // 这样即便初始化中途崩溃，也能从该文件看到它执行到哪一步、报什么错，用于精准定位。
        Path logErrPath = mysqlHome.resolve("init-mysqld.err");
        List<String> initArgv = List.of(
                mysqld.toString(),
                "--no-defaults",
                "--initialize-insecure",
                "--log-error=" + logErrPath,
                "--basedir=" + absBase,
                "--datadir=" + absData);

        // 人类可读命令（仅供日志与『手动兜底』粘贴到 cmd 使用，单层引号形式；不含 --log-error，保持与已验证命令一致）
        String initLine = quote(mysqld.toString())
                + " --no-defaults --initialize-insecure"
                + " --basedir=" + quote(absBase.toString())
                + " --datadir=" + quote(absData.toString());

        log.accept("  mysqld: " + mysqld);
        log.accept("  basedir: " + absBase);
        log.accept("  datadir: " + absData);
        log.accept("  日志文件(--log-error): " + logErrPath);
        log.accept("  执行(直接参数，不经 cmd 包装): " + displayArgv(initArgv));
        Path initCapture = mysqlHome.resolve("init-mysqld.out");
        // (4) 首次：继承完整环境（与用户在 cmd 中手动执行完全等价，不改 PATH）
        int rc = runMysqldCaptured(initArgv, mysqlHome, initCapture, log);
        // (5) 回退：仅当首次 0xC0000005 且 datadir 仍为空时，用『仅系统目录 PATH』再试一次（覆盖个别 PATH 冲突机）
        if (rc != 0 && rc(rc) == 0xC0000005L && isEmptyDir(dataDir)) {
            log.accept("  [回退] 首次初始化失败(0xC0000005)，改用『仅系统目录 PATH』(直接参数) 重试一次…");
            Map<String, String> cleanEnv = new LinkedHashMap<>();
            cleanEnv.put("PATH", buildCleanMysqlPath());
            rc = runMysqldCaptured(initArgv, mysqlHome, initCapture, log, cleanEnv);
        }
        if (rc != 0) {
            // 优先回显显式 --log-error 文件（最能说明崩溃位置）
            logTail(logErrPath, log, "mysqld --log-error 文件");
            String err = readMysqlErr(dataDir);
            if (err != null && !err.isBlank()) {

                log.accept("  ---- mysqld 错误日志（末尾） ----");
                for (String line : err.split("\n")) {
                    log.accept("    | " + line);
                }
                log.accept("  --------------------------------");
            }
            logMysqlFailureDiagnostics(log, absBase, absData, null);
            // 手动初始化兜底：这里打印一条可直接粘贴到 cmd 的初始化命令（单层引号形式）。
            // 用户手动执行一次成功后，安装器检测到 datadir 下的 ibdata1 即跳过初始化并继续。
            log.accept("  ============ 手动初始化兜底（自动初始化失败时使用） ============");
            log.accept("  请打开「命令提示符(cmd)」（普通权限即可，无需管理员），粘贴执行下面这一整行完成初始化，");
            log.accept("  然后在安装器中点『重试』（安装器检测到 datadir 下已有 ibdata1 会自动跳过初始化）：");
            log.accept("    " + initLine);
            log.accept("  ============================================================");
            throw new IOException("MySQL 初始化失败 (退出码 " + rc + " / " + hex(rc(rc)) + ")。"
                    + hint(rc(rc))
                    + " 可按上方『手动初始化兜底』在 cmd 中手动执行一次后点『重试』。"
                    + " 详见上方 mysqld 输出、失败诊断信息与 " + dataDir + " 目录下的 *.err 文件。");
        }
        log.accept("  MySQL data 初始化完成（root 空密码）。");
    }

    /**
     * 构造 mysqld 子进程的「干净」PATH：仅保留 Windows 系统目录（System32 / 系统根 / Wbem / PowerShell / OpenSSH），
     * 不继承用户或第三方 PATH（如 VMware\bin、其它 JDK 或 runtime\bin），也<b>不注入随包 JDK 的 bin</b>，
     * 以免 mysqld 加载到不兼容的 vcruntime140 / msvcp140 / ucrtbase 等 CRT DLL 而崩溃（0xC0000005）。
     */
    private static String buildCleanMysqlPath() {
        String sysRoot = System.getenv("SystemRoot");
        if (sysRoot == null || sysRoot.isBlank()) {
            sysRoot = System.getenv("windir");
        }
        if (sysRoot == null || sysRoot.isBlank()) {
            sysRoot = "C:\\Windows";
        }
        return sysRoot + "\\System32" + File.pathSeparator
                + sysRoot + File.pathSeparator
                + sysRoot + "\\System32\\Wbem" + File.pathSeparator
                + sysRoot + "\\System32\\WindowsPowerShell\\v1.0" + File.pathSeparator
                + sysRoot + "\\System32\\OpenSSH";
    }

    /**
     * 构造 mysqld 子进程 PATH：<b>继承</b>系统 PATH，但剔除随包 JDK 的 {@code bin} 及任何含 {@code java.exe} 的目录
     * （JDK/JRE 自带整套 CRT DLL，可能抢在 Microsoft VC++ 运行库之前被 mysqld 加载，导致 {@code 0xC0000005}）。
     * <p>相较「替换为 System32-only」，本策略<b>保留</b>其它可能提供兼容 CRT 的目录（如某些机器上 Python 自带的新版
     * {@code vcruntime140.dll}）——这类机器恰恰依赖继承 PATH 才能运行 mysqld。若剔除后 PATH 为空，则回退到系统目录。
     */
    private static String buildMysqlPath(Map<String, Path> homes) {
        String raw = System.getenv("PATH");
        List<String> kept = new ArrayList<>();
        Path jdkBin = null;
        if (homes != null && homes.get("jdk") != null) {
            jdkBin = homes.get("jdk").toAbsolutePath().normalize().resolve("bin");
        }
        if (raw != null && !raw.isBlank()) {
            for (String d : raw.split(File.pathSeparator)) {
                if (d == null || d.isBlank()) {
                    continue;
                }
                String t = d.trim();
                Path p;
                try {
                    p = Path.of(t).toAbsolutePath().normalize();
                } catch (RuntimeException ex) {
                    kept.add(t);
                    continue;
                }
                // 剔除随包 JDK 的 bin 及其子目录
                if (jdkBin != null && p.startsWith(jdkBin)) {
                    continue;
                }
                // 剔除任何 JDK/JRE 的 bin（含 java.exe 的目录）
                if (Files.isRegularFile(p.resolve("java.exe"))) {
                    continue;
                }
                kept.add(t);
            }
        }
        if (kept.isEmpty()) {
            return buildCleanMysqlPath();
        }
        return String.join(File.pathSeparator, kept);
    }

    /** datadir 是否为「存在且为空目录」（用于判断首次初始化崩溃是否未落盘，从而安全回退重试）。 */
    private static boolean isEmptyDir(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.findAny().isEmpty();
        } catch (IOException ex) {
            return false;
        }
    }

    /** 把 Java 的 int 退出码按 Windows 语义还原为无符号 32 位。 */
    private static long rc(int code) {
        return code & 0xFFFFFFFFL;
    }

    private static String hex(long code) {
        return "0x" + Long.toHexString(code).toUpperCase(Locale.ROOT);
    }

    /** 把 Windows 常见崩溃码翻译为可读提示。 */
    private static String hint(long code) {
        if (code == 0xC0000005L) {
            return "退出码 0xC0000005（STATUS_ACCESS_VIOLATION）：常见于启动 mysqld 时命令行参数被错误拆分"
                    + "（尤其安装路径/目录含空格，旧实现经 cmd 双层引号易拆错 --basedir/--datadir），"
                    + "或缺少/不匹配 Microsoft Visual C++ 运行库、mysqld 二进制损坏，或存在冲突的 MySQL 配置(my.ini)。"
                    + "建议按上方『手动初始化兜底』在 cmd 中手动初始化后重试。";
        }
        if (code == 0xC000007BL) {
            return "退出码 0xC000007B（STATUS_INVALID_IMAGE_FORMAT）：二进制损坏或运行库不匹配。";
        }
        if (code == 0xC0000135L) {
            return "退出码 0xC0000135（STATUS_DLL_NOT_FOUND）：缺少 Microsoft Visual C++ 运行库。";
        }
        return "";
    }

    /**
     * 在 mysqld 无法运行或初始化失败时，输出足够的「现场环境」信息，便于在拿不到 {@code *.err} 的目标机上定位根因。
     * 覆盖：OS/架构、Java 版本、mysqld 是否存在及大小、basedir/datadir 是否存在、datadir 内容（尤其有无 *.err）、
     * 磁盘可用空间、实际传给子进程的 PATH，并给出常见原因清单。
     */
    private static void logMysqlFailureDiagnostics(Consumer<String> log, Path absBase, Path absData,
            Map<String, String> extraEnv) {
        log.accept("  ---- MySQL 失败诊断信息（用于定位目标机环境问题） ----");
        log.accept("    操作系统: " + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " (" + System.getProperty("os.arch") + ")");
        log.accept("    Java: " + System.getProperty("java.version")
                + " / " + System.getProperty("java.vendor"));
        log.accept("    管理员权限: " + (isElevated() ? "是（已提升）"
                : "否（未提升；mysqld 建库/计划任务兜底通常需要管理员，请右键『以管理员身份运行』start-installer.cmd）"));
        // 自动抓取 Windows 事件日志里 mysqld 的崩溃记录（含「故障模块名称」），这是「写日志前就崩」唯一可见的真因
        dumpRecentAppErrorEvents(log);
        Path mysqld = absBase.resolve("bin").resolve("mysqld.exe");
        log.accept("    mysqld: " + mysqld);
        try {
            log.accept("    mysqld 大小: " + Files.size(mysqld) + " 字节");
        } catch (IOException ex) {
            log.accept("    mysqld 大小: 读取失败（" + ex.getMessage() + "）");
        }
        log.accept("    basedir: " + absBase + (Files.isDirectory(absBase) ? "（存在）" : "（不存在!）"));
        boolean ddExists = Files.isDirectory(absData);
        log.accept("    datadir: " + absData + (ddExists ? "（存在）" : "（不存在）"));
        if (ddExists) {
            try (Stream<Path> s = Files.list(absData)) {
                List<String> names = s.map(p -> p.getFileName().toString()).sorted().toList();
                log.accept("    datadir 内容(" + names.size() + " 项): "
                        + (names.isEmpty() ? "<空>" : String.join(", ", names)));
                boolean hasErr = names.stream()
                        .anyMatch(n -> n.toLowerCase(Locale.ROOT).endsWith(".err"));
                if (!hasErr) {
                    log.accept("    注意: datadir 下未生成 *.err —— mysqld 在极早期崩溃，或对 datadir 无写权限。");
                }
            } catch (IOException ex) {
                log.accept("    datadir 内容: 读取失败（" + ex.getMessage() + "）");
            }
        }
        try {
            log.accept("    磁盘可用空间: " + gb(absBase.toFile().getUsableSpace())
                    + " GB（" + absBase.getRoot() + "）");
        } catch (RuntimeException ex) {
            log.accept("    磁盘可用空间: 读取失败（" + ex.getMessage() + "）");
        }
        String path = (extraEnv != null && extraEnv.containsKey("PATH"))
                ? extraEnv.get("PATH") : System.getenv("PATH");
        log.accept("    子进程 PATH: " + path);
        // 系统 VC++ 运行库存在性（定位「缺运行库」根因）
        String sysRoot = System.getenv("SystemRoot");
        if (sysRoot == null || sysRoot.isBlank()) {
            sysRoot = System.getenv("windir");
        }
        if (sysRoot == null || sysRoot.isBlank()) {
            sysRoot = "C:\\Windows";
        }
        Path sys32 = Path.of(sysRoot, "System32");
        for (String dll : new String[]{"vcruntime140.dll", "vcruntime140_1.dll", "msvcp140.dll", "ucrtbase.dll"}) {
            Path f = sys32.resolve(dll);
            log.accept("    System32\\" + dll + ": " + (Files.isRegularFile(f) ? "存在" : "**缺失**（VC++ 运行库不全）"));
        }
        // 继承 PATH 中含 vcruntime140.dll 的第三方目录（潜在冲突 CRT 源）
        String rawPath = System.getenv("PATH");
        if (rawPath != null && !rawPath.isBlank()) {
            List<String> conflictDirs = new ArrayList<>();
            for (String d : rawPath.split(File.pathSeparator)) {
                if (d == null || d.isBlank()) {
                    continue;
                }
                try {
                    if (Files.isRegularFile(Path.of(d.trim(), "vcruntime140.dll"))) {
                        conflictDirs.add(d.trim());
                    }
                } catch (RuntimeException ignore) {
                    // 非法路径跳过
                }
            }
            if (!conflictDirs.isEmpty()) {
                log.accept("    继承 PATH 中含 vcruntime140.dll 的目录(潜在冲突源): " + String.join("; ", conflictDirs));
            }
        }
        // 安全软件检测：企业 EDR/杀毒常按「父进程血缘」拦截未签名安装器(java.exe)派生的 mysqld 建库行为，
        // 这是「同一二进制、同一命令在 cmd 手动成功、经安装器派生却 0xC0000005」的最常见原因。
        List<String> sec = detectSecuritySoftware();
        if (!sec.isEmpty()) {
            log.accept("    检测到安全软件(可能拦截 mysqld 建库): " + String.join(", ", sec));
            log.accept("    提示: 若初始化因 0xC0000005 失败，请将安装目录及 " + absBase.resolve("bin").resolve("mysqld.exe")
                    + " 加入上述安全软件的信任/白名单后重试。");
        } else {
            log.accept("    未检测到常见安全软件进程（不代表一定没有 EDR，部分系统服务进程名可能不可见）。");
        }
        log.accept("    常见原因: ①启动 mysqld 的命令行参数被拆错（安装路径含空格时旧实现最常见）；"
                + "②缺少/版本不匹配的 Microsoft Visual C++ 运行库(vcruntime140.dll 等)；③datadir 残留文件或权限不足；"
                + "④磁盘空间不足；⑤PATH 中存在与 mysqld 不兼容的 DLL；⑥安全软件拦截（已可在诊断中查看）。");
        log.accept("  ----------------------------------------------------");
    }

    /**
     * 枚举常见杀毒/EDR 进程名关键词，返回命中的可读名称。用于诊断「EDR 按父进程血缘拦截安装器(java)派生的
     * mysqld 建库」这一常见根因（同一二进制、同一命令在 cmd 手动成功，经安装器派生却 0xC0000005）。
     */
    private static List<String> detectSecuritySoftware() {
        String[][] sigs = {
            {"360tray", "360安全卫士"}, {"360safe", "360安全卫士"}, {"zhudongfangyu", "360主动防御"},
            {"usysdiag", "火绒安全"}, {"hipstray", "火绒安全"}, {"hipsdaemon", "火绒安全"},
            {"qax", "奇安信天擎"}, {"tianqing", "奇安信天擎"}, {"kxetray", "金山/猎鹰"}, {"kxescore", "金山/猎鹰"},
            {"qqpctray", "腾讯电脑管家"}, {"qqpcrtp", "腾讯电脑管家"},
            {"sangfor", "深信服EDR"}, {"edr", "疑似EDR(深信服/其他)"}, {"yunsuo", "云锁"},
            {"csfalcon", "CrowdStrike Falcon"}, {"ccsvchst", "Symantec Endpoint"},
            {"ekrn", "ESET"}, {"mcshield", "McAfee"}, {"msmpeng", "Windows Defender"},
            {"sentinelone", "SentinelOne"}, {"cyoptics", "Cylance"},
            {"sophos", "Sophos"}, {"tmccsf", "Trend Micro"}, {"ntrtscan", "Trend Micro"},
            {"avp", "Kaspersky"}, {"bdagent", "Bitdefender"}, {"avastui", "Avast"}
        };
        List<String> hits = new ArrayList<>();
        try {
            ProcessHandle.allProcesses().forEach(ph -> {
                String cmd = ph.info().command().orElse("");
                if (cmd.isBlank()) {
                    return;
                }
                String base = cmd.replace('\\', '/');
                int i = base.lastIndexOf('/');
                if (i >= 0) {
                    base = base.substring(i + 1);
                }
                String lower = base.toLowerCase(Locale.ROOT);
                for (String[] s : sigs) {
                    if (lower.contains(s[0])) {
                        String label = s[1] + "(" + base + ")";
                        if (!hits.contains(label)) {
                            hits.add(label);
                        }
                    }
                }
            });
        } catch (RuntimeException ignore) {
            // 权限不足等忽略：仅作为诊断提示，不影响主流程。
        }
        return hits;
    }

    /**
     * 把随包 JDK 的 VC++ 运行库（msvcp140.dll / vcruntime140.dll / vcruntime140_1.dll）复制到 mysqld 同目录，
     * 实现 app-local 部署。Windows 的 DLL 搜索顺序里「exe 所在目录」优先于 System32，故可保证 mysqld 始终使用
     * 与随包 JDK 一致的较新版本，规避目标机 System32 中过旧 msvcp140 导致的 0xC0000005。若 JDK 未提供某 DLL 则跳过。
     */
    private static void ensureVcRuntimeLocal(Path mysqlBin, Map<String, Path> homes, Consumer<String> log) {
        if (mysqlBin == null || !Files.isDirectory(mysqlBin)) {
            return;
        }
        // 注意：homes 的 key 是压缩包文件名（如 jdk-27_windows-x64_bin.zip），并非 "jdk"，
        // 因此不能 homes.get("jdk")（恒为 null）。用关键词匹配定位 JDK home（与 findHome 占位符逻辑一致）。
        Path jdkHome = (homes == null) ? null : findHome(homes, "jdk");
        Path jdkBin = (jdkHome != null) ? jdkHome.resolve("bin") : null;
        if (jdkBin == null || !Files.isDirectory(jdkBin)) {
            log.accept("  [VC运行库] 未找到随包 JDK 的 bin，跳过 app-local VC 运行库部署（若初始化崩溃请手动放置 msvcp140.dll）");
            return;
        }
        String[] dlls = {"msvcp140.dll", "vcruntime140.dll", "vcruntime140_1.dll"};
        boolean any = false;
        for (String d : dlls) {
            Path src = jdkBin.resolve(d);
            Path dst = mysqlBin.resolve(d);
            try {
                if (!Files.isRegularFile(src)) {
                    continue;
                }
                if (Files.isRegularFile(dst) && Files.size(dst) == Files.size(src)) {
                    continue;
                }
                Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                log.accept("  [VC运行库] 已部署 app-local: " + d + " -> " + dst);
                any = true;
            } catch (IOException ex) {
                log.accept("  [VC运行库] 复制 " + d + " 失败: " + ex.getMessage());
            }
        }
        if (!any) {
            log.accept("  [VC运行库] app-local VC 运行库已就位（msvcp140/vcruntime140/vcruntime140_1）");
        }
    }

    /** 最佳努力判断当前进程是否以管理员（提升）权限运行：{@code net session} 仅在高完整性级别下返回 0。 */
    private static boolean isElevated() {
        try {
            Process p = new ProcessBuilder("net", "session").redirectErrorStream(true).start();
            try (InputStream in = p.getInputStream()) {
                in.readAllBytes();
            }
            return p.waitFor() == 0;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * 自动从 Windows 事件日志抓取最近的「应用程序错误(事件1000)」中与 mysqld 相关的记录，并回显。
     * 这是「mysqld 在写日志前就 0xC0000005 崩溃」时唯一能看到真因的途径——记录里含
     * 「Faulting application name / Faulting module name / Exception code」，据此可判定是哪个 DLL/模块导致崩溃。
     * 用 wevtutil（只读查询）；若被策略禁用或无记录则优雅跳过。
     */
    private static void dumpRecentAppErrorEvents(Consumer<String> log) {
        try {
            ProcessBuilder pb = new ProcessBuilder("wevtutil", "qe", "Application",
                    "/q:*[System[(EventID=1000)]]", "/c:10", "/rd:true", "/f:text");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            Charset cs = Charset.forName(System.getProperty("native.encoding", "GBK"));
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), cs))) {
                String l;
                while ((l = r.readLine()) != null) {
                    sb.append(l).append('\n');
                }
            }
            p.waitFor();
            String t = sb.toString().trim();
            if (t.isEmpty()) {
                return;
            }
            boolean any = false;
            for (String chunk : t.split("(?m)^Event\\[")) {
                if (chunk.toLowerCase(Locale.ROOT).contains("mysqld")) {
                    if (!any) {
                        log.accept("  ---- Windows 应用程序错误(事件1000) 中与 mysqld 相关的记录（关注『故障模块名称』） ----");
                        any = true;
                    }
                    for (String line : chunk.split("\\R")) {
                        String s = line.trim();
                        if (!s.isEmpty()) {
                            log.accept("    | " + s);
                        }
                    }
                    log.accept("    ----------------------------------------------");
                }
            }
            if (!any) {
                log.accept("  (事件日志中暂未找到 mysqld 的事件1000 记录；可稍后用事件查看器查看『应用程序错误』的『故障模块名称』)");
            }
        } catch (Exception ex) {
            log.accept("  (自动读取事件日志失败: " + ex.getMessage() + "；可手动用事件查看器查看“应用程序错误”(事件1000) 的『故障模块名称』)");
        }
    }

    /** 含空格（或为空）的字符串用双引号括起（Windows 命令行惯例）；否则原样返回。 */
    private static String quote(String s) {
        if (s == null || s.isEmpty()) {
            return "\"\"";
        }
        return (s.indexOf(' ') >= 0 || s.indexOf('\t') >= 0) ? "\"" + s + "\"" : s;
    }

    /**
     * 把「程序 + 参数」拼成一条命令行交给 {@code cmd /c} 执行。
     * <p>{@code cmd /c} 对「以引号开头」的命令行有特殊解析规则，需在最外层再包一对引号：{@code ""prog" args"}，
     * 否则带空格的程序路径会被误拆（报 {@code 'D:\my' 不是内部或外部命令}）。
     */
    private static String cmdWrap(String progAndArgs) {
        return "\"" + progAndArgs + "\"";
    }

    /** 回显指定日志文件的末尾内容（用于 mysqld 显式 --log-error 文件；不存在/为空则提示）。 */
    private static void logTail(Path file, Consumer<String> log, String title) {
        String tail = readTail(file, 4000);
        if (tail == null) {
            log.accept("  ---- " + title + "：不存在或为空（进程可能在打开日志文件前就崩溃了） ----");
            return;
        }
        log.accept("  ---- " + title + "（末尾） ----");
        for (String line : tail.split("\\R")) {
            if (!line.isBlank()) {
                log.accept("    | " + line);
            }
        }
        log.accept("  --------------------------------");
    }

    private static String readTail(Path file, int max) {
        try {
            if (!Files.isRegularFile(file)) {
                return null;
            }
            byte[] all = Files.readAllBytes(file);
            if (all.length == 0) {
                return null;
            }
            Charset cs = Charset.forName(System.getProperty("native.encoding", "GBK"));
            String t = new String(all, cs);
            return t.length() > max ? "…(仅末尾 " + max + " 字符)…\n" + t.substring(t.length() - max) : t;
        } catch (IOException ex) {
            return null;
        }
    }

    /** 读取 MySQL data 目录下的 {@code *.err} 错误日志末尾若干字符，用于失败时回显（无则返回 null）。 */
    private static String readMysqlErr(Path dataDir) {
        try (Stream<Path> s = Files.list(dataDir)) {
            Path err = s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".err"))
                    .findFirst().orElse(null);
            if (err == null) {
                return null;
            }
            Charset cs = Charset.forName(System.getProperty("native.encoding", "GBK"));
            String t = new String(Files.readAllBytes(err), cs);
            int max = 4000;
            return t.length() > max ? "…(仅显示末尾 " + max + " 字符)…\n" + t.substring(t.length() - max) : t;
        } catch (IOException ex) {
            return null;
        }
    }

    /**
     * 便携模式（portable）下 MySQL 由安装器以 {@code --initialize-insecure} 初始化，root 为空密码。
     * 工程内 {@code *.properties} 若硬编码了非空 jdbc.password 会导致连接失败，故统一对齐为空密码。
     * 仅改写 jdbc.password 行本身，保留文件编码（UTF-8）与换行（CRLF）。
     */
    static void alignJdbcPassword(Path projectRoot, Consumer<String> log) throws IOException {
        try (Stream<Path> s = Files.walk(projectRoot)) {
            List<Path> props = s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".properties"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/target/"))
                    .toList();
            for (Path p : props) {
                String text = Files.readString(p, StandardCharsets.UTF_8);
                if (!text.contains("jdbc.password")) {
                    continue;
                }
                String updated = text.replaceAll("(?m)^[\\t ]*jdbc\\.password[\\t ]*=.*$", "jdbc.password=");
                if (!updated.equals(text)) {
                    Files.writeString(p, updated, StandardCharsets.UTF_8);
                    log.accept("    [便携对齐] 清空 " + projectRoot.relativize(p) + " 的 jdbc.password");
                }
            }
        }
    }

    /**
     * 在释放出的工作区根写 {@code .mvn/maven.config}，把 {@code maven.repo.local} 指向随安装包分发的
     * 离线仓库（{@code <安装目录>/maven-repo}）。这样在目标机无网络时，工作区（含各子模块）仍可用
     * Maven（含 IDEA 内置 Maven）构建并运行测试，无需从公网下载依赖。
     * <p>{@code maven.config} 每行作为「一个完整参数」整体传入（Maven 不做引号解析），
     * 故路径含空格时也不能加引号——加了引号会被当成路径的一部分，导致本地仓库路径非法。
     */
    static void writeMavenConfig(Path workspaceRoot, Path repoDir, Consumer<String> log) throws IOException {
        Path mvnDir = workspaceRoot.resolve(".mvn");
        Files.createDirectories(mvnDir);
        Path cfg = mvnDir.resolve("maven.config");
        String repo = slash(repoDir);
        // 两行参数（每行一个完整参数，Maven 不剥引号，含空格路径也不能加引号）：
        //   ① -o               —— 强制离线：绝不访问任何远程仓库（含 repo.maven.apache.org）。
        //                        仅此一项即可杜绝「本地仓库已指向随包 repo、却仍去公网抓依赖/插件」的问题；
        //                        缺了它，一旦某个依赖/插件/元数据不在本地库，Maven 仍会联网解析。
        //   ② -Dmaven.repo.local=  —— 本地仓库指向随包离线仓库。
        String content = "-o\r\n" + "-Dmaven.repo.local=" + repo + "\r\n";
        Files.writeString(cfg, content, StandardCharsets.UTF_8);
        log.accept("  已配置离线 Maven: -o -Dmaven.repo.local=" + repo);
        log.accept("    -> " + cfg);
    }

    /**
     * 双保险方案（B 线）：<b>彻底移除</b> Maven Wrapper，让 IDEA 回落到随包 apache-maven（CUSTOM）。
     * <p>背景：IDEA 一旦检测到 {@code .mvn/wrapper/maven-wrapper.properties}，就会把「Maven 主路径」
     * 强制切成「Use Maven wrapper」，并覆盖安装器写入 {@code workspace.xml} 的 CUSTOM（随包 apache-maven）
     * 配置；同时 wrapper 会解压随包 maven 发行版 zip 内<b>未离线</b>的 {@code conf/settings.xml}
     * （{@code offline=false}），一旦用户级 settings 未生效便会回落到 {@code ~/.m2} 并访问公网。
     * <p>故此处删除历史遗留的 {@code .mvn/wrapper}（含 properties 与 maven-dist.zip）及 {@code mvnw} 脚本，
     * 使 IDEA 使用 {@code workspace.xml} 的 CUSTOM（随包 apache-maven，其 {@code conf/settings.xml} 已写入
     * localRepository + offline + file:// 镜像）；Maven Home 显示随包路径、且全程离线。
     */
    private static void writeMavenWrapper(Path workspaceRoot, Map<String, Path> homes, Path softwaresSrc,
                                          Consumer<String> log) throws IOException {
        Path mavenHome = findHome(homes, "apache-maven");
        if (mavenHome == null) {
            log.accept("  [跳过] 未找到随包 apache-maven，未能固定 IDEA Maven 主路径");
            return;
        }
        Path wrapperDir = workspaceRoot.resolve(".mvn").resolve("wrapper");
        deleteDir(wrapperDir);
        Files.deleteIfExists(workspaceRoot.resolve("mvnw"));
        Files.deleteIfExists(workspaceRoot.resolve("mvnw.cmd"));
        log.accept("  已移除 Maven Wrapper（.mvn/wrapper + mvnw*），IDEA 将使用随包 Maven（CUSTOM）");
        log.accept("    随包 Maven: " + mavenHome.toAbsolutePath().normalize());
    }

    /**
     * 把随包分发的独立 Maven（apache-maven-*）的 {@code conf/settings.xml} 里的 {@code <localRepository>}
     * 由缺省（{@code ${user.home}/.m2/repository}）改为随安装包释放到安装目录的离线仓库
     * （{@code <安装目录>/maven-repo}）。这样用该 Maven（命令行 {@code mvn}）构建时，无论从哪个目录运行
     * 都使用随包携带的离线仓库，不再回退到用户缺省的 {@code ~/.m2}，无网也能构建/测试。
     * <p>仅新增/替换「生效的」{@code <localRepository>} 行（注释块内的示例行保持不动）；幂等：已存在则替换。
     */
    static void configureMavenLocalRepo(Map<String, Path> homes, Path repoDir, Consumer<String> log) throws IOException {
        Path mavenHome = findHome(homes, "apache-maven");
        if (mavenHome == null) {
            log.accept("  [跳过] 未找到随包的独立 Maven（apache-maven）");
            return;
        }
        Path settings = mavenHome.resolve("conf").resolve("settings.xml");
        if (!Files.isRegularFile(settings)) {
            log.accept("  [跳过] 未找到 " + settings);
            return;
        }
        String repo = slash(repoDir);
        applyLocalRepositoryToSettings(settings, repo);
        applyOfflineToSettings(settings, true);
        applyPackagedMirrorToSettings(settings, repo);
        log.accept("  配置独立 Maven 本地仓库 & 离线(含 file:// 镜像): <localRepository>" + repo + "</localRepository> ; offline=true");
        log.accept("    -> " + settings);
    }

    /**
     * 把离线仓库写入 IDEA「内置（bundled）Maven」的 {@code conf/settings.xml}。
     * <p>背景：在 IDEA 中打开工作区时，IDEA 默认用其自带 Maven，用该 Maven 的
     * {@code plugins/maven-plugin/lib/maven3/conf/settings.xml} 作为全局 settings；而安装器此前只把随包的
     * <b>独立</b> apache-maven 的 settings.xml 指向了离线仓库，IDEA 并不读它 → IDEA 依赖解析仍回落到
     * {@code ~/.m2}，表现为「离线仓库没生效」。写到这里，IDEA 的内置 Maven 才会把本地仓库指向离线仓库。
     */
    static void configureIdeaBundledMavenRepo(Path ideaHome, Path repoDir, Consumer<String> log) {
        if (ideaHome == null || !Files.isDirectory(ideaHome)) {
            return;
        }
        Path settings = null;
        try (Stream<Path> s = Files.walk(ideaHome, 8)) {
            settings = s.filter(Files::isRegularFile)
                    .filter(p -> slash(p).toLowerCase(Locale.ROOT).endsWith("/maven3/conf/settings.xml"))
                    .findFirst().orElse(null);
        } catch (IOException ex) {
            settings = null;
        }
        if (settings == null) {
            log.accept("  [跳过] 未找到 IDEA 内置 Maven 的 settings.xml（命令行 mvn 仍可用离线仓库）");
            return;
        }
        String repo = slash(repoDir);
        try {
            applyLocalRepositoryToSettings(settings, repo);
            applyOfflineToSettings(settings, true);
            applyPackagedMirrorToSettings(settings, repo);
            log.accept("  配置 IDEA 内置 Maven 本地仓库 & 离线(含 file:// 镜像): <localRepository>" + repo + "</localRepository> ; offline=true");
            log.accept("    -> " + settings);
        } catch (IOException ex) {
            log.accept("  [警告] 写入 IDEA 内置 Maven settings.xml 失败: " + ex.getMessage());
        }
    }

    /** 把生效的 {@code <localRepository>} 写入指定 settings.xml（通用委托）。 */
    static void applyLocalRepositoryToSettings(Path settings, String repo) throws IOException {
        applySettingToSettings(settings, "localRepository", repo);
    }

    /** 把 {@code <offline>true</offline>} 写入指定 settings.xml（让 Maven 彻底离线，绝不再访问远程仓库）。 */
    static void applyOfflineToSettings(Path settings, boolean offline) throws IOException {
        applySettingToSettings(settings, "offline", String.valueOf(offline));
    }

    /**
     * 把随包离线仓库以 {@code <mirror><mirrorOf>*</mirrorOf></mirror>} 的形式写入指定 settings.xml 的
     * {@code <mirrors>} 段（{@code file://} 本地地址）。
     * <p>与 {@code <offline>true</offline>} 形成双保险：即便有人把离线开关关掉，所有仓库（含 central）的
     * 请求也只会打到本地文件仓库，绝不访问 {@code repo.maven.apache.org}。幂等：已存在 packaged-offline 则跳过。
     */
    static void applyPackagedMirrorToSettings(Path settings, String repo) throws IOException {
        String xml = Files.readString(settings, StandardCharsets.UTF_8);
        if (xml.contains("<id>packaged-offline</id>")) {
            return;
        }
        String fileUrl = "file:///" + repo.replace(" ", "%20");
        List<String> lines = new ArrayList<>(Arrays.asList(xml.replace("\r\n", "\n").split("\n", -1)));
        int mirrorsIdx = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().equals("<mirrors>")) {
                mirrorsIdx = i;
                break;
            }
        }
        List<String> block = Arrays.asList(
                "    <mirror>",
                "      <id>packaged-offline</id>",
                "      <mirrorOf>*</mirrorOf>",
                "      <url>" + x(fileUrl) + "</url>",
                "    </mirror>");
        if (mirrorsIdx >= 0) {
            lines.addAll(mirrorsIdx + 1, block);
        } else {
            int settingsClose = -1;
            for (int i = lines.size() - 1; i >= 0; i--) {
                if (lines.get(i).trim().startsWith("</settings")) {
                    settingsClose = i;
                    break;
                }
            }
            if (settingsClose < 0) {
                throw new IOException("settings.xml 结构异常（未找到 </settings>）: " + settings);
            }
            List<String> wrapped = new ArrayList<>();
            wrapped.add("  <mirrors>");
            wrapped.addAll(block);
            wrapped.add("  </mirrors>");
            lines.addAll(settingsClose, wrapped);
        }
        Files.writeString(settings, String.join("\r\n", lines), StandardCharsets.UTF_8);
    }

    /**
     * 往指定 settings.xml 写入一个顶层单值元素（如 localRepository / offline）：
     * 剔除已有的「生效」行（注释块内的示例行保留），在 {@code <settings ...>} 开标签闭合之后插入一行新值。
     * 幂等，可重复执行。
     */
    static void applySettingToSettings(Path settings, String tagName, String value) throws IOException {
        List<String> lines = new ArrayList<>(Arrays.asList(
                Files.readString(settings, StandardCharsets.UTF_8).replace("\r\n", "\n").split("\n", -1)));
        String openTag = "<" + tagName + ">";
        String closeTag = "</" + tagName + ">";
        // 丢弃已有的「生效」同名元素行（注释块内的示例行因处于注释态而保留）。
        List<String> cleaned = new ArrayList<>();
        boolean inComment = false;
        for (String ln : lines) {
            String t = ln.trim();
            if (!inComment && t.startsWith(openTag) && t.endsWith(closeTag)) {
                continue;
            }
            cleaned.add(ln);
            if (!inComment && ln.contains("<!--")) {
                inComment = true;
            }
            if (inComment && ln.contains("-->")) {
                inComment = false;
            }
        }
        // 在 <settings ...> 开标签（可能跨多行）闭合之后插入目标元素。
        int after = -1;
        boolean seenSettings = false;
        for (int i = 0; i < cleaned.size(); i++) {
            String t = cleaned.get(i).trim();
            if (!seenSettings && t.startsWith("<settings")) {
                seenSettings = true;
            }
            if (seenSettings && t.endsWith(">")) {
                after = i;
                break;
            }
        }
        if (after < 0) {
            throw new IOException("settings.xml 结构异常（未找到 <settings> 开标签）: " + settings);
        }
        cleaned.add(after + 1, "  " + openTag + x(value) + closeTag);
        Files.writeString(settings, String.join("\r\n", cleaned), StandardCharsets.UTF_8);
    }

    /**
     * 把离线仓库 & 离线模式写入「默认用户 Maven 设置」{@code ~/.m2/settings.xml}。
     * <p>关键根因：IDEA 的「用户设置文件」Override 未勾选时，IDEA 使用 <b>默认位置</b>的
     * {@code ~/.m2/settings.xml}；而用户级 settings 的 {@code <localRepository>} 优先级<b>高于</b>
     * Maven 自带 {@code conf/settings.xml}（全局 settings）。因此即便把 Maven 主路径改成随包 Maven，
     * 也会被 {@code ~/.m2/settings.xml} 覆盖而回落到默认 {@code ~/.m2/repository}——这正是
     * 「依赖仍从 https://repo.maven.apache.org 下载、没走本地库」的根因。
     * 写入此文件后，无论用随包 Maven、IDEA 内置 Maven 还是 wrapper，都会使用随包离线仓库并强制离线，
     * 彻底不再访问公网。
     */
    static void configureMavenUserSettings(Path repoDir, Consumer<String> log) {
        try {
            // 目标固定为「当前用户」的 ~/.m2/settings.xml（Windows 即 C:\Users\<用户>\.m2\settings.xml）。
            // 这正是 IDEA「用户设置文件」Override 未勾选时使用的<b>默认位置</b>。
            String home = System.getProperty("user.home");
            if (home == null || home.isBlank()) {
                home = System.getenv("USERPROFILE");
            }
            if (home == null || home.isBlank()) {
                log.accept("  [跳过] 无法确定用户主目录，未配置默认用户 Maven 设置");
                return;
            }
            Path m2 = Path.of(home, ".m2");
            Files.createDirectories(m2);
            Path settings = m2.resolve("settings.xml");
            String repo = slash(repoDir);
            if (!Files.isRegularFile(settings)) {
                Files.writeString(settings, packagedOfflineSettingsXml(repo), StandardCharsets.UTF_8);
                log.accept("  已创建默认用户 Maven 设置（IDEA『用户设置文件』未勾选重写时即用它）: " + settings);
            } else {
                try {
                    applyLocalRepositoryToSettings(settings, repo);
                    applyOfflineToSettings(settings, true);
                    log.accept("  已更新默认用户 Maven 设置: " + settings);
                } catch (IOException bad) {
                    // 目标机 ~/.m2/settings.xml 常是历史遗留、结构异常（这正是「内容也不对」的来源）；
                    // 无法安全插入时直接重写为干净的离线配置，避免坏文件继续让 Maven 走公网仓库。
                    Files.writeString(settings, packagedOfflineSettingsXml(repo), StandardCharsets.UTF_8);
                    log.accept("  [修正] 原默认用户 Maven 设置无法安全解析，已重写为离线配置: " + settings);
                }
            }
            log.accept("    localRepository=" + repo + " ; offline=true");
        } catch (Exception ex) {
            log.accept("  [警告] 配置默认用户 Maven 设置失败: " + ex.getMessage());
        }
    }

    /**
     * 让释放出的工作区在 IDEA 中使用随包离线 Maven 仓库（Bug7 根因修复）。
     *
     * <p>背景：IDEA 里「用户设置文件」「本地仓库」若不勾选「重写（Override）」，就用缺省位置的
     * {@code ~/.m2/settings.xml}；而用户级 settings 的 {@code <localRepository>} 优先级<b>高于</b>
     * Maven 自带 {@code conf/settings.xml}。目标机上的 {@code ~/.m2/settings.xml} 往往是历史遗留、
     * 指向一个不存在的仓库路径，导致依赖解析回落到 {@code ~/.m2/repository} 并从
     * {@code repo.maven.apache.org} 下载，即使把 Maven 主路径改成随包 Maven 也无效。
     *
     * <p>修复：直接在工程级 IDEA 设置里显式重写（override）这三项，指向随包离线仓库：
     * <ul>
     *   <li>{@code userSettingsFile} —— 安装目录内生成的 {@code maven-settings.xml}（等价「勾选 Override 并指向它」）；</li>
     *   <li>{@code localRepository} —— {@code <安装目录>/maven-repo}（等价「勾选本体仓库重写」）；</li>
     *   <li>{@code useMavenConfig=true}、{@code workOffline=true} —— 启用 {@code .mvn/maven.config} 并强制离线。</li>
     * </ul>
     * 落点：IDEA 2026 起该状态为 project 级，{@code @State(name="MavenImportPreferences",
     * storages=@Storage("$WORKSPACE_FILE$"))}，即工程根 {@code .idea/workspace.xml} 的
     * {@code <component name="MavenImportPreferences">}；XML option 名由 getter 推导（{@code getUserSettingsFile}
     * -> {@code userSettingsFile}、{@code getLocalRepository} -> {@code localRepository} 等）。
     *
     * @param workspaceRoot 工作区（聚合工程）根（其 {@code .idea} 由 IDEA 识别）
     * @param installDir    安装目录（在其下生成 {@code maven-settings.xml}）
     * @param repoDir       随包释放的离线仓库目录
     * @param mavenHome     随包 apache-maven 主目录（用于把 IDEA「Maven 主路径」固定为 CUSTOM，避免回落到
     *                      「Use Maven wrapper」按公网 distributionUrl 下载 Maven 本身；为 {@code null} 时跳过该项）
     */
    static void configureIdeaMavenWorkspaceSettings(Path workspaceRoot, Path installDir, Path repoDir,
                                                    Path mavenHome, Consumer<String> log) throws IOException {
        String repo = slash(repoDir);
        // 1) 安装目录内生成/更新专用 user settings（不覆盖 ~/.m2，避免污染用户 profile）。
        Path settings = installDir.resolve("maven-settings.xml");
        writeMavenUserSettingsFile(settings, repo);
        log.accept("  已生成离线 Maven 用户设置: " + settings);
        // 2) 写工程级 IDEA 设置：.idea/workspace.xml 的 MavenImportPreferences。
        Path ws = workspaceRoot.resolve(".idea").resolve("workspace.xml");
        String component = buildMavenImportPreferencesXml(settings, repo, mavenHome);
        upsertWorkspaceComponent(ws, "MavenImportPreferences", component);
        log.accept("  已写入 IDEA Maven 设置（workspace.xml → MavenImportPreferences）");
        log.accept("    userSettingsFile=" + slash(settings));
        log.accept("    localRepository=" + repo + " ; useMavenConfig=true ; workOffline=true");
        if (mavenHome != null) {
            log.accept("    mavenHomeTypeForPersistence=CUSTOM ; customMavenHome="
                    + mavenHome.toAbsolutePath().normalize());
        }
    }

    /** 生成/更新一份 user settings：{@code <localRepository>} + {@code <offline>true}。幂等（存在则改写）。 */
    private static void writeMavenUserSettingsFile(Path settings, String repo) throws IOException {
        Files.createDirectories(settings.getParent());
        if (!Files.isRegularFile(settings)) {
            Files.writeString(settings, packagedOfflineSettingsXml(repo), StandardCharsets.UTF_8);
        } else {
            try {
                applyLocalRepositoryToSettings(settings, repo);
                applyOfflineToSettings(settings, true);
            } catch (IOException ex) {
                // 既有文件结构异常、无法安全插入时，直接重写为干净的离线配置，避免坏 settings 继续生效。
                Files.writeString(settings, packagedOfflineSettingsXml(repo), StandardCharsets.UTF_8);
            }
        }
    }

    /**
     * 生成一份完整的「随包离线」Maven user settings：
     * <ul>
     *   <li>{@code <localRepository>} 指向随包离线仓库；</li>
     *   <li>{@code <offline>true</offline>} 强制离线，绝不访问远程仓库；</li>
     *   <li>{@code <mirrors>} 把 {@code *} 全部镜像到随包离线仓库（{@code file://}），
     *       即便有人把离线开关关掉，central 请求也只会打到本地文件仓库、不会联网 repo.maven.apache.org。</li>
     * </ul>
     */
    private static String packagedOfflineSettingsXml(String repo) {
        String fileUrl = "file:///" + repo.replace(" ", "%20");
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
                + "<settings xmlns=\"http://maven.apache.org/SETTINGS/1.0.0\"\r\n"
                + "          xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\r\n"
                + "          xsi:schemaLocation=\"http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd\">\r\n"
                + "  <localRepository>" + x(repo) + "</localRepository>\r\n"
                + "  <offline>true</offline>\r\n"
                + "  <mirrors>\r\n"
                + "    <mirror>\r\n"
                + "      <id>packaged-offline</id>\r\n"
                + "      <mirrorOf>*</mirrorOf>\r\n"
                + "      <url>" + x(fileUrl) + "</url>\r\n"
                + "    </mirror>\r\n"
                + "  </mirrors>\r\n"
                + "</settings>\r\n";
    }

    /** 构造 {@code <component name="MavenImportPreferences">} 块（字段名取自 IDEA getter 推导的真实 option 名）。 */
    private static String buildMavenImportPreferencesXml(Path settings, String repo, Path mavenHome) {
        String s = slash(settings);
        String homeXml = "";
        if (mavenHome != null) {
            String custom = mavenHome.toAbsolutePath().normalize().toString();
            homeXml = "        <option name=\"customMavenHome\" value=\"" + x(custom) + "\" />\n"
                    + "        <option name=\"mavenHomeTypeForPersistence\" value=\"CUSTOM\" />\n";
        }
        return "  <component name=\"MavenImportPreferences\">\n"
                + "    <option name=\"generalSettings\">\n"
                + "      <MavenGeneralSettings>\n"
                + "        <option name=\"userSettingsFile\" value=\"" + x(s) + "\" />\n"
                + "        <option name=\"localRepository\" value=\"" + x(repo) + "\" />\n"
                + "        <option name=\"useMavenConfig\" value=\"true\" />\n"
                + "        <option name=\"workOffline\" value=\"true\" />\n"
                + homeXml
                + "      </MavenGeneralSettings>\n"
                + "    </option>\n"
                + "  </component>";
    }

    /**
     * 在 {@code workspace.xml} 中插入或替换指定 {@code <component>} 块。
     * <p>文件不存在则新建最小工程骨架 {@code <project version="4">}；已存在同名 component 则整块替换
     * （保证幂等、不重复插入）；否则插入到 {@code </project>} 之前。
     */
    private static void upsertWorkspaceComponent(Path ws, String name, String component) throws IOException {
        if (!Files.isRegularFile(ws)) {
            Files.createDirectories(ws.getParent());
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<project version=\"4\">\n"
                    + component + "\n</project>\n";
            Files.writeString(ws, xml, StandardCharsets.UTF_8);
            return;
        }
        String xml = Files.readString(ws, StandardCharsets.UTF_8);
        String regex = "(?s)<component name=\"" + Pattern.quote(name) + "\">.*?</component>";
        if (Pattern.compile(regex).matcher(xml).find()) {
            xml = xml.replaceAll(regex, Matcher.quoteReplacement(component));
        } else {
            int idx = xml.lastIndexOf("</project>");
            if (idx < 0) {
                throw new IOException("workspace.xml 结构异常（缺少 </project>）: " + ws);
            }
            xml = xml.substring(0, idx) + component + "\n" + xml.substring(idx);
        }
        Files.writeString(ws, xml, StandardCharsets.UTF_8);
    }

    /**
     * 把随包分发的「本体应用构建」SKILL（{@code skills/ontology-app-builder/}）投放到释放出的工作区根的
     * {@code .codeartsdoer/skills/ontology-app-builder/}。码道（CodeArts）按项目级技能约定扫描打开的工程根下该目录，
     * 投放后客户在 IDEA 中打开工作区即自动识别该技能，无需手工导入。缺失来源目录时跳过（保持对其他分发包形态的通用性）。
     */
    static void installAppBuilderSkill(Path resHome, Path workspaceRoot, Consumer<String> log) throws IOException {
        Path src = resHome.resolve(SKILL_SRC_DIR);
        if (!Files.isDirectory(src)) {
            log.accept("  [跳过] 未找到本体应用构建技能: " + src);
            return;
        }
        Path dest = workspaceRoot.resolve(SKILL_DEPLOY_DIR);
        log.accept("  投放本体应用构建技能 -> " + dest);
        copyDir(src, dest);
        log.accept("    -> " + dest);
    }

    /** 组装 application.yaml 模板占位符 -> 实际路径（指向安装目录下解压出的软件根目录）。 */
    private static Map<String, String> buildPlaceholders(Map<String, Path> homes) {
        Map<String, String> ph = new LinkedHashMap<>();
        put(ph, "@{JAVA_HOME}", findHome(homes, "jdk"));
        put(ph, "@{ONTOP_HOME}", findHome(homes, "ontop"));
        put(ph, "@{CAMUNDA_HOME}", findHome(homes, "camunda8", "c8run"));
        put(ph, "@{RABBITMQ_HOME}", findHome(homes, "rabbitmq"));
        put(ph, "@{ERLANG_HOME}", findHome(homes, "otp"));
        Path mysql = findMysqlHome(homes);
        put(ph, "@{MYSQL_HOME}", mysql);
        ph.put("@{MYSQL_BIN}", mysql == null ? "" : mysql.resolve("bin").resolve("mysql.exe").toString());
        ph.put("@{MYSQL_DATADIR}", mysql == null ? "" : mysql.resolve("data").toString());
        ph.put("@{MYSQL_PORT}", String.valueOf(MYSQL_PORT));
        return ph;
    }

    private static void put(Map<String, String> m, String key, Path value) {
        m.put(key, value == null ? "" : value.toString());
    }

    private static Path findMysqlHome(Map<String, Path> homes) {
        return findHomeExcluding(homes, new String[]{"shell", "workbench"}, "mysql");
    }

    private static Path findHome(Map<String, Path> homes, String... anyKeywords) {
        return findHomeExcluding(homes, null, anyKeywords);
    }

    private static Path findHomeExcluding(Map<String, Path> homes, String[] excludes, String... anyKeywords) {
        for (Map.Entry<String, Path> e : homes.entrySet()) {
            String n = e.getKey().toLowerCase(Locale.ROOT);
            boolean any = false;
            for (String k : anyKeywords) {
                if (n.contains(k)) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                continue;
            }
            if (excludes != null) {
                boolean ex = false;
                for (String x : excludes) {
                    if (n.contains(x)) {
                        ex = true;
                        break;
                    }
                }
                if (ex) {
                    continue;
                }
            }
            return e.getValue();
        }
        return null;
    }

    /**
     * 修正「带包装目录」的压缩包 home：部分 vendor 包（如 JDK 的 {@code jdk-27_windows-x64_bin/jdk-27/bin/java.exe}）
     * 在统一顶层目录之下还多套了一层真实根目录，使 {@code commonTopDir} 得到的是包装目录而非软件根。
     * 此处按包名关键字找到对应 home，若其未直接含 {@code markerRel}，则下探（最多 {@code maxDepth} 层）
     * 定位真正含特征文件的目录并回写，避免 {@code JAVA_HOME} 等占位符与 IDEA SDK 路径指错。
     */
    private static void correctNestedHome(Map<String, Path> homes, String keyword, String markerRel, Consumer<String> log) {
        for (Map.Entry<String, Path> e : homes.entrySet()) {
            if (!e.getKey().toLowerCase(Locale.ROOT).contains(keyword)) {
                continue;
            }
            Path home = e.getValue();
            if (Files.isRegularFile(home.resolve(markerRel))) {
                return;
            }
            Path real = findDirWithMarker(home, markerRel, 4);
            if (real != null && !real.equals(home)) {
                e.setValue(real);
                log.accept("    [修正] " + keyword + " home -> " + real + "（压缩包含包装目录）");
            }
            return;
        }
    }

    /** 在 {@code root}（含自身，最多 {@code maxDepth} 层）下查找直接包含 {@code markerRel} 的目录；找不到返回 null。 */
    private static Path findDirWithMarker(Path root, String markerRel, int maxDepth) {
        if (!Files.isDirectory(root)) {
            return null;
        }
        try (Stream<Path> s = Files.walk(root, maxDepth)) {
            return s.filter(Files::isDirectory)
                    .filter(d -> Files.isRegularFile(d.resolve(markerRel)))
                    .findFirst()
                    .orElse(null);
        } catch (IOException ex) {
            return null;
        }
    }

    /**
     * 直接用 {@code ProcessBuilder} 以「参数数组」方式启动 mysqld（不经 cmd /c、不做任何手工引号拼接），
     * 由 Windows CreateProcess 按参数边界正确加引号，从而支持含空格的 basedir/datadir 路径；
     * 同时把子进程 stdout+stderr 重定向到 {@code captureFile}，进程结束后回显——即使 mysqld 早期崩溃，
     * 也能看到它在崩溃前已 flush 的输出，便于定位（旧实现用管道，崩溃时常丢失全部输出）。
     */
    private static int runMysqldCaptured(List<String> argv, Path cwd, Path captureFile, Consumer<String> log)
            throws IOException, InterruptedException {
        return runMysqldCaptured(argv, cwd, captureFile, log, Map.of());
    }

    private static int runMysqldCaptured(List<String> argv, Path cwd, Path captureFile, Consumer<String> log,
            Map<String, String> extraEnv) throws IOException, InterruptedException {
        try {
            Files.deleteIfExists(captureFile);
        } catch (IOException ignore) {
            // 忽略：仅日志捕获文件
        }
        ProcessBuilder pb = new ProcessBuilder(argv);
        if (cwd != null && Files.isDirectory(cwd)) {
            pb.directory(cwd.toFile());
        }
        if (extraEnv != null && !extraEnv.isEmpty()) {
            pb.environment().putAll(extraEnv);
        }
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.to(captureFile.toFile()));
        Process p = pb.start();
        int exit = p.waitFor();
        try {
            if (Files.isRegularFile(captureFile)) {
                Charset cs = Charset.forName(System.getProperty("native.encoding", "GBK"));
                String t = new String(Files.readAllBytes(captureFile), cs);
                for (String line : t.split("\\R")) {
                    if (!line.isBlank()) {
                        log.accept("    | " + line);
                    }
                }
            }
        } catch (IOException ignore) {
            // 忽略：输出回显失败不影响退出码
        }
        return exit;
    }

    /** 把参数数组格式化为「可读命令行」（仅用于日志；含空格的参数加引号）。 */
    private static String displayArgv(List<String> argv) {
        StringBuilder sb = new StringBuilder();
        for (String a : argv) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(a.indexOf(' ') >= 0 ? "\"" + a + "\"" : a);
        }
        return sb.toString();
    }

    private static int runAndLog(List<String> cmd, Path cwd, Consumer<String> log) throws IOException, InterruptedException {
        return runAndLog(cmd, cwd, log, Map.of());
    }

    private static int runAndLog(List<String> cmd, Path cwd, Consumer<String> log, Map<String, String> extraEnv)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(cwd.toFile());
        // 仅追加/覆盖少数环境变量（如给 mysqld 兜底 VC++ 运行库的 PATH），其余继承父进程。
        if (extraEnv != null && !extraEnv.isEmpty()) {
            pb.environment().putAll(extraEnv);
        }
        pb.redirectErrorStream(true);
        Process p = pb.start();
        // 子进程（mysqld 等）按平台本地编码输出（中文 Windows 为 GBK/CP936），须用 native.encoding 解码，
        // 否则 UI 日志区中文会显示为乱码。
        Charset nativeCs = Charset.forName(System.getProperty("native.encoding", "GBK"));
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), nativeCs))) {
            String line;
            while ((line = r.readLine()) != null) {
                log.accept("    | " + line);
            }
        }
        return p.waitFor();
    }

    private static List<Path> listZips(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))
                    .sorted()
                    .toList();
        }
    }

    /**
     * 探测 zip 是否带统一顶层目录：所有条目共享同一顶层目录则返回该目录名（解压到安装目录后即为其 home）；
     * 若为「扁平包」（首个条目即根级文件，或条目分属多个顶层目录）则返回 null——此时解压到安装目录下
     * 以包名命名的独立子目录，避免污染安装根目录并保证 home 正确。
     */
    private static String commonTopDir(Path zip) throws IOException {
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            String top = null;
            boolean first = true;
            var en = zf.entries();
            while (en.hasMoreElements()) {
                String n = en.nextElement().getName().replace('\\', '/');
                if (n.isEmpty()) {
                    continue;
                }
                int slash = n.indexOf('/');
                if (first) {
                    if (slash <= 0) {
                        return null;
                    }
                    first = false;
                }
                String seg = slash > 0 ? n.substring(0, slash) : n;
                if (top == null) {
                    top = seg;
                } else if (!top.equals(seg)) {
                    return null;
                }
            }
            return top;
        }
    }

    private static void unzip(Path zip, Path dest) throws IOException {
        Path destNorm = dest.normalize();
        byte[] buf = new byte[8192];
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zip)))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                Path target = destNorm.resolve(e.getName().replace('\\', '/')).normalize();
                if (!target.startsWith(destNorm)) {
                    throw new IOException("压缩包内存在非法路径: " + e.getName());
                }
                if (e.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    try (OutputStream os = Files.newOutputStream(target)) {
                        int n;
                        while ((n = zis.read(buf)) > 0) {
                            os.write(buf, 0, n);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    /**
     * 把 CodeArts Agent 插件释放到 IDEA 的 plugins/ 目录。
     * <p>软包形态为「IDEA（扁平包）+ 独立插件包（codearts）」两份；两者解压后都落在安装根目录。
     * IDEA 只会从自身 {@code plugins/} 目录加载插件，故这里把插件目录整体移入
     * {@code IDEA_HOME/plugins/}，使解压出的 IDEA 打开时即为「自带 CodeArts 插件」的形态。
     * <p>以包名关键字识别（idea=IDEA，兼容 ideaIC-* 与 idea-* 两种命名；codearts=插件），
     * 任一缺失则跳过，保持对其他软件包的通用性。
     */
    private static void installIdeaPlugin(Map<String, Path> homes, Consumer<String> log) throws IOException {
        String ideaKey = findKey(homes, "idea");
        String pluginKey = findKey(homes, "codearts");
        if (ideaKey == null || pluginKey == null) {
            return;
        }
        Path ideaHome = homes.get(ideaKey);
        Path pluginHome = homes.get(pluginKey);
        if (pluginHome.startsWith(ideaHome)) {
            return;
        }
        Path target = ideaHome.resolve("plugins").resolve(pluginHome.getFileName().toString());
        log.accept("  安装 IDEA 插件 " + pluginHome.getFileName() + " -> " + target);
        copyDir(pluginHome, target);
        deleteDir(pluginHome);
        homes.put(pluginKey, target);
        log.accept("    -> " + target);
    }

    /**
     * 在 IDEA 的 {@code bin/idea64.exe.vmoptions} 追加 {@code -Didea.plugins.path=<IDE_HOME>/plugins}。
     * <p>IDEA 2025.2 起内置插件由预生成清单 {@code plugins/plugin-classpath.txt} 枚举，直接放在
     * {@code IDE_HOME/plugins} 下的新插件目录不会被自动识别；用 {@code idea.plugins.path} 把「用户插件目录」
     * 显式指向 {@code IDE_HOME/plugins}，即可让随包分发的 CodeArts 插件（已置于该目录）被加载，且无需写入
     * 用户 profile。副作用：用户的第三方插件默认位置也随之改为该目录。幂等：已存在同项则替换，不重复追加。
     */
    private static void configureIdeaPluginPath(Path ideaHome, Consumer<String> log) throws IOException {
        Path vm = ideaHome.resolve("bin").resolve("idea64.exe.vmoptions");
        if (!Files.isRegularFile(vm)) {
            log.accept("  [跳过] 未找到 " + vm);
            return;
        }
        String entry = "-Didea.plugins.path=" + slash(ideaHome.resolve("plugins"));
        List<String> lines = new ArrayList<>(Arrays.asList(
                Files.readString(vm, StandardCharsets.UTF_8).replace("\r\n", "\n").split("\n")));
        lines.removeIf(l -> l.startsWith("-Didea.plugins.path="));
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        lines.add(entry);
        Files.writeString(vm, String.join("\r\n", lines) + "\r\n", StandardCharsets.UTF_8);
        log.accept("  配置 IDEA 插件目录: " + entry);
        log.accept("    -> " + vm);
    }

    /**
     * 便携化：在 IDEA 的 {@code bin/idea.properties} 里把 {@code idea.config.path} / {@code idea.system.path}
     * 指向 {@code IDE_HOME} 内的 {@code config} / {@code system} 目录。这样 IDEA 的设置（含全局 SDK 表
     * {@code jdk.table.xml}、新工程默认 {@code project.default.xml}）随解压出的 IDEA 目录走，不再依赖
     * {@code %APPDATA%} 用户 profile；也避免安装到机器上时污染已有用户的 IDEA 配置。
     * <p>{@code ${idea.home.path}} 为 IDEA 内置宏（见包内 idea.properties 注释）。幂等：已存在同名项则替换。
     */
    static void enablePortableIdeaConfig(Path ideaHome, Consumer<String> log) throws IOException {
        Path props = ideaHome.resolve("bin").resolve("idea.properties");
        if (!Files.isRegularFile(props)) {
            log.accept("  [跳过] 未找到 " + props);
            return;
        }
        List<String> lines = new ArrayList<>(Arrays.asList(
                Files.readString(props, StandardCharsets.UTF_8).replace("\r\n", "\n").split("\n")));
        lines.removeIf(l -> {
            String t = l.replace("#", "").trim();
            return t.startsWith("idea.config.path=") || t.startsWith("idea.system.path=");
        });
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        lines.add("idea.config.path=${idea.home.path}/config");
        lines.add("idea.system.path=${idea.home.path}/system");
        Files.writeString(props, String.join("\r\n", lines) + "\r\n", StandardCharsets.UTF_8);
        log.accept("  便携化 IDEA 配置: idea.config.path=${idea.home.path}/config");
        log.accept("    -> " + props);
    }

    /**
     * 在解压出的 IDEA 环境内的配置目录（{@code IDE_HOME/config/options}，由 {@code bin/idea.properties} 的
     * {@code idea.config.path} 指定，见 {@link #enablePortableIdeaConfig}）注册解压出的 JDK 为 SDK，
     * 并把释放出的工程（{@code .idea/misc.xml}）与「新工程默认」（{@code project.default.xml}）的 Project SDK
     * 指向它；全部落在解压出的 IDEA 目录内，不再依赖 {@code %APPDATA%} 用户 profile。
     * <p>SDK 的版本/名称取自 JDK 自身 {@code release} 文件的 {@code JAVA_VERSION}（非硬编码）。
     */
    private static void configureIdeaJdk(Path ideaHome, Map<String, Path> homes, Path projectRoot, Consumer<String> log)
            throws IOException {
        Path jdkHome = findHome(homes, "jdk");
        if (jdkHome == null) {
            log.accept("  [跳过] 未找到 JDK 包，无法注册 SDK");
            return;
        }
        Path options = ideaHome.resolve("config").resolve("options");
        Files.createDirectories(options);

        String home = slash(jdkHome);
        String name = jdkSdkName(jdkHome);
        String version = jdkVersionString(jdkHome);
        List<String> modules = jdkModules(jdkHome);
        boolean srcZip = Files.isRegularFile(jdkHome.resolve("lib").resolve("src.zip"));

        // a) jdk.table.xml：注册 SDK（按 homePath 去重，已存在则跳过，避免重复插入）
        Path jdkTable = options.resolve("jdk.table.xml");
        String table = Files.isRegularFile(jdkTable) ? Files.readString(jdkTable, StandardCharsets.UTF_8) : null;
        if (table != null && table.contains("homePath value=\"" + home + "\"")) {
            log.accept("  SDK 已注册，跳过: " + home);
        } else {
            String xml = table != null ? table
                    : "<application>\n  <component name=\"ProjectJdkTable\">\n  </component>\n</application>\n";
            int idx = xml.lastIndexOf("</component>");
            if (idx < 0) {
                throw new IOException("jdk.table.xml 结构异常（缺少 </component>）: " + jdkTable);
            }
            xml = xml.substring(0, idx) + buildJdkBlock(name, version, home, modules, srcZip) + xml.substring(idx);
            Files.writeString(jdkTable, xml, StandardCharsets.UTF_8);
            log.accept("  注册 SDK \"" + name + "\" -> " + home);
            log.accept("    -> " + jdkTable);
        }

        // b) 释放出的工程：写 .idea/misc.xml 指定 Project SDK
        if (projectRoot != null) {
            setProjectSdk(projectRoot, name, log);
        }

        // c) 新工程默认 Project SDK
        setDefaultProjectSdk(options, name, log);
    }

    /** 生成 jdk.table.xml 中的单个 {@code <jdk>} 块：classPath 由 release 的 MODULES 逐模块生成 jrt，sourcePath 指向 lib/src.zip。 */
    private static String buildJdkBlock(String name, String version, String home, List<String> modules, boolean srcZip) {
        StringBuilder sb = new StringBuilder();
        sb.append("    <jdk version=\"2\">\n");
        sb.append("      <name value=\"").append(x(name)).append("\" />\n");
        sb.append("      <type value=\"JavaSDK\" />\n");
        sb.append("      <version value=\"").append(x(version)).append("\" />\n");
        sb.append("      <homePath value=\"").append(x(home)).append("\" />\n");
        sb.append("      <roots>\n");
        sb.append("        <annotationsPath>\n");
        sb.append("          <root type=\"composite\">\n");
        sb.append("            <root url=\"jar://$APPLICATION_HOME_DIR$/plugins/java/lib/resources/jdkAnnotations.jar!/\" type=\"simple\" />\n");
        sb.append("          </root>\n");
        sb.append("        </annotationsPath>\n");
        sb.append("        <classPath>\n");
        sb.append("          <root type=\"composite\">\n");
        for (String m : modules) {
            sb.append("            <root url=\"jrt://").append(x(home)).append("!/").append(m).append("\" type=\"simple\" />\n");
        }
        sb.append("          </root>\n");
        sb.append("        </classPath>\n");
        sb.append("        <javadocPath>\n");
        sb.append("          <root type=\"composite\" />\n");
        sb.append("        </javadocPath>\n");
        sb.append("        <sourcePath>\n");
        sb.append("          <root type=\"composite\">\n");
        if (srcZip) {
            for (String m : modules) {
                sb.append("            <root url=\"jar://").append(x(home)).append("/lib/src.zip!/").append(m).append("\" type=\"simple\" />\n");
            }
        }
        sb.append("          </root>\n");
        sb.append("        </sourcePath>\n");
        sb.append("      </roots>\n");
        sb.append("    </jdk>\n");
        return sb.toString();
    }

    /** 写/改工程 .idea/misc.xml 的 ProjectRootManager，指定工程 SDK；文件不存在则新建。 */
    private static void setProjectSdk(Path projectRoot, String name, Consumer<String> log) throws IOException {
        Path ideaDir = projectRoot.resolve(".idea");
        Files.createDirectories(ideaDir);
        Path misc = ideaDir.resolve("misc.xml");
        String comp = "<component name=\"ProjectRootManager\" version=\"2\" project-jdk-name=\"" + x(name)
                + "\" project-jdk-type=\"JavaSDK\" />";
        String xml;
        if (Files.isRegularFile(misc)) {
            xml = Files.readString(misc, StandardCharsets.UTF_8);
            if (xml.contains("name=\"ProjectRootManager\"")) {
                xml = xml.replaceAll("(?s)<component name=\"ProjectRootManager\"[^>]*/>",
                        Matcher.quoteReplacement(comp));
            } else {
                int idx = xml.lastIndexOf("</project>");
                if (idx < 0) {
                    throw new IOException("misc.xml 结构异常（缺少 </project>）: " + misc);
                }
                xml = xml.substring(0, idx) + "  " + comp + "\n" + xml.substring(idx);
            }
        } else {
            xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<project version=\"4\">\n  " + comp + "\n</project>\n";
        }
        Files.writeString(misc, xml, StandardCharsets.UTF_8);
        log.accept("  设置工程 Project SDK = \"" + name + "\"");
        log.accept("    -> " + misc);
    }

    /** 写/改 IDEA 用户配置的 project.default.xml，把 defaultProject 的 ProjectRootManager 指向该 SDK（决定新工程默认 SDK）。 */
    private static void setDefaultProjectSdk(Path options, String name, Consumer<String> log) throws IOException {
        Path pd = options.resolve("project.default.xml");
        String comp = "<component name=\"ProjectRootManager\" version=\"2\" default=\"true\" project-jdk-name=\""
                + x(name) + "\" project-jdk-type=\"JavaSDK\" />";
        String xml;
        if (Files.isRegularFile(pd)) {
            xml = Files.readString(pd, StandardCharsets.UTF_8);
            if (xml.contains("name=\"ProjectRootManager\"")) {
                xml = xml.replaceFirst("(<component name=\"ProjectRootManager\"[^>]*?project-jdk-name=\")[^\"]*(\")",
                        "$1" + Matcher.quoteReplacement(name) + "$2");
            } else {
                int close = xml.indexOf("</defaultProject>");
                if (close < 0) {
                    log.accept("  [跳过] project.default.xml 无 defaultProject 节点: " + pd);
                    return;
                }
                xml = xml.substring(0, close) + "      " + comp + "\n" + xml.substring(close);
            }
        } else {
            xml = "<application>\n  <component name=\"ProjectManager\">\n    <defaultProject>\n      " + comp
                    + "\n    </defaultProject>\n  </component>\n</application>\n";
        }
        Files.writeString(pd, xml, StandardCharsets.UTF_8);
        log.accept("  设置默认 Project SDK = \"" + name + "\"");
        log.accept("    -> " + pd);
    }

    /**
     * 把释放出的工作区（父聚合 pom 及其子模块）内各 {@code pom.xml} 的 {@code <java.version>} 对齐为
     * 安装目录内 JDK 的实际主版本（取自其 {@code release} 的 {@code JAVA_VERSION}，如 26.0.1 -> 26），
     * 使工程编译/运行版本跟随随包携带的 JDK，而非写死的 26。仅改写带该标签的 pom，其它内容保持不变。
     */
    static void alignPomJavaVersion(Path workspaceRoot, Path jdkHome, Consumer<String> log) throws IOException {
        String major = jdkSdkName(jdkHome);
        if (major == null || major.isBlank() || major.equals("jdk")) {
            log.accept("  [跳过] 无法从 JDK release 读取 JAVA_VERSION，保留 pom 原有 java.version");
            return;
        }
        Pattern p = Pattern.compile("(?s)(<java\\.version>)\\s*[^<]*?\\s*(</java\\.version>)");
        try (Stream<Path> s = Files.walk(workspaceRoot)) {
            List<Path> poms = s.filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().equals("pom.xml"))
                    .filter(f -> !f.toString().replace('\\', '/').contains("/target/"))
                    .toList();
            for (Path pom : poms) {
                String text = Files.readString(pom, StandardCharsets.UTF_8);
                Matcher m = p.matcher(text);
                if (!m.find()) {
                    continue;
                }
                String updated = m.replaceAll("$1" + Matcher.quoteReplacement(major) + "$2");
                if (!updated.equals(text)) {
                    Files.writeString(pom, updated, StandardCharsets.UTF_8);
                    log.accept("    [版本对齐] " + workspaceRoot.relativize(pom) + " -> java.version=" + major);
                }
            }
        }
    }

    /**
     * 参考工程内 Example 的 Run Configuration，为释放出的工作区（聚合工程）生成两个 IDEA 运行配置：
     * <ul>
     *   <li>{@code ExampleApplication.xml}（Spring Boot 类型，主类
     *       {@code com.ocean.ontologyframework.example.ExampleApplication}）——直接启动示例应用；</li>
     *   <li>{@code AllExampleSuiteTest.xml}（JUnit 类型，测试类
     *       {@code com.ocean.ontologyframework.example.AllExampleSuiteTest}）——一键串跑本模块测试套件。</li>
     * </ul>
     * 二者 {@code Working directory = $MODULE_WORKING_DIR$}，保证 application.yaml 内相对路径（ontology/... 等）
     * 以模块根为基准解析。使用户打开工作区后无需手工新建 Run Configuration。
     *
     * <p>落点必须在工作区（聚合工程）根，而非示例模块自身：IDEA 只识别工程根下的 {@code .idea}，
     * 模块内的 {@code .idea} 对聚合工程无效（与工程内 PlumBlossomApplication 的运行配置落点一致）。
     *
     * @param workspaceRoot 工作区（聚合工程）根目录；运行配置写入其下的 {@code .idea/runConfigurations/}
     */
    static void generateExampleRunConfiguration(Path workspaceRoot, Consumer<String> log) throws IOException {
        Path rcDir = workspaceRoot.resolve(".idea").resolve("runConfigurations");
        Files.createDirectories(rcDir);

        String appTpl = readResource("templates/example-run-configuration.xml");
        String appXml = appTpl.replace("@{MODULE}", PROJECT_DIR)
                .replace("@{MAIN_CLASS}", "com.ocean.ontologyframework.example.ExampleApplication");
        Path appRc = rcDir.resolve("ExampleApplication.xml");
        Files.writeString(appRc, appXml, StandardCharsets.UTF_8);
        log.accept("  已生成 IDEA 运行配置 -> " + appRc);

        String suiteTpl = readResource("templates/example-suite-run-configuration.xml");
        String suiteXml = suiteTpl.replace("@{MODULE}", PROJECT_DIR)
                .replace("@{MAIN_CLASS}", "com.ocean.ontologyframework.example.AllExampleSuiteTest");
        Path suiteRc = rcDir.resolve("AllExampleSuiteTest.xml");
        Files.writeString(suiteRc, suiteXml, StandardCharsets.UTF_8);
        log.accept("  已生成 IDEA 运行配置 -> " + suiteRc);
    }

    /** SDK 名：取 JDK release 的 JAVA_VERSION 主版本号（如 26.0.1 -> "26"，即 IDEA「添加 SDK」的默认命名）。 */
    private static String jdkSdkName(Path jdkHome) {
        String v = jdkReleaseValue(jdkHome, "JAVA_VERSION");
        if (v == null || v.isBlank()) {
            return "jdk";
        }
        int dot = v.indexOf('.');
        return dot > 0 ? v.substring(0, dot) : v;
    }

    /** SDK 显示版本串（如 "Oracle OpenJDK 26.0.1"）；IDEA 加载时会自行探测，此处仅作初始值。 */
    private static String jdkVersionString(Path jdkHome) {
        String v = jdkReleaseValue(jdkHome, "JAVA_VERSION");
        if (v == null || v.isBlank()) {
            return "Java";
        }
        String impl = jdkReleaseValue(jdkHome, "IMPLEMENTOR");
        if (impl != null && impl.toLowerCase(Locale.ROOT).contains("oracle")) {
            return "Oracle OpenJDK " + v;
        }
        return "OpenJDK " + v;
    }

    /** JDK 模块列表：取自 release 的 MODULES，用于生成 classPath/sourcePath 的 jrt 根。 */
    private static List<String> jdkModules(Path jdkHome) {
        String m = jdkReleaseValue(jdkHome, "MODULES");
        if (m == null || m.isBlank()) {
            return List.of();
        }
        return Arrays.stream(m.trim().split("\\s+")).toList();
    }

    /** 读取 JDK release 文件里的 {@code KEY="value"} 行。 */
    private static String jdkReleaseValue(Path jdkHome, String key) {
        Path rel = jdkHome.resolve("release");
        if (!Files.isRegularFile(rel)) {
            return null;
        }
        try {
            for (String line : Files.readAllLines(rel, StandardCharsets.UTF_8)) {
                if (line.startsWith(key + "=")) {
                    String v = line.substring(key.length() + 1).trim();
                    if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
                        v = v.substring(1, v.length() - 1);
                    }
                    return v;
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private static String slash(Path path) {
        return path.toAbsolutePath().normalize().toString().replace('\\', '/');
    }

    private static String x(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String findKey(Map<String, Path> homes, String keyword) {
        for (String k : homes.keySet()) {
            if (k.toLowerCase(Locale.ROOT).contains(keyword)) {
                return k;
            }
        }
        return null;
    }

    private static void copyDir(Path src, Path dest) throws IOException {
        try (Stream<Path> s = Files.walk(src)) {
            for (Path p : s.toList()) {
                Path t = dest.resolve(src.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(t);
                } else {
                    Files.createDirectories(t.getParent());
                    Files.copy(p, t, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void deleteDir(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }

    /** 定位安装器分发包目录（含 softwares/ 与工程包），优先 jar 所在目录，其次向上查找。 */
    private static Path resolveResourceHome() {
        try {
            java.security.CodeSource cs = InstallService.class.getProtectionDomain().getCodeSource();
            if (cs != null && cs.getLocation() != null) {
                Path loc = Path.of(cs.getLocation().toURI());
                Path dir = Files.isDirectory(loc) ? loc : loc.getParent();
                if (dir != null && Files.isDirectory(dir.resolve("softwares"))) {
                    return dir;
                }
            }
        } catch (Exception ignored) {
        }
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path c : List.of(cwd, cwd.resolve(".."), cwd.resolve("../.."))) {
            Path n = c.normalize();
            if (Files.isDirectory(n.resolve("softwares"))) {
                return n;
            }
        }
        return cwd;
    }

    private static String readResource(String name) throws IOException {
        try (InputStream in = InstallService.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) {
                throw new IOException("缺少模板资源: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 解压前磁盘空间预检：按各 zip 的解压后大小（zip 条目未压缩大小之和）估算安装目录与工作目录所需空间，
     * 各加 10% 余量；任一目录所在磁盘剩余不足即抛异常快速失败，避免解压到一半因空间耗尽留下半成品。
     * 安装目录需容纳全部软件包与离线 Maven 仓库；工作目录需容纳工程包。
     */
    private static void ensureDiskSpace(Path installDir, Path workDir, List<Path> zips, Path repoZip,
                                        Path projectZip, Consumer<String> log) throws IOException {
        long installNeed = 0;
        for (Path z : zips) {
            installNeed += uncompressedSize(z);
        }
        if (Files.isRegularFile(repoZip)) {
            installNeed += uncompressedSize(repoZip);
        }
        long workNeed = Files.isRegularFile(projectZip) ? uncompressedSize(projectZip) : 0;
        installNeed = (long) (installNeed * 1.1);
        workNeed = (long) (workNeed * 1.1);
        long installFree = Files.getFileStore(installDir).getUsableSpace();
        long workFree = Files.getFileStore(workDir).getUsableSpace();
        log.accept(String.format(Locale.ROOT,
                "  磁盘空间预检: 安装目录需≈%.2f GB / 可用 %.2f GB；工作目录需≈%.2f GB / 可用 %.2f GB",
                gb(installNeed), gb(installFree), gb(workNeed), gb(workFree)));
        if (installFree < installNeed) {
            throw new IOException(String.format(Locale.ROOT,
                    "安装目录磁盘空间不足: 需≈%.2f GB, 可用 %.2f GB。请更换安装目录或清理磁盘后重试。",
                    gb(installNeed), gb(installFree)));
        }
        if (workFree < workNeed) {
            throw new IOException(String.format(Locale.ROOT,
                    "工作目录磁盘空间不足: 需≈%.2f GB, 可用 %.2f GB。请更换工作目录或清理磁盘后重试。",
                    gb(workNeed), gb(workFree)));
        }
    }

    /** 计算 zip 解压后的总大小（各条目未压缩大小之和；getSize() 为 -1 的条目忽略）。 */
    private static long uncompressedSize(Path zip) throws IOException {
        long sum = 0;
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            var en = zf.entries();
            while (en.hasMoreElements()) {
                long sz = en.nextElement().getSize();
                if (sz > 0) {
                    sum += sz;
                }
            }
        }
        return sum;
    }

    private static double gb(long bytes) {
        return bytes / 1024.0 / 1024.0 / 1024.0;
    }

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /**
     * 检测安装路径是否含非 ASCII 字符（中文等）。若含则直接抛异常阻止安装。
     * 原因：MySQL（mysqld）26.7.0 在 Windows 上正常启动时内部用 ANSI API 读取自身路径（argv[0]），
     * 中文字符会被截断，导致 basedir/datadir 解析错误、找不到 errmsg.sys/ibdata1 而启动失败。
     * 实测确认：即使系统 ANSI codepage=65001（UTF-8 区域设置）也无法解决此问题。
     */
    private static void checkChinesePath(Path installDir, Consumer<String> log) throws IOException {
        String pathStr = installDir.toString();
        boolean hasNonAscii = pathStr.chars().anyMatch(c -> c > 127);
        if (!hasNonAscii) {
            return;
        }
        log.accept("============================================================");
        log.accept("[错误] 安装路径含非 ASCII 字符（中文等）: " + pathStr);
        log.accept("        MySQL（mysqld）在 Windows 上无法在含中文的路径下正常启动，");
        log.accept("        会导致基于路径的服务全部失败。请使用纯英文路径安装。");
        log.accept("============================================================");
        throw new IOException("安装路径含非 ASCII 字符，MySQL 无法在此路径下运行: " + pathStr
                + "。请使用纯英文路径。");
    }

    /**
     * 校验操作系统为 Windows 10 及以上版本，否则直接抛异常阻止安装。
     * 说明：Windows 10 与 Windows 11 的 os.version 均报告为 10.0（Win11 不会报 11），
     * 故以主版本号 >= 10 作为「>= Windows 10」的统一判据，天然涵盖 Win11；
     * Win8.1（6.3）、Win8（6.2）、Win7（6.1）会被正确拦截。
     * 为避免 os.version 在企业环境中受兼容性 manifest 影响而误报为 6.x，
     * 优先读取注册表 CurrentMajorVersionNumber（Win10 起才存在）作为权威判据。
     */
    static void checkWindowsVersion(Consumer<String> log) throws IOException {
        String osName = System.getProperty("os.name", "");
        String osVersion = System.getProperty("os.version", "");
        int regMajor = readRegistryDword("HKLM\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion",
                "CurrentMajorVersionNumber");
        checkWindowsVersion(osName, osVersion, regMajor, log);
    }

    /**
     * 纯逻辑判定（可单测）：osName 须为 Windows，且主版本号 >= 10；主版本号优先取注册表值（regMajor > 0 时），
     * 否则回退解析 os.version。不满足则记录中文错误日志并抛 IOException。
     */
    static void checkWindowsVersion(String osName, String osVersion, int regMajor, Consumer<String> log)
            throws IOException {
        boolean windows = osName != null && osName.toLowerCase(Locale.ROOT).contains("windows");
        int major = regMajor > 0 ? regMajor : parseMajorVersion(osVersion);
        if (windows && major >= 10) {
            log.accept("操作系统: " + osName + " " + osVersion + "（主版本 " + major + "）");
            return;
        }
        log.accept("============================================================");
        if (!windows) {
            log.accept("[错误] 当前操作系统不是 Windows: " + osName);
            log.accept("        本安装程序仅支持 Windows 10 及以上版本。");
            log.accept("============================================================");
            throw new IOException("本安装程序仅支持 Windows 10 及以上版本，当前系统: " + osName);
        }
        log.accept("[错误] 当前 Windows 版本过低: " + osName + " " + osVersion);
        log.accept("        本安装程序要求 Windows 10 及以上版本（主版本 >= 10）。");
        log.accept("============================================================");
        throw new IOException("Windows 版本过低（" + osName + " " + osVersion
                + "），本安装程序要求 Windows 10 及以上版本。");
    }

    /** 解析版本号字符串的主版本号；无法解析返回 -1。 */
    static int parseMajorVersion(String osVersion) {
        if (osVersion == null) {
            return -1;
        }
        String[] parts = osVersion.split("\\.");
        try {
            return parts.length > 0 ? Integer.parseInt(parts[0].trim()) : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 读取注册表 REG_DWORD 值（如 CurrentMajorVersionNumber）；读取失败返回 -1。reg 输出为纯 ASCII。 */
    private static int readRegistryDword(String key, String value) {
        try {
            ProcessBuilder pb = new ProcessBuilder("reg", "query", key, "/v", value);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            p.waitFor();
            int idx = sb.indexOf("REG_DWORD");
            if (idx < 0) {
                return -1;
            }
            String rest = sb.substring(idx + "REG_DWORD".length()).trim();
            int sp = rest.indexOf(' ');
            String hex = (sp > 0 ? rest.substring(0, sp) : rest).trim();
            if (hex.startsWith("0x") || hex.startsWith("0X")) {
                hex = hex.substring(2);
            }
            return Integer.parseInt(hex, 16);
        } catch (Exception ignore) {
            return -1;
        }
    }
}
