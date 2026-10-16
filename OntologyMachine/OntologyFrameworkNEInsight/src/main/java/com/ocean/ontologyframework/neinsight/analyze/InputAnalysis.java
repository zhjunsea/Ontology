package com.ocean.ontologyframework.neinsight.analyze;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ocean.utilities.LlmClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 入口「输入分析」通用能力：从客户输入中抽取洞察所需信息，判定必要信息是否齐全，
 * 并为缺失项生成追问，供流程入口（JobWorker）与 Web 层（Controller 澄清接口）复用。
 *
 * <p><b>与业务无关（★铁律21）</b>：本类<b>不得</b>内置任何具体行业 / 身份 / 客户词表或示例；
 * 所有业务信息一律来自客户输入（自由文本经大模型抽取，或由客户显式提供结构化字段）。
 * 大模型不可用或解析失败时降级为「仅取自由文本作为场景、其余字段留空」的确定性策略——
 * 留空即触发追问，绝不猜测业务。
 *
 * <p>必要信息字段见 {@link #REQUIRED_FIELDS}：{@code scenario}/{@code identity}/{@code industry}/{@code objective}。
 */
public final class InputAnalysis {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 洞察分析前必须齐全的必要信息字段（任一缺失即须向客户追问，不得进入洞察分析）。 */
    public static final List<String> REQUIRED_FIELDS = List.of("scenario", "identity", "industry", "objective");

    /** 可被客户显式覆盖的结构化字段（非空即覆盖抽取结果）。 */
    private static final List<String> OVERLAY_FIELDS =
            List.of("scenario", "identity", "industry", "objective", "customer", "region", "timeframe");

    /**
     * 面向大模型的字段定义系统提示词。
     *
     * <p>刻意不含任何具体行业 / 身份 / 客户示例，避免把业务硬编码进代码：
     * 字段取值范围完全由客户输入决定。
     */
    public static final String SYSTEM_PROMPT =
            "你是资深战略洞察分析师，精通「五看三定」方法论。"
                    + "请解析用户提供的输入文本，抽取后续洞察所需的关键信息，"
                    + "并只输出一个严格的 JSON 对象（不要输出 Markdown 代码块或任何多余文字）。字段定义："
                    + "identity（输入方/咨询发起方的身份或角色——即「我方」或「客户方」是谁，须忠于原文，未知则空串）；"
                    + "scenario（本次待洞察的「业务/主题 + 希望回答的战略问题」的精炼、准确、专业化表述，1-2 句；"
                    + "须忠于原文、不得添加原文没有的信息）；"
                    + "industry（所属行业或领域，须忠于原文，未知则空串）；"
                    + "customer（目标客户，未知则空串）；objective（洞察目标/关注重点，未知则空串）；"
                    + "region（地域范围，无则空串）；timeframe（时间范围，无则空串）；"
                    + "keyEntities（关键实体/技术/政策等字符串数组，无则空数组）；confidence（0~1 的置信度数字）。";

    /** 缺失字段 → 追问话术（业务无关，仅提示客户补充该类信息）。 */
    private static final Map<String, String> QUESTION_TEMPLATES = Map.of(
            "scenario", "请补充或确认本次要洞察的场景：面向什么业务/主题、希望回答哪些战略问题？",
            "identity", "请说明咨询发起方（我方或客户方）在本次洞察中的身份或角色？",
            "industry", "请说明本次洞察所属的行业或领域？",
            "objective", "请说明本次洞察的目标或关注重点？");

    private InputAnalysis() {
    }

    /**
     * 输入分析结果。
     *
     * @param analysis      抽取到的结构化信息（含 identity/scenario/industry/customer/objective/region/timeframe/keyEntities/confidence）
     * @param missingFields 缺失的必要信息字段（空表示齐全）
     * @param questions     针对缺失字段的追问问题（与 {@code missingFields} 一一对应）
     * @param ready         必要信息是否齐全（齐全方可进入洞察分析）
     * @param source        抽取来源：{@code LLM}／{@code HEURISTIC}（确定性降级）／{@code PROVIDED}（客户已显式提供全部必要信息）
     */
    public record Result(Map<String, Object> analysis,
                         List<String> missingFields,
                         List<String> questions,
                         boolean ready,
                         String source) {
    }

    /**
     * 解析客户输入并给出必要信息齐全性判定与追问。
     *
     * @param llm   大模型客户端（可为 {@code null} 或不可用 → 确定性降级，绝不猜测业务）
     * @param input 客户输入：{@code insightScenario}/{@code input}/{@code text} 为自由文本；
     *              另可携带客户已确认的结构化字段（{@link #OVERLAY_FIELDS}，非空则覆盖抽取结果）
     */
    public static Result analyze(LlmClient llm, Map<String, Object> input) {
        Map<String, Object> in = input == null ? Map.of() : input;
        String text = firstText(in, "insightScenario", "input", "text");

        boolean providedComplete = true;
        for (String f : REQUIRED_FIELDS) {
            if (textOrEmpty(in.get(f)).isBlank()) {
                providedComplete = false;
                break;
            }
        }

        String source;
        Map<String, Object> analysis;
        if (providedComplete) {
            // 客户已显式提供全部必要信息：无需调用大模型，直接用客户输入（source=PROVIDED）
            analysis = fallback(text);
            source = "PROVIDED";
        } else {
            source = "HEURISTIC";
            analysis = null;
            if (llm != null && llm.isAvailable()) {
                String reply = llm.chat(SYSTEM_PROMPT, buildUserPrompt(text));
                analysis = parse(reply);
                if (analysis != null) {
                    source = "LLM";
                }
            }
            if (analysis == null) {
                analysis = fallback(text);
            }
        }

        overlay(analysis, in);

        if (textOrEmpty(analysis.get("scenario")).isBlank()) {
            analysis.put("scenario", text == null ? "" : text.trim());
        }

        List<String> missing = missingRequired(analysis);
        return new Result(analysis, missing, questionsFor(missing), missing.isEmpty(), source);
    }

    /** 必要信息中为空的字段列表（顺序与 {@link #REQUIRED_FIELDS} 一致）。 */
    public static List<String> missingRequired(Map<String, Object> analysis) {
        Map<String, Object> a = analysis == null ? Map.of() : analysis;
        List<String> missing = new ArrayList<>();
        for (String f : REQUIRED_FIELDS) {
            if (textOrEmpty(a.get(f)).isBlank()) {
                missing.add(f);
            }
        }
        return missing;
    }

    /** 缺失字段对应的追问问题。 */
    public static List<String> questionsFor(List<String> missingFields) {
        List<String> qs = new ArrayList<>();
        if (missingFields == null) {
            return qs;
        }
        for (String f : missingFields) {
            qs.add(QUESTION_TEMPLATES.getOrDefault(f, "请补充「" + f + "」相关信息。"));
        }
        return qs;
    }

    private static String buildUserPrompt(String raw) {
        return "请解析以下洞察场景输入：\n\n" + (raw == null ? "" : raw);
    }

    /** 客户显式提供的结构化字段覆盖抽取结果（避免大模型丢失客户已确认信息）。 */
    private static void overlay(Map<String, Object> analysis, Map<String, Object> in) {
        for (String f : OVERLAY_FIELDS) {
            String v = textOrEmpty(in.get(f));
            if (!v.isBlank()) {
                analysis.put(f, v);
            }
        }
        Object ents = in.get("keyEntities");
        if (ents instanceof List<?> l && !l.isEmpty()) {
            analysis.put("keyEntities", l);
        }
    }

    /** 解析大模型返回的 JSON（容忍 ```json 代码块包裹）；失败返回 {@code null} 以触发降级。 */
    static Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode n = MAPPER.readTree(stripCodeFence(json.trim()));
            if (n == null || !n.isObject()) {
                return null;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("identity", textOrEmpty(n, "identity"));
            m.put("scenario", textOrEmpty(n, "scenario"));
            m.put("industry", textOrEmpty(n, "industry"));
            m.put("customer", textOrEmpty(n, "customer"));
            m.put("objective", textOrEmpty(n, "objective"));
            m.put("region", textOrEmpty(n, "region"));
            m.put("timeframe", textOrEmpty(n, "timeframe"));
            List<String> entities = new ArrayList<>();
            JsonNode arr = n.path("keyEntities");
            if (arr.isArray()) {
                arr.forEach(x -> {
                    String v = x.asText(null);
                    if (v != null && !v.isBlank()) {
                        entities.add(v.trim());
                    }
                });
            }
            m.put("keyEntities", entities);
            JsonNode conf = n.path("confidence");
            m.put("confidence", conf.isNumber() ? conf.asDouble() : null);
            return m;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 确定性降级：仅将自由文本作为场景，其余字段留空。
     *
     * <p>不内置任何业务词表——留空的必要字段将触发追问，由客户补齐。
     */
    static Map<String, Object> fallback(String text) {
        String t = text == null ? "" : text.trim();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("identity", "");
        m.put("scenario", t);
        m.put("industry", "");
        m.put("customer", "");
        m.put("objective", "");
        m.put("region", "");
        m.put("timeframe", "");
        m.put("keyEntities", new ArrayList<String>());
        m.put("confidence", null);
        return m;
    }

    private static String stripCodeFence(String s) {
        String t = s;
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            if (firstNl >= 0) {
                t = t.substring(firstNl + 1);
            }
            int fence = t.lastIndexOf("```");
            if (fence >= 0) {
                t = t.substring(0, fence);
            }
        }
        return t.trim();
    }

    private static String firstText(Map<String, Object> in, String... keys) {
        for (String k : keys) {
            String v = textOrEmpty(in.get(k));
            if (!v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    private static String textOrEmpty(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static String textOrEmpty(JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return "";
        }
        return v.isTextual() ? v.asText().trim() : v.asText();
    }
}
