package com.ocean.utilities;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 通用 {@code application.yaml} 定位器。
 *
 * <p>定位优先级：
 * <ol>
 *   <li>显式覆盖路径（如配置项 {@code tmsd.config-file}），须存在，否则抛错；</li>
 *   <li>当前工作目录源码文件 {@code <user.dir>/src/main/resources/application.yaml}；</li>
 *   <li>classpath 下的 {@code application.yaml}（仅 file 协议）。</li>
 * </ol>
 */
public final class ConfigFileLocator {

    private ConfigFileLocator() {
    }

    /**
     * 定位 {@code application.yaml}。
     *
     * @param override 显式覆盖路径（可为 null/空白，表示不启用）
     * @return 已存在的配置文件路径
     * @throws IllegalStateException 覆盖路径不存在，或三处均无法定位
     */
    public static Path resolve(String override) {
        if (override != null && !override.isBlank()) {
            Path p = Paths.get(override.strip());
            if (Files.exists(p)) {
                return p;
            }
            throw new IllegalStateException("tmsd.config-file 指向的文件不存在: " + p);
        }
        Path p = Paths.get(System.getProperty("user.dir"), "src", "main", "resources", "application.yaml");
        if (Files.exists(p)) {
            return p;
        }
        URL url = ConfigFileLocator.class.getClassLoader().getResource("application.yaml");
        if (url != null && "file".equals(url.getProtocol())) {
            try {
                return Paths.get(url.toURI());
            } catch (Exception e) {
                throw new IllegalStateException("解析 application.yaml 路径失败: " + url, e);
            }
        }
        throw new IllegalStateException("无法定位 application.yaml，请设置 tmsd.config-file");
    }
}
