package com.ocean.utilities;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 对 {@code application.yaml} 做「保留注释与排版」的外科式赋值。
 *
 * <p>不重排版整份 YAML（那会丢失全部注释），而是在指定顶层段内定位子键所在行，
 * 只替换该行的值；若子键不存在则插入到段首之后。
 */
public final class YamlConfigUpdater {

    private YamlConfigUpdater() {
    }


    private static int findTopLevelKey(List<String> lines, String key) {
        String prefix = key + ":";
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (isCommentOrBlank(line)) {
                continue;
            }
            if (leadingSpaces(line) == 0 && line.strip().startsWith(prefix)) {
                String rest = line.strip().substring(prefix.length()).trim();
                if (rest.isEmpty() || rest.startsWith("#")) {
                    return i; // 段标题行（无内联值）
                }
            }
        }
        return -1;
    }

    private static int findSectionEnd(List<String> lines, int start) {
        for (int i = start + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (isCommentOrBlank(line)) {
                continue;
            }
            if (leadingSpaces(line) == 0) {
                return i;
            }
        }
        return lines.size();
    }

    private static boolean matchesKey(String line, String key) {
        String stripped = line.strip();
        if (stripped.startsWith("#")) {
            return false;
        }
        return stripped.startsWith(key + ":");
    }

    private static boolean isCommentOrBlank(String line) {
        String stripped = line.strip();
        return stripped.isEmpty() || stripped.startsWith("#");
    }

    private static int leadingSpaces(String line) {
        int n = 0;
        while (n < line.length() && line.charAt(n) == ' ') {
            n++;
        }
        return n;
    }

    /** 渲染 YAML 标量值：空值写成 {@code ""}，必要时加双引号。 */
    private static String render(String value) {
        if (value == null || value.isBlank()) {
            return "\"\"";
        }
        String s = value.strip();
        boolean needsQuote = s.indexOf('#') >= 0
                || s.indexOf(": ") >= 0
                || s.startsWith("\"") || s.startsWith("'")
                || s.startsWith("[") || s.startsWith("{")
                || s.startsWith("*") || s.startsWith("&")
                || s.startsWith("!") || s.startsWith("|")
                || s.startsWith(">") || s.startsWith("%")
                || s.startsWith("@") || s.startsWith("`");
        if (needsQuote) {
            return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        return s;
    }
}
