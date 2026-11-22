package com.ocean.envprepare;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * application.yaml 端口行级改写器：仅改写 {@code env-prepare.ontop-port} 这一行的「值」，
 * 保留缩进、行尾（CRLF/LF）与行尾注释，其余各行原样不动。
 *
 * <p>用于 Ontop 启动遇端口冲突时「自动改端口避让」，停止时「改回原端口」。
 * 注意：匹配行首（允许前导空格/制表符）的 {@code ontop-port: <值>}；被注释的行（{@code # ontop-port:}）不会命中。
 */
final class ConfigPortUpdater {

    private ConfigPortUpdater() {
    }

    /** 匹配一行 {@code ontop-port: <value>}；group(1)=前导+键+冒号+空格，group(2)=值，group(3)=行内其余（注释等）。 */
    private static final Pattern ONTOP_PORT_LINE =
            Pattern.compile("(?m)^([ \\t]*ontop-port[ \\t]*:[ \\t]*)([^ \\t#\\r\\n]+)(.*)$");

    /**
     * 把 application.yaml 中 env-prepare 的 {@code ontop-port} 改为 {@code newPort}。
     *
     * @return true=发生了实际改动并写回成功；false=文件缺失/无该键/值已是新值/写回失败（均不抛异常）。
     */
    static boolean updateOntopPort(Path configFile, String newPort, Consumer<String> log) {
        if (configFile == null || !Files.isRegularFile(configFile)) {
            log.accept("[WARN] application.yaml 不存在，无法改写 ontop-port: " + configFile);
            return false;
        }
        if (newPort == null || newPort.isBlank()) {
            log.accept("[WARN] 目标 ontop-port 为空，已跳过改写。");
            return false;
        }
        String original;
        try {
            original = Files.readString(configFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.accept("[WARN] 读取 application.yaml 失败: " + e.getMessage());
            return false;
        }
        Matcher m = ONTOP_PORT_LINE.matcher(original);
        if (!m.find()) {
            log.accept("[WARN] 未在 application.yaml 找到活动的 ontop-port 键，无法自动改端口: " + configFile);
            return false;
        }
        String updated = m.replaceAll(mr -> Matcher.quoteReplacement(mr.group(1) + newPort + mr.group(3)));
        if (updated.equals(original)) {
            log.accept("[INFO] application.yaml 的 ontop-port 已是 " + newPort + "，无需改写。");
            return false;
        }
        try {
            Files.writeString(configFile, updated, StandardCharsets.UTF_8);
            log.accept("[CFG] 已改写 application.yaml 的 env-prepare.ontop-port -> " + newPort + " : " + configFile);
            return true;
        } catch (IOException e) {
            log.accept("[WARN] 写回 application.yaml 失败: " + e.getMessage());
            return false;
        }
    }
}
