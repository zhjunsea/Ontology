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
            } else {
                rest.add(a);
            }
        }
        final Path cfg = configPath;
        String command = rest.isEmpty() ? "help" : rest.get(0).toLowerCase(Locale.ROOT);
        int code;
        try {
            code = switch (command) {
                case "start" -> new EnvironmentStarter(loadConfig(cfg)).run();
                case "createdb", "create-db" -> new DatabaseInitializer(loadConfig(cfg)).run();
                case "create-rabbitmq", "createrabbitmq" -> new RabbitMqInitializer(loadConfig(cfg)).run();
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

    private static EnvConfig loadConfig(Path configPath) {
        return configPath == null ? EnvConfig.load() : EnvConfig.load(configPath);
    }

    private static void printUsage() {
        System.out.println("用法: EnvPrepare <command> [--config <application.yaml 路径>]");
        System.out.println("  --config <路径>   指定要使用的 application.yaml（决定启动/创建哪个应用环境）；");
        System.out.println("                  省略时按当前目录向上查找 src/main/resources/application.yaml");
        System.out.println("  start             启动环境（按 application.yaml 配置启停 RabbitMQ/Ontop/Camunda，Ctrl+C 退出）");
        System.out.println("  createdb          创建数据库（先 DROP 旧库，再建库建表并灌数）");
        System.out.println("  create-rabbitmq   创建 RabbitMQ 资源（先删旧队列/交换机，再建拓扑）");
    }
}
