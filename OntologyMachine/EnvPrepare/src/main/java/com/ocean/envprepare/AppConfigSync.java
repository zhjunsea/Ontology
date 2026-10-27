package com.ocean.envprepare;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 应用侧连接串同步器：EnvPrepare 以 application.yaml 中配置的端口启动配套服务后，需保证
 * 应用自身读取的连接串（{@code .properties}，由禁区模块 OBDAHandler 直接读取）与之保持一致——
 * 应用侧的 Ontop 端口 / DB 端口真值落点是 .properties（而非 application.yaml），故此处做「回写对齐」。
 * <p>仅改写文件中「已存在」的键，其余行原样保留（含 CRLF 行尾）：
 * <ul>
 *   <li>{@code port=} → env-prepare.ontop-port</li>
 *   <li>{@code sparql.endpoint=} / {@code ontop.sparql.endpoint=} → http://localhost:&lt;ontop-port&gt;/sparql</li>
 *   <li>{@code jdbc.url} 的主机端口 → env-prepare.mysql.port（声明了 env-prepare.mysql 时）</li>
 * </ul>
 */
final class AppConfigSync {

    private AppConfigSync() {
    }

    static void sync(EnvConfig cfg, Consumer<String> log) {
        if (!cfg.ontopConfigured() || isBlank(cfg.ontopProperties())) {
            return;
        }
        Path props = cfg.resolvePath(cfg.ontopProperties());
        if (!Files.isRegularFile(props)) {
            log.accept("[SKIP] 应用属性文件不存在，跳过连接串同步: " + props);
            return;
        }
        String original;
        try {
            original = Files.readString(props, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.accept("[WARN] 读取应用属性文件失败: " + e.getMessage());
            return;
        }

        String ontopPort = cfg.ontopPort();
        String sparqlUrl = "http://localhost:" + ontopPort + "/sparql";

        String content = replaceLineValue(original, "port", ontopPort);
        content = replaceLineValue(content, "sparql\\.endpoint", sparqlUrl);
        content = replaceLineValue(content, "ontop\\.sparql\\.endpoint", sparqlUrl);
        if (cfg.mysqlDefined()) {
            content = replaceJdbcPort(content, cfg.mysqlPort());
        }

        if (content.equals(original)) {
            log.accept("[INFO] 应用连接串已与配置端口一致，无需改动: " + props);
            return;
        }
        try {
            Files.writeString(props, content, StandardCharsets.UTF_8);
            log.accept("[SYNC] 已对齐应用连接串到配置端口（ontop-port=" + ontopPort
                    + (cfg.mysqlDefined() ? ", mysql-port=" + cfg.mysqlPort() : "") + "）: " + props);
        } catch (IOException e) {
            log.accept("[WARN] 写回应用属性文件失败: " + e.getMessage());
        }
    }

    /** 改写整行键的值（键名精确匹配行首，避免误伤 management-port / pool.* 等）；CRLF 行尾不受影响。 */
    private static String replaceLineValue(String content, String keyRegex, String newValue) {
        Pattern p = Pattern.compile("(?m)^(" + keyRegex + "\\s*=\\s*).+$");
        Matcher m = p.matcher(content);
        return m.replaceAll(mr -> mr.group(1) + newValue);
    }

    /** 仅替换 jdbc:mysql://host:PORT/ 中的端口，保留其余连接参数。 */
    private static String replaceJdbcPort(String content, String port) {
        Pattern p = Pattern.compile("(jdbc:mysql://[^:/\\s]+:)\\d+(/)");
        Matcher m = p.matcher(content);
        return m.replaceAll(mr -> mr.group(1) + port + mr.group(2));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
