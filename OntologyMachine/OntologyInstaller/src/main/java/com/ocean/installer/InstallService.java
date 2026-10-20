package com.ocean.installer;

import java.io.BufferedReader;
import java.io.BufferedInputStream;
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
 *   <li>拷贝 softwares/ 下全部 zip 到「安装目录/softwares」；</li>
 *   <li>逐个解压到「安装目录」，并记录每个软件的 home（zip 顶层目录）；</li>
 *   <li>把 CodeArts Agent 插件（codearts 包）移入 IDEA（idea/ideaic 包）的 plugins/ 目录，并在 IDEA 的
 *       {@code bin/idea64.exe.vmoptions} 里加 {@code -Didea.plugins.path=<IDE_HOME>/plugins}，使 IDEA 打开即自带插件；</li>
 *   <li>把解压出的 JDK 注册为 IDEA 的 SDK，并设为工作区/新工程的默认 Project SDK；配置写入解压出的
 *       IDEA 环境内（经 {@code bin/idea.properties} 便携化，不依赖用户 profile）；</li>
 *   <li>把释放出的工程各 pom 的 {@code <java.version>} 对齐为安装目录内 JDK 的实际版本，并为示例模块
 *       生成 IDEA Spring Boot 运行配置（照工程内 Example 形态）；</li>
 *   <li>把随包携带的离线 Maven 仓库释放到安装目录，并让释放出的工作区默认使用它（无网也能构建/测试）；</li>
 *   <li>MySQL 在解压出的目录建 data，执行 {@code bin\mysqld.exe --initialize-insecure --basedir=. --datadir=.\data}；</li>
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

        // 1) 拷贝软件包
        Path destSoft = installDir.resolve("softwares");
        Files.createDirectories(destSoft);
        log.accept("\n[1/4] 拷贝软件包 -> " + destSoft);
        for (Path z : zips) {
            Files.copy(z, destSoft.resolve(z.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
            log.accept("  复制 " + z.getFileName() + " (" + human(Files.size(z)) + ")");
        }

        // 2) 解压到安装目录，记录 home
        log.accept("\n[2/4] 解压软件到 " + installDir);
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
        // 2.1) 安装 IDEA 插件：CodeArts Agent 插件随软包分发，解压后落在安装根目录，
        //      需移入 IDEA 的 plugins/ 目录，使解压出的 IDEA 打开即自带该插件。
        installIdeaPlugin(homes, log);
        // 2.2) IDEA 2025.2 起 IDE_HOME/plugins 不再被自动扫描（内置插件由预生成清单 plugin-classpath.txt 枚举），
        //      故用 idea.plugins.path 把「用户插件目录」显式指向 IDEA_HOME/plugins，使落在此处的 CodeArts 插件被加载。
        Path ideaHome = findHome(homes, "idea");
        if (ideaHome != null) {
            configureIdeaPluginPath(ideaHome, log);
            // 2.2.1) 便携化：把 IDEA 的配置/缓存目录指向 IDE_HOME 内（写 bin/idea.properties），
            //        使全局 SDK 等设置随解压出的 IDEA 目录走，不再依赖 %APPDATA% 用户 profile。
            enablePortableIdeaConfig(ideaHome, log);
        }
        // 2.3) 释放离线 Maven 仓库快照到安装目录：目标机无网络时，释放出的工程用它构建并运行测试。
        Path repoDir = installDir.resolve(MAVEN_REPO_DIR);
        Path repoZip = resHome.resolve(MAVEN_REPO_ZIP);
        if (Files.isRegularFile(repoZip)) {
            log.accept("\n[2.3] 释放离线 Maven 仓库 -> " + repoDir);
            unzip(repoZip, repoDir);
            log.accept("    -> " + repoDir);
        } else {
            log.accept("\n[2.3] [跳过] 未找到离线仓库包: " + repoZip);
        }

        // 3) MySQL 初始化
        log.accept("\n[3/4] 初始化 MySQL");
        Path mysqlHome = findMysqlHome(homes);
        if (mysqlHome == null) {
            log.accept("  [跳过] 未在软件包中找到 MySQL");
        } else {
            Path dataDir = mysqlHome.resolve("data");
            Files.createDirectories(dataDir);
            Path mysqld = mysqlHome.resolve("bin").resolve("mysqld.exe");
            if (!Files.isRegularFile(mysqld)) {
                throw new IOException("未找到 mysqld.exe: " + mysqld);
            }
            List<String> cmd = List.of(mysqld.toString(), "--initialize-insecure", "--basedir=.", "--datadir=.\\data");
            log.accept("  mysqld: " + mysqld);
            log.accept("  data:   " + dataDir);
            log.accept("  执行: " + String.join(" ", cmd) + "  (cwd=" + mysqlHome + ")");
            int rc = runAndLog(cmd, mysqlHome, log);
            if (rc != 0) {
                throw new IOException("MySQL 初始化失败 (退出码 " + rc + ")");
            }
            log.accept("  MySQL data 初始化完成。");
        }

        // 4) 解压工程 + 生成配置
        //    工程包解压到工作目录后，工作目录即「工作区聚合工程根」（根为父聚合 pom.xml，
        //    OntologyFrameworkExample 为其子模块）。因此用 IDEA 打开工作目录时，示例与后续
        //    新建的应用会作为并列模块同时出现在工程视图中——这正是「打开示例工程后能看到新工程」的关键。
        log.accept("\n[4/4] 解压工程并生成配置");
        unzip(projectZip, workDir);
        Path workspaceRoot = workDir;
        Path moduleRoot = workDir.resolve(PROJECT_DIR);
        if (!Files.isDirectory(moduleRoot)) {
            // 兼容不含子模块目录的旧工程包：退化为工作区根即模块根。
            moduleRoot = workspaceRoot;
        }
        log.accept("  工作区（聚合工程）根: " + workspaceRoot);
        log.accept("  示例模块目录: " + moduleRoot);

        // 4.1) 把解压出的 JDK 注册为 IDEA 的 SDK，并设为工作区/新工程的默认 Project SDK，
        //      否则打开释放出的工作区会因无 SDK 而报「JDK 未设置」。
        if (ideaHome != null) {
            configureIdeaJdk(ideaHome, homes, workspaceRoot, log);
        }
        // 4.1.1) 把释放出的工程各 pom 的 <java.version> 对齐为安装目录内 JDK 的实际主版本，
        //        使工程编译/运行版本跟随随包携带的 JDK，而非写死的 26。
        Path jdkHome = findHome(homes, "jdk");
        if (jdkHome != null) {
            alignPomJavaVersion(workspaceRoot, jdkHome, log);
        }
        // 4.1.2) 参考工程内 Example 的 Run Configuration，为工作区（聚合工程）生成 IDEA Spring Boot 运行配置，
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

        // 4.2) 让释放出的工作区默认使用随安装包分发的离线 Maven 仓库（无网也能构建/测试）。
        //      写在工作区根，使聚合工程（含各子模块）整体共享该配置。
        if (Files.isDirectory(repoDir)) {
            writeMavenConfig(workspaceRoot, repoDir, log);
        }

        // 4.3) 投放「本体应用构建」SKILL 到工作区根 .codeartsdoer/skills/ontology-app-builder/，
        //      使客户在 IDEA 中打开工作区后，码道（CodeArts）即识别该技能，按 14 步流程协助构建新应用。
        installAppBuilderSkill(resHome, workspaceRoot, log);

        log.accept("\n安装完成。用 IDEA 打开工作区目录 " + workspaceRoot
                + " 即可看到示例与新建应用并列；进入示例模块 " + moduleRoot.getFileName()
                + " 双击 start-env.cmd 一键准备并启动环境。");
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
     * Maven（含 IDEA 内置 Maven）构建并运行测试，无需从公网下载依赖。仅当安装目录不含空格时路径无需转义。
     */
    private static void writeMavenConfig(Path workspaceRoot, Path repoDir, Consumer<String> log) throws IOException {
        Path mvnDir = workspaceRoot.resolve(".mvn");
        Files.createDirectories(mvnDir);
        Path cfg = mvnDir.resolve("maven.config");
        String repo = slash(repoDir);
        String arg = repo.indexOf(' ') >= 0 ? "-Dmaven.repo.local=\"" + repo + "\"" : "-Dmaven.repo.local=" + repo;
        Files.writeString(cfg, arg + "\r\n", StandardCharsets.UTF_8);
        log.accept("  已配置离线 Maven 仓库: " + arg);
        log.accept("    -> " + cfg);
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

    private static int runAndLog(List<String> cmd, Path cwd, Consumer<String> log) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(cwd.toFile());
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
     * 参考工程内 Example 的 Run Configuration，为释放出的工作区（聚合工程）生成
     * {@code <工程根>/.idea/runConfigurations/ExampleApplication.xml}（Spring Boot 类型，主类
     * {@code com.ocean.ontologyframework.example.ExampleApplication}，且 {@code Working directory = $MODULE_WORKING_DIR$}）。
     * 使用户打开工作区后无需手工新建 Run Configuration 即可直接启动示例应用；{@code $MODULE_WORKING_DIR$} 保证
     * application.yaml 内相对路径（ontology/... 等）以模块根为基准解析。
     *
     * <p>落点必须在工作区（聚合工程）根，而非示例模块自身：IDEA 只识别工程根下的 {@code .idea}，
     * 模块内的 {@code .idea} 对聚合工程无效（与工程内 PlumBlossomApplication 的运行配置落点一致）。
     *
     * @param workspaceRoot 工作区（聚合工程）根目录；运行配置写入其下的 {@code .idea/runConfigurations/}
     */
    static void generateExampleRunConfiguration(Path workspaceRoot, Consumer<String> log) throws IOException {
        String tpl = readResource("templates/example-run-configuration.xml");
        String xml = tpl.replace("@{MODULE}", PROJECT_DIR)
                .replace("@{MAIN_CLASS}", "com.ocean.ontologyframework.example.ExampleApplication");
        Path rc = workspaceRoot.resolve(".idea").resolve("runConfigurations").resolve("ExampleApplication.xml");
        Files.createDirectories(rc.getParent());
        Files.writeString(rc, xml, StandardCharsets.UTF_8);
        log.accept("  已生成 IDEA 运行配置 -> " + rc);
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

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String human(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.ROOT, "%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(Locale.ROOT, "%.1f MB", mb);
        }
        return String.format(Locale.ROOT, "%.2f GB", mb / 1024.0);
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
}
