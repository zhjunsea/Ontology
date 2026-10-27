package com.ocean.utilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * MaaS / 大模型 API Key 双来源解析通用能力（与业务无关）。
 *
 * <p><b>优先级</b>：内联值（配置如 {@code spring.ai.openai.api-key}）去空白后非空 → 直接采用；
 * 否则回落到外部文本文件（配置如 {@code spring.ai.openai.api-key-file}），取文件首个非空行（去首尾空白、去除 BOM）作为 key。
 *
 * <p><b>降级</b>：内联值为空且文件缺失、不可读或为空时，一律返回空串（不抛异常），
 * 由调用方决定确定性降级策略。
 */
public final class ApiKeyResolver {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyResolver.class);

    private ApiKeyResolver() {
    }

    /**
     * 按「内联优先、外部文件兜底」解析实际使用的 key。
     *
     * @param inlineKey 内联 key（可为 {@code null}/空白）
     * @param filePath  外部 key 文件路径（可为 {@code null}/空白，此时不读文件）
     * @return 解析后的 key；均不可用时返回空串
     */
    public static String resolve(String inlineKey, String filePath) {
        if (inlineKey != null && !inlineKey.isBlank()) {
            return inlineKey.trim();
        }
        if (filePath == null || filePath.isBlank()) {
            return "";
        }
        try {
            Path path = Path.of(filePath.trim());
            if (!Files.isRegularFile(path)) {
                log.warn("[ApiKeyResolver] key 文件不存在或非普通文件: {}", path);
                return "";
            }
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String line : lines) {
                String value = stripBom(line).trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
            log.warn("[ApiKeyResolver] key 文件无有效内容: {}", path);
            return "";
        } catch (Exception e) {
            log.warn("[ApiKeyResolver] 读取 key 文件失败: {}", e.toString());
            return "";
        }
    }

    private static String stripBom(String s) {
        return (s != null && !s.isEmpty() && s.charAt(0) == '\uFEFF') ? s.substring(1) : s;
    }
}
