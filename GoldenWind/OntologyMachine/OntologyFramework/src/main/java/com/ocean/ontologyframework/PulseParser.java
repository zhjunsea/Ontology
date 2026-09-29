package com.ocean.ontologyframework;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 脉象描述解析工具
 */
public class PulseParser {

    // 定义分隔符模式：中文顿号、逗号、空格、以及“而”字
    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("[而、，,\\s]+");

    /**
     * 将包含多个脉象描述的字符串拆分成独立的脉象名称列表（每个名称后带“脉”字）。
     * <p>
     * 本方法用于解析复合脉象描述，将其中的各个脉象独立提取出来，并统一添加“脉”字后缀，
     * 以便与本体（maixiang.owl）中的脉象个体（如“浮脉”“缓脉”）进行匹配。
     * </p>
     *
     * <h3>处理逻辑</h3>
     * <ol>
     *   <li>去除输入字符串的首尾空白。</li>
     *   <li>如果字符串以“脉”字开头，则移除该“脉”字（避免重复）。</li>
     *   <li>按分隔符（而、，、,、空格等）分割剩余部分。</li>
     *   <li>对每个片段，移除其中所有的“脉”字（防止重复）。</li>
     *   <li>将片段拆分为单个中文字符，每个字符视为一个脉象名称，并追加“脉”字后缀。</li>
     *   <li>返回所有脉象名称的列表（按原顺序）。</li>
     * </ol>
     *
     * <h3>示例</h3>
     * <pre>{@code
     * splitPulseDescriptions("脉浮而缓")  → ["浮脉", "缓脉"]
     * splitPulseDescriptions("脉沉细")    → ["沉脉", "细脉"]
     * splitPulseDescriptions("浮而缓")    → ["浮脉", "缓脉"]
     * splitPulseDescriptions("脉弦数")    → ["弦脉", "数脉"]
     * splitPulseDescriptions("脉结代")    → ["结脉", "代脉"]
     * splitPulseDescriptions("脉微细")    → ["微脉", "细脉"]
     * splitPulseDescriptions("脉浮紧")    → ["浮脉", "紧脉"]
     * splitPulseDescriptions("脉缓")      → ["缓脉"]
     * splitPulseDescriptions("滑数")      → ["滑脉", "数脉"]
     * splitPulseDescriptions("脉")        → []
     * splitPulseDescriptions(null)        → []
     * }</pre>
     *
     * <h3>注意事项</h3>
     * <ul>
     *   <li>脉象名称仅支持单个中文字符（如“浮”“沉”“缓”等），复合词（如“濡缓”）暂不支持拆分。</li>
     *   <li>输入中的非中文字符（如标点、数字）会被忽略。</li>
     *   <li>返回的列表顺序与输入中出现的顺序一致。</li>
     * </ul>
     *
     * @param input 脉象描述字符串（可为 null、空白或仅含“脉”字）
     * @return 脉象名称列表（每个带有“脉”字后缀），永远不会返回 null
     */
    public static List<String> splitPulseDescriptions(String input) {
        if (input == null || input.isBlank()) {
            return new ArrayList<>();
        }

        // 去除首尾空白
        String trimmed = input.trim();

        // 移除开头的“脉”字（若存在）
        if (trimmed.startsWith("脉")) {
            trimmed = trimmed.substring(1).trim();
        }

        // 如果移除后为空，返回空列表
        if (trimmed.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> result = new ArrayList<>();

        // 先按分隔符拆分（而、，、,、空格等）
        String[] parts = SEPARATOR_PATTERN.split(trimmed);
        for (String part : parts) {
            String cleaned = part.trim();
            if (cleaned.isEmpty()) {
                continue;
            }
            // 移除所有“脉”字符（以免重复）
            cleaned = cleaned.replace("脉", "");
            if (cleaned.isEmpty()) {
                continue;
            }
            // 按字符拆分成独立脉象（每个中文字作为一个脉象名），并添加“脉”字后缀
            for (char ch : cleaned.toCharArray()) {
                // 只保留中文字符（可根据需要调整范围）
                if (Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN) {
                    result.add(String.valueOf(ch) + "脉");
                }
            }
        }

        return result;
    }
}