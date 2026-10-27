package com.ocean.envprepare;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 环境准备入口：纯 Java main，按子命令分发「启动环境 / 创建数据库 / 创建 RabbitMQ 资源」。
 * 可通过 --config &lt;路径&gt; 指定要使用的 application.yaml，从而以不同配置启动不同应用环境。
 */
public final class EnvPrepare {

    private EnvPrepare() {
    }

    public static void main(String[] args) {
        Path configPath = null;
        boolean autoYes = false;
        List<String> rest = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--config") || a.equals("-c")) {
                if (i + 1 >= args.length) {
                    System.err.println("[ERROR] --config 需要一个配置文件路径参数");
                    System.exit(2);
                }
                configPath = Path.of(args[++i]);
            } else if (a.startsWith("--config=")) {
                configPath = Path.of(a.substring("--config=".length()));
            } else if (a.equals("--yes") || a.equals("-y")) {
                autoYes = true;
            } else {
                rest.add(a);
            }
        }
        final Path cfg = configPath;
        String command = rest.isEmpty() ? "help" : rest.get(0).toLowerCase(Locale.ROOT);
        int code;
        try {
            code = switch (command) {
                case "start" -> new EnvironmentStarter(loadConfig(cfg), false).run();
                case "init" -> {
                    EnvConfig initCfg = loadConfig(cfg);
                    if (autoYes || confirm(initConfirmMessage(initCfg))) {
                        yield new EnvironmentStarter(initCfg, true).run();
                    }
                    System.out.println("[ABORT] 已取消 init。");
                    yield 1;
                }
                case "createdb", "create-db" -> new DatabaseInitializer(loadConfig(cfg)).run(true);
                case "create-rabbitmq", "createrabbitmq" -> new RabbitMqInitializer(loadConfig(cfg)).run(true);
                case "help", "-h", "--help" -> {
                    printUsage();
                    yield 0;
                }
                default -> {
                    System.err.println("未知命令: " + command);
                    printUsage();
                    yield 2;
                }
            };
        } catch (Exception e) {
            System.err.println("[ERROR] " + e.getMessage());
            code = 1;
        }
        System.exit(code);
    }

    /** 危险操作二次确认：仅当输入 yes/y 时返回 true；无输入（重定向/关闭）时安全地返回 false。 */
    private static boolean confirm(String prompt) {
        System.out.println("\n" + "!".repeat(60));
        System.out.println(" [危险操作] " + prompt);
        System.out.println("!".repeat(60));
        System.out.print(" 请输入 yes 继续，其它任意输入取消: ");
        System.out.flush();
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(System.in));
            String line = br.readLine();
            if (line == null) {
                return false;
            }
            String t = line.strip();
            return t.equalsIgnoreCase("yes") || t.equalsIgnoreCase("y");
        } catch (Exception e) {
            return false;
        }
    }

    private static EnvConfig loadConfig(Path configPath) {
        return configPath == null ? EnvConfig.load() : EnvConfig.load(configPath);
    }

    /** init 二次确认文案：仅列出 application.yaml 实际声明/启用的服务（未声明者不出现，避免误导）。 */
    private static String initConfirmMessage(EnvConfig cfg) {
        List<String> wipe = new ArrayList<>();
        if (cfg.mysqlDefined()) {
            wipe.add("数据库（DROP 旧库）");
        }
        if (cfg.rabbitmqDefined()) {
            wipe.add("RabbitMQ 资源（删除旧队列/交换机）");
        }
        List<String> restart = new ArrayList<>();
        if (cfg.ontopConfigured()) {
            restart.add("Ontop");
        }
        if (cfg.bpmnPathDefined()) {
            restart.add("Camunda");
        }
        if (wipe.isEmpty() && restart.isEmpty()) {
            return "init 将重建环境服务。";
        }
        StringBuilder sb = new StringBuilder("init 将");
        if (!wipe.isEmpty()) {
            sb.append("清空并重建 ").append(String.join("、", wipe));
        }
        if (!restart.isEmpty()) {
            if (!wipe.isEmpty()) {
                sb.append("，并");
            }
            sb.append("停止后重启 ").append(String.join("、", restart));
        }
        return sb.append(wipe.isEmpty() ? "。" : "，已有数据将不可恢复。").toString();
    }

    private static void printUsage() {
        System.out.println("用法: EnvPrepare <command> [--config <application.yaml 路径>] [--yes]");
        System.out.println("  --config <路径>   指定要使用的 application.yaml（决定启动/创建哪个应用环境）；");
        System.out.println("                  省略时按当前目录向上查找 src/main/resources/application.yaml");
        System.out.println("  --yes, -y        危险命令（init）免二次确认（自动化/脚本用）");
        System.out.println("  start             启动环境：按 application.yaml 声明的服务逐个拉起（RabbitMQ/MySQL/Ontop/Camunda，"
                + "未声明者自动跳过）；");
        System.out.println("                    本环境已在运行的实例则复用、库/队列缺则建有则留（不删数据）；");
        System.out.println("                    Ctrl+C 退出时仅停止本次启动的服务（复用来的进程不动）");
        System.out.println("  init              重建环境：对已声明的持久化服务（MySQL 库 / RabbitMQ 资源）删旧重建，"
                + "其余已声明服务停止后重启，再常驻启动（危险，需确认）");
        System.out.println("  createdb          创建数据库（先 DROP 旧库，再建库建表并灌数）——重建单步");
        System.out.println("  create-rabbitmq   创建 RabbitMQ 资源（先删旧队列/交换机，再建拓扑）——重建单步");
        System.out.println();
        System.out.println("端口冲突处理（端口被非本环境进程占用时，逐服务询问）：");
        System.out.println("  1) 复用原有环境   2) 杀掉原进程并启动新进程");
        System.out.println("  3) 改用其它端口（提示一个空闲端口，改 application.yaml 后重新启动）");
        System.out.println("  非交互环境（右键双击/输入重定向）直接失败退出，不擅自终止进程。");
    }
}
