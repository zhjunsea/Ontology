package com.ocean.envprepare;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 创建数据库：启动 MySQL Windows 服务（按需 UAC 提权），先 DROP 旧库，
 * 再执行建库建表 DDL 与数据灌入脚本（先删旧建新）。
 */
public final class DatabaseInitializer {

    private final EnvConfig cfg;

    public DatabaseInitializer(EnvConfig cfg) {
        this.cfg = cfg;
    }

    public int run() {
        System.out.println("=".repeat(50));
        System.out.println(" [START] 初始化 MySQL 环境（服务 + 删旧建库建表 + 灌数） ");
        System.out.println("=".repeat(50));
        System.out.println("[INFO] 已加载配置: " + cfg.configFile());
        System.out.println("[INFO] 模块根目录: " + cfg.moduleRoot());
        System.out.println("[INFO] 目标数据库: " + cfg.dbName());

        if (!cfg.mysqlDefined()) {
            System.out.println("[SKIP] application.yaml 未配置 env-prepare.mysql，跳过 MySQL 初始化（服务启动 + 建库建表 + 灌数）。");
            return 0;
        }

        if (isBlank(cfg.mysqlServiceName()) || isBlank(cfg.dbName()) || isBlank(cfg.mysqlUser())
                || isBlank(cfg.ddlFile()) || isBlank(cfg.dataFile())) {
            System.out.println("[ERROR] application.yaml 的 env-prepare 节点缺少必要配置项"
                    + " (service-name / db-name / user / ddl-file / data-file)");
            return 1;
        }

        Path logFile = cfg.logDir().resolve("mysql-init.log");
        try {
            Files.createDirectories(cfg.logDir());
        } catch (IOException e) {
            System.out.println("[ERROR] 创建日志目录失败: " + e.getMessage());
            return 1;
        }

        try (PrintWriter log = new PrintWriter(Files.newBufferedWriter(logFile,
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {

            if (new MysqlService(cfg).ensureStarted(log) == MysqlService.StartOutcome.FAILED) {
                System.out.println("\n[FAIL] MySQL 服务未能就绪，已中止。");
                System.out.println("   请手动检查 MySQL 服务后重新运行。");
                return 1;
            }

            String exe = findMysqlBin();
            if (exe == null) {
                System.out.println("\n[FAIL] 未找到 mysql.exe。请在 application.yaml 的 env-prepare.mysql.bin 中设置，");
                System.out.println("   或确保 mysql 在 PATH 中。");
                return 1;
            }
            System.out.println("[INFO] mysql 客户端: " + exe);

            String db = cfg.dbName();
            int rcDrop = runSqlStatement(exe, "DROP DATABASE IF EXISTS `" + db + "`;", log);
            System.out.println("       " + (rcDrop == 0 ? "[OK]" : "[FAIL]")
                    + " 删除旧数据库 `" + db + "` (rc=" + rcDrop + ")");

            int rcDdl = runSqlFile(exe, cfg.resolvePath(cfg.ddlFile()), null, true, log);
            System.out.println("       " + (rcDdl == 0 ? "[OK]" : "[FAIL]") + " DDL 执行完成 (rc=" + rcDdl + ")");

            int rcData = runSqlFile(exe, cfg.resolvePath(cfg.dataFile()), db, false, log);
            System.out.println("       " + (rcData == 0 ? "[OK]" : "[FAIL]") + " 数据灌入完成 (rc=" + rcData + ")");

            System.out.println("\n" + "=".repeat(50));
            if (rcDrop == 0 && rcDdl == 0 && rcData == 0) {
                System.out.println(" [OK] MySQL 初始化完成：库 " + db + " 已就绪。");
                return 0;
            }
            System.out.println(" [WARN] MySQL 初始化完成，但存在错误，请查看日志：");
            System.out.println("    " + logFile);
            return 1;

        } catch (IOException e) {
            System.out.println("[ERROR] 打开日志文件失败: " + e.getMessage());
            return 1;
        }
    }

    private String findMysqlBin() {
        String configured = cfg.mysqlBin();
        if (configured != null && !configured.isBlank() && Files.isRegularFile(Path.of(configured))) {
            return configured;
        }
        String fromPath = which("mysql");
        if (fromPath != null) {
            return fromPath;
        }
        for (String c : new String[]{
                "C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin\\mysql.exe",
                "C:\\Program Files\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe",
                "C:\\Program Files\\MySQL\\MySQL Server 9.0\\bin\\mysql.exe"}) {
            if (Files.isRegularFile(Path.of(c))) {
                return c;
            }
        }
        return null;
    }

    private String which(String exe) {
        String pathVar = System.getenv("PATH");
        if (pathVar == null) {
            return null;
        }
        for (String dir : pathVar.split(File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            for (String candidate : new String[]{exe, exe + ".exe", exe + ".bat", exe + ".cmd"}) {
                Path p = Path.of(dir, candidate);
                if (Files.isRegularFile(p)) {
                    return p.toString();
                }
            }
        }
        return null;
    }

    private List<String> mysqlArgs(String exe, String database, boolean force) {
        List<String> args = new ArrayList<>();
        args.add(exe);
        args.add("-u");
        args.add(cfg.mysqlUser());
        args.add("--default-character-set=utf8mb4");
        if (force) {
            args.add("--force");
        }
        if (database != null) {
            args.add(database);
        }
        return args;
    }

    private int runSqlFile(String exe, Path sqlFile, String database, boolean force, PrintWriter log) {
        if (!Files.isRegularFile(sqlFile)) {
            System.out.println("       [FAIL] SQL 文件不存在: " + sqlFile);
            return -1;
        }
        List<String> args = mysqlArgs(exe, database, force);
        String label = sqlFile + (database != null ? "  ->  库 " + database : "");
        System.out.println("[SQL] 执行 " + label + (force ? "  (--force 忽略重复定义错误)" : ""));

        ProcessBuilder pb = new ProcessBuilder(args);
        pb.redirectInput(sqlFile.toFile());
        pb.environment().put("MYSQL_PWD", cfg.mysqlPassword());
        return execSql(pb, label, log);
    }

    private int runSqlStatement(String exe, String sql, PrintWriter log) {
        List<String> args = mysqlArgs(exe, null, false);
        args.add("-e");
        args.add(sql);
        System.out.println("[SQL] 执行: " + sql);

        ProcessBuilder pb = new ProcessBuilder(args);
        pb.environment().put("MYSQL_PWD", cfg.mysqlPassword());
        return execSql(pb, sql, log);
    }

    private int execSql(ProcessBuilder pb, String label, PrintWriter log) {
        try {
            Process p = pb.start();
            try {
                p.getOutputStream().close();
            } catch (IOException ignored) {
            }
            byte[] outBytes = p.getInputStream().readAllBytes();
            byte[] errBytes = p.getErrorStream().readAllBytes();
            int code = p.waitFor();

            String out = new String(outBytes, StandardCharsets.UTF_8).strip();
            String err = new String(errBytes, StandardCharsets.UTF_8).strip();
            if (log != null) {
                log.println("\n===== " + label + " (rc=" + code + ") =====");
                if (!out.isEmpty()) {
                    log.println(out);
                }
                if (!err.isEmpty()) {
                    log.println(err);
                }
                log.flush();
            }
            if (!out.isEmpty()) {
                System.out.println(out);
            }
            if (!err.isEmpty()) {
                System.out.println("       [WARN] " + err);
            }
            return code;
        } catch (IOException e) {
            System.out.println("       [FAIL] 执行 SQL 异常: " + e.getMessage());
            return -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
