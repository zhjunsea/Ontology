package com.ocean.ontologyframework.tcm.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 症状实例个体映射服务（三层策略）。
 *
 * <pre>
 *   L1 确定性检索：精确 / 同义词 / 同义词包含 / 子串 / 超串 / 字符 Dice 模糊
 *   L2 LLM 语义映射：把「白名单候选」喂给大模型，要求其只能从白名单中选择（防幻觉）
 *   L3 置信度与确认门控：低于 accept-threshold 的采纳项 + 未匹配项 → 需要人工确认
 * </pre>
 *
 * <p>LLM 不可用时自动降级为纯 L1，应用整体仍可用。
 *
 * <p><b>同义词来源（唯一权威）</b>：本类不含任何硬编码症状同义词表。所有
 * 「口语/文言/缩写 → 本体规范名」的映射一律经 {@link SymptomCatalog} 从配套
 * <b>SKOS 词表</b>（{@code tcm-zhengzhuang_skos.ttl}，覆盖症状 / 脉象 / 舌象 / 腹证
 * 四诊全部通道）加载。需要新增同义词时，只改 SKOS 词表，不改本类代码。
 */
@Component
public class SymptomMappingService {

    private static final Logger log = LoggerFactory.getLogger(SymptomMappingService.class);

    private final SymptomCatalog catalog;
    private final LlmClient llm;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${symptom-mapping.top-k:3}")
    private int topK = 3;

    @Value("${symptom-mapping.accept-threshold:0.85}")
    private double acceptThreshold = 0.85;

    @Value("${symptom-mapping.candidate-threshold:0.50}")
    private double candidateThreshold = 0.50;

    @Value("${symptom-mapping.max-rounds:3}")
    private int maxRounds = 3;

    @Value("${symptom-mapping.llm-candidate-limit:60}")
    private int llmCandidateLimit = 60;

    /**
     * 升级轮（全目录候选）的候选上限。
     *
     * <p>窄候选集靠字面召回构造，无法覆盖「语义远距」表述
     * （如「两边肋骨下面胀痛」→「胸胁苦满」：二者无任何共享字符）。
     * 这类表述在窄候选集下必然被防幻觉校验丢弃，因此需要一次全目录候选的升级调用。
     */
    @Value("${symptom-mapping.llm-full-catalog-limit:800}")
    private int llmFullCatalogLimit = 800;

    public SymptomMappingService(SymptomCatalog catalog, LlmClient llm) {
        this.catalog = catalog;
        this.llm = llm;
    }

    /** 供单元测试调整阈值。 */
    public void configure(int topK, double acceptThreshold, double candidateThreshold,
                          int maxRounds, int llmCandidateLimit) {
        this.topK = topK;
        this.acceptThreshold = acceptThreshold;
        this.candidateThreshold = candidateThreshold;
        this.maxRounds = maxRounds;
        this.llmCandidateLimit = llmCandidateLimit;
    }

    public SymptomCatalog getCatalog() {
        return catalog;
    }

    public int getMaxRounds() {
        return maxRounds;
    }

    // ============================================================
    // 内部数据结构
    // ============================================================

    /** L1 候选 */
    public static class Candidate {
        public final SymptomCatalog.Entry entry;
        public final double score;
        public final String source;

        Candidate(SymptomCatalog.Entry entry, double score, String source) {
            this.entry = entry;
            this.score = score;
            this.source = source;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("fragment", entry.getFragment());
            m.put("label", entry.getLabel());
            m.put("category", entry.getCategory().cn());
            m.put("score", round(score));
            m.put("source", source);
            return m;
        }
    }

    /** 已采纳的匹配 */
    private static class Match {
        final SymptomCatalog.Entry entry;
        final double confidence;
        final String source;
        final String text;

        Match(SymptomCatalog.Entry entry, double confidence, String source, String text) {
            this.entry = entry;
            this.confidence = confidence;
            this.source = source;
            this.text = text;
        }
    }

    /** 待确认项 */
    private static class Ambiguous {
        final String text;
        final List<Candidate> candidates;

        Ambiguous(String text, List<Candidate> candidates) {
            this.text = text;
            this.candidates = candidates;
        }
    }

    /** 待 LLM 处理项 */
    private static class Pending {
        final String text;
        final List<Candidate> l1;

        Pending(String text, List<Candidate> l1) {
            this.text = text;
            this.l1 = l1;
        }
    }

    /** 确定性切词命中的片段 */
    private static class Span {
        final int start;
        final int end;
        final String text;
        final SymptomCatalog.Entry entry;
        final double score;
        final String source;

        Span(int start, int end, String text, SymptomCatalog.Entry entry, double score, String source) {
            this.start = start;
            this.end = end;
            this.text = text;
            this.entry = entry;
            this.score = score;
            this.source = source;
        }
    }

    /** 映射结果 */
    public static class MappingResult {
        public final List<String> symptomIris = new ArrayList<>();
        public final List<String> pulseIris = new ArrayList<>();
        public final List<String> tongueIris = new ArrayList<>();
        public final List<String> fuzhengIris = new ArrayList<>();
        public boolean needsConfirmation;
        public final List<Map<String, Object>> detail = new ArrayList<>();
        public final List<Map<String, Object>> ambiguous = new ArrayList<>();
        public final List<String> unmatched = new ArrayList<>();
        public String summary = "";
        public boolean llmAvailable;
        public int round;

        /**
         * 输出为流程变量。
         *
         * <p><b>注意</b>：{@code symptomIris} 等四个通道输出的是<b>完整 IRI</b>
         * （{@code BASE_NS + fragment}，例如 {@code ...#Fare_instance}），
         * 与 {@code sizhen-input} 及其下游（八纲/六经/方证推理）的入参契约一致。
         * 内部字段 {@link #symptomIris} 仍保存 fragment，便于测试与展示；
         * 需要 fragment 时请读 {@code mappingDetail[*].fragment}。
         */
        public Map<String, Object> toVariables() {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("symptomIris", toIris(symptomIris));
            v.put("pulseIris", toIris(pulseIris));
            v.put("tongueIris", toIris(tongueIris));
            v.put("fuzhengIris", toIris(fuzhengIris));
            v.put("needsConfirmation", needsConfirmation);
            v.put("ambiguousSymptoms", ambiguous);
            v.put("unmatchedTexts", unmatched);
            v.put("mappingDetail", detail);
            v.put("mappingSummary", summary);
            v.put("mappingRound", round);
            v.put("llmAvailable", llmAvailable);
            return v;
        }

        public int matchedCount() {
            return detail.size();
        }

        /** fragment → 完整 IRI；已是绝对 IRI 的原样返回。 */
        private static List<String> toIris(List<String> fragments) {
            List<String> out = new ArrayList<>(fragments.size());
            for (String f : fragments) {
                if (f == null || f.isBlank()) continue;
                out.add(f.startsWith("http://") || f.startsWith("https://")
                        ? f : SymptomCatalog.BASE_NS + f);
            }
            return out;
        }
    }

    // ============================================================
    // 主流程
    // ============================================================

    /**
     * 把自然语言映射到症状实例个体。
     *
     * @param userInput 用户原始输入
     * @param confirmed 用户确认采纳的 fragment
     * @param rejected  用户拒绝的 fragment
     * @param extra     用户手工补充的 fragment
     * @param round     重跑轮次（从 0 开始）
     */
    public MappingResult map(String userInput,
                             List<String> confirmed,
                             List<String> rejected,
                             List<String> extra,
                             int round) {
        MappingResult r = new MappingResult();
        r.round = round;
        r.llmAvailable = llm.isAvailable();

        Set<String> rejectedSet = fragmentSet(rejected);
        Set<String> confirmedSet = fragmentSet(confirmed);
        Set<String> extraSet = fragmentSet(extra);

        List<Match> matches = new ArrayList<>();
        List<Ambiguous> ambiguous = new ArrayList<>();
        List<String> unmatched = new ArrayList<>();
        List<Pending> pending = new ArrayList<>();

        // ---- 用户强制采纳 ----
        for (String f : confirmedSet) {
            catalog.byFragment(f).ifPresent(e -> matches.add(new Match(e, 1.0, "USER_CONFIRMED", e.getLabel())));
        }
        for (String f : extraSet) {
            catalog.byFragment(f).ifPresent(e -> matches.add(new Match(e, 1.0, "USER_EXTRA", e.getLabel())));
        }

        // ---- 切分表述单元 → 逐单元处理 ----
        for (String unit : splitUnits(userInput)) {
            List<Span> spans = segment(unit);
            if (spans.isEmpty()) {
                handleLeftover(unit, rejectedSet, confirmedSet, matches, ambiguous, unmatched, pending);
                continue;
            }
            for (Span s : spans) {
                if (rejectedSet.contains(s.entry.getFragment())) continue;
                matches.add(new Match(s.entry, s.score, s.source, s.text));
            }
            for (String gap : gaps(unit, spans)) {
                handleLeftover(gap, rejectedSet, confirmedSet, matches, ambiguous, unmatched, pending);
            }
        }

        // ---- L2：LLM 批量解析 ----
        if (!pending.isEmpty()) {
            if (llm.isAvailable()) {
                resolveWithLlm(pending, rejectedSet, matches, ambiguous, unmatched);
            } else {
                for (Pending p : pending) unmatched.add(p.text);
            }
        }

        // ---- 汇总 ----
        assemble(r, matches, ambiguous, unmatched, rejectedSet, round > 0);
        return r;
    }

    // ============================================================
    // 汇总
    // ============================================================

    private void assemble(MappingResult r,
                          List<Match> matches,
                          List<Ambiguous> ambiguous,
                          List<String> unmatched,
                          Set<String> rejectedSet,
                          boolean postConfirmation) {
        // 同一 fragment 只保留最高置信度
        Map<String, Match> best = new LinkedHashMap<>();
        for (Match m : matches) {
            if (rejectedSet.contains(m.entry.getFragment())) continue;
            Match prev = best.get(m.entry.getFragment());
            if (prev == null || m.confidence > prev.confidence) best.put(m.entry.getFragment(), m);
        }

        Set<String> ambiguousFrags = new HashSet<>();
        for (Ambiguous a : ambiguous) {
            for (Candidate c : a.candidates) ambiguousFrags.add(c.entry.getFragment());
        }

        boolean needConfirm = false;

        for (Match m : best.values()) {
            boolean low = m.confidence < acceptThreshold;
            if (low) needConfirm = true;

            Map<String, Object> d = new LinkedHashMap<>();
            d.put("text", m.text);
            d.put("fragment", m.entry.getFragment());
            d.put("iri", m.entry.getIri());
            d.put("label", m.entry.getLabel());
            d.put("category", m.entry.getCategory().cn());
            d.put("confidence", round(m.confidence));
            d.put("source", m.source);
            d.put("needsConfirmation", low);
            if (low || ambiguousFrags.contains(m.entry.getFragment())) {
                List<Map<String, Object>> cands = new ArrayList<>();
                for (Candidate c : l1Candidates(m.text)) cands.add(c.toMap());
                d.put("candidates", cands);
            }
            r.detail.add(d);

            switch (m.entry.getCategory()) {
                case MAIXIANG -> r.pulseIris.add(m.entry.getFragment());
                case SHEXIANG -> r.tongueIris.add(m.entry.getFragment());
                case FUZHENG -> r.fuzhengIris.add(m.entry.getFragment());
                default -> r.symptomIris.add(m.entry.getFragment());
            }
        }

        for (Ambiguous a : ambiguous) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("text", a.text);
            List<Map<String, Object>> cs = new ArrayList<>();
            for (Candidate c : a.candidates) cs.add(c.toMap());
            m.put("candidates", cs);
            r.ambiguous.add(m);
            needConfirm = true;
        }

        for (String u : new LinkedHashSet<>(unmatched)) {
            r.unmatched.add(u);
            // 人工确认后的重跑：未匹配项用户已审阅过，不再重复拦截
            if (!postConfirmation) needConfirm = true;
        }

        // 回环上限保护：超过 maxRounds 强制放行，避免死循环
        if (r.round >= maxRounds) {
            needConfirm = false;
        }
        r.needsConfirmation = needConfirm;

        StringBuilder sb = new StringBuilder();
        sb.append("已识别 ").append(r.detail.size()).append(" 项");
        if (!r.ambiguous.isEmpty()) sb.append("，").append(r.ambiguous.size()).append(" 项待确认");
        if (!r.unmatched.isEmpty()) sb.append("，").append(r.unmatched.size()).append(" 项未匹配");
        if (!r.llmAvailable) sb.append("（大模型不可用，已降级为确定性映射）");
        r.summary = sb.toString();
    }

    // ============================================================
    // L1 确定性检索
    // ============================================================

    private void handleLeftover(String text,
                                Set<String> rejectedSet,
                                Set<String> confirmedSet,
                                List<Match> matches,
                                List<Ambiguous> ambiguous,
                                List<String> unmatched,
                                List<Pending> pending) {
        String t = normalize(text);
        if (t.length() < 2) return;

        List<Candidate> cands = l1Candidates(t);
        if (!cands.isEmpty()) {
            Candidate best = cands.get(0);
            // 用户已确认过的项：直接采纳，不再进入待确认列表（保证确认回环能收敛）
            if (confirmedSet.contains(best.entry.getFragment())) {
                matches.add(new Match(best.entry, 1.0, "USER_CONFIRMED", t));
                return;
            }
            if (best.score >= acceptThreshold) {
                if (!rejectedSet.contains(best.entry.getFragment())) {
                    matches.add(new Match(best.entry, best.score, best.source, t));
                }
                return;
            }
            if (best.score >= candidateThreshold) {
                if (!rejectedSet.contains(best.entry.getFragment())) {
                    matches.add(new Match(best.entry, best.score, best.source, t));
                }
                ambiguous.add(new Ambiguous(t, cands));
                return;
            }
        }
        pending.add(new Pending(t, cands));
    }

    private List<Candidate> l1Candidates(String text) {
        String t = normalize(text);
        Map<String, Candidate> best = new LinkedHashMap<>();

        // A. 整串精确
        catalog.byLabel(t).ifPresent(e -> put(best, e, 1.0, "EXACT"));
        // B. 整串同义词
        String aliasTarget = catalog.aliasTarget(t);
        if (aliasTarget != null) {
            catalog.byLabel(aliasTarget).ifPresent(e -> put(best, e, 0.95, "ALIAS"));
        }
        // C. 同义词包含（如「老想吐」包含「想吐」）
        for (Map.Entry<String, String> a : catalog.aliases().entrySet()) {
            String k = a.getKey();
            if (k.length() >= 2 && t.contains(k)) {
                catalog.byLabel(a.getValue()).ifPresent(e -> put(best, e, 0.90, "ALIAS_CONTAINS"));
            }
        }
        // D. 逐条目录打分
        for (SymptomCatalog.Entry e : catalog.all()) {
            String lab = e.getLabel();
            double score = 0;
            String src = null;
            if (t.equals(lab)) {
                score = 1.0;
                src = "EXACT";
            } else if (lab.length() >= 2 && t.contains(lab)) {
                score = 0.80 + 0.2 * ((double) lab.length() / t.length());
                src = "SUBSTRING";
            } else if (lab.length() == 1 && t.contains(lab)) {
                score = 0.75;   // 单字症状：采纳但需确认
                src = "SUBSTRING";
            } else if (t.length() >= 2 && lab.contains(t)) {
                score = 0.70;
                src = "SUPERSET";
            } else {
                double d = dice(t, lab);
                if (d >= 0.5) {
                    score = 0.6 * d;
                    src = "FUZZY";
                }
            }
            if (score > 0) put(best, e, score, src);
        }

        List<Candidate> out = new ArrayList<>(best.values());
        out.sort(Comparator.comparingDouble((Candidate c) -> c.score).reversed());
        if (out.size() > topK) out = new ArrayList<>(out.subList(0, topK));
        return out;
    }

    private static void put(Map<String, Candidate> best, SymptomCatalog.Entry e, double score, String src) {
        Candidate prev = best.get(e.getFragment());
        if (prev == null || score > prev.score) {
            best.put(e.getFragment(), new Candidate(e, score, src));
        }
    }

    // ============================================================
    // 确定性切词
    // ============================================================

    private volatile List<String> surfaceFormsCache;

    private List<String> surfaceForms() {
        List<String> f = surfaceFormsCache;
        if (f == null) {
            f = catalog.surfaceForms();
            surfaceFormsCache = f;
        }
        return f;
    }

    /** 在单元文本中做最长匹配切词，命中规范 label 或口语同义词。 */
    private List<Span> segment(String unit) {
        List<Span> spans = new ArrayList<>();
        List<String> forms = surfaceForms();
        int i = 0;
        while (i < unit.length()) {
            boolean hit = false;
            for (String f : forms) {
                if (unit.startsWith(f, i)) {
                    SymptomCatalog.Entry e = resolveSurface(f);
                    if (e != null) {
                        boolean isAlias = catalog.aliasTarget(f) != null;
                        spans.add(new Span(i, i + f.length(), f, e,
                                isAlias ? 0.95 : 1.0, isAlias ? "ALIAS" : "EXACT"));
                        i += f.length();
                        hit = true;
                        break;
                    }
                }
            }
            if (!hit) i++;
        }
        return spans;
    }

    private SymptomCatalog.Entry resolveSurface(String form) {
        String alias = catalog.aliasTarget(form);
        if (alias != null) {
            Optional<SymptomCatalog.Entry> e = catalog.byLabel(alias);
            if (e.isPresent()) return e.get();
        }
        return catalog.byLabel(form).orElse(null);
    }

    /** 切词后未被覆盖的文本片段（长度 ≥ 2）。 */
    private List<String> gaps(String unit, List<Span> spans) {
        List<String> out = new ArrayList<>();
        int cursor = 0;
        for (Span s : spans) {
            if (s.start > cursor) {
                String g = unit.substring(cursor, s.start).trim();
                if (g.length() >= 2) out.add(g);
            }
            cursor = Math.max(cursor, s.end);
        }
        if (cursor < unit.length()) {
            String g = unit.substring(cursor).trim();
            if (g.length() >= 2) out.add(g);
        }
        return out;
    }

    // ============================================================
    // L2 LLM 语义映射
    // ============================================================

    /**
     * L2 语义映射：<b>递进式召回</b>。
     *
     * <ol>
     *   <li><b>第一轮（窄候选集）</b>：候选 = 各待处理项的 L1 候选并集 + 字符召回补足。
     *       提示词小、精度高，能覆盖绝大多数「字面相近但未精确命中」的表述。</li>
     *   <li><b>第二轮（全目录候选）</b>：仅对第一轮仍未解析的表述再调一次大模型，
     *       候选 = 整个症状目录。用于「语义远距」表述——例如
     *       「两边肋骨下面胀痛」→「胸胁苦满」，二者无任何共享字符，
     *       任何字面召回都召不回，只有把整份目录交给大模型才有机会命中。</li>
     * </ol>
     *
     * <p>两轮的防幻觉判据一致：LLM 给出的 fragment 必须存在于<b>本次候选清单</b>中，
     * 而候选清单始终是症状目录的子集，因此「发明目录外症状」永远被丢弃。
     */
    private void resolveWithLlm(List<Pending> pending,
                                Set<String> rejectedSet,
                                List<Match> matches,
                                List<Ambiguous> ambiguous,
                                List<String> unmatched) {
        // ---- 第一轮：窄候选集 ----
        List<SymptomCatalog.Entry> tier1 = buildWhitelist(pending, llmCandidateLimit);
        Map<String, Pick> picks = askLlm(tier1, pending);

        List<Pending> unresolved = new ArrayList<>();
        for (Pending p : pending) {
            Pick pick = picks.get(p.text);
            if (pick == null) unresolved.add(p);
            else acceptPick(p, pick, rejectedSet, matches, ambiguous);
        }
        if (unresolved.isEmpty()) return;

        // ---- 第二轮：全目录候选（语义远距升级） ----
        List<SymptomCatalog.Entry> tier2 = fullCatalogCandidates();
        if (tier2.size() <= tier1.size()) {
            // 目录本身就不比窄候选集大，升级无意义 → 直接判为未匹配
            for (Pending p : unresolved) unmatched.add(p.text);
            return;
        }
        log.info("[SymptomMapping] 窄候选集（{} 项）未覆盖 {} 项表述，升级为全目录候选（{} 项）",
                tier1.size(), unresolved.size(), tier2.size());
        Map<String, Pick> picks2 = askLlm(tier2, unresolved);
        for (Pending p : unresolved) {
            Pick pick = picks2.get(p.text);
            if (pick == null) unmatched.add(p.text);
            else acceptPick(p, pick, rejectedSet, matches, ambiguous);
        }
    }

    /** 窄候选集：各待处理项的 L1 候选并集 + 字符召回补足（上限 {@code limit}）。 */
    private List<SymptomCatalog.Entry> buildWhitelist(List<Pending> pending, int limit) {
        Map<String, SymptomCatalog.Entry> whitelist = new LinkedHashMap<>();
        for (Pending p : pending) {
            for (Candidate c : p.l1) whitelist.putIfAbsent(c.entry.getFragment(), c.entry);
        }
        if (whitelist.size() < 20) {
            Set<Character> chars = new HashSet<>();
            for (Pending p : pending) for (char ch : p.text.toCharArray()) chars.add(ch);
            for (SymptomCatalog.Entry e : catalog.all()) {
                if (whitelist.size() >= limit) break;
                for (char ch : e.getLabel().toCharArray()) {
                    if (chars.contains(ch)) {
                        whitelist.putIfAbsent(e.getFragment(), e);
                        break;
                    }
                }
            }
        }
        List<SymptomCatalog.Entry> wl = new ArrayList<>(whitelist.values());
        if (wl.size() > limit) wl = wl.subList(0, limit);
        return wl;
    }

    /** 全目录候选（受 {@code llm-full-catalog-limit} 约束，防止提示词无限膨胀）。 */
    private List<SymptomCatalog.Entry> fullCatalogCandidates() {
        List<SymptomCatalog.Entry> all = catalog.all();
        if (all.size() <= llmFullCatalogLimit) return all;
        log.warn("[SymptomMapping] 症状目录 {} 项超过 llm-full-catalog-limit={}，已截断",
                all.size(), llmFullCatalogLimit);
        return new ArrayList<>(all.subList(0, llmFullCatalogLimit));
    }

    /**
     * 一次 LLM 调用：给定候选清单，返回 {@code 表述文本 → 采纳项}。
     *
     * <p>只返回至少命中一个合法 fragment 的表述；其余留给调用方决定是否升级召回。
     */
    private Map<String, Pick> askLlm(List<SymptomCatalog.Entry> candidates, List<Pending> pending) {
        Map<String, Pick> out = new LinkedHashMap<>();
        if (candidates.isEmpty() || pending.isEmpty()) return out;

        Map<String, SymptomCatalog.Entry> byFrag = new LinkedHashMap<>();
        for (SymptomCatalog.Entry e : candidates) byFrag.put(e.getFragment(), e);

        String system = """
                你是中医症状标准化助手。你的唯一任务是把患者的口语表述映射到给定的候选症状清单。
                硬性规则：
                1. 只能从候选清单中选择，绝对不得发明清单之外的任何症状。
                2. 若某个表述在清单中确实找不到合适项，把它放进 unmatched。
                3. 只输出 JSON，不要输出任何解释性文字、不要加 markdown 代码块。
                """;

        StringBuilder sb = new StringBuilder();
        sb.append("【候选清单】（格式：fragment|规范名|类别）\n");
        for (SymptomCatalog.Entry e : candidates) {
            sb.append(e.getFragment()).append('|').append(e.getLabel()).append('|')
                    .append(e.getCategory().cn()).append('\n');
        }
        sb.append("\n【待映射表述】\n");
        for (Pending p : pending) sb.append("- ").append(p.text).append('\n');
        sb.append("""
                
                【输出格式】严格 JSON：
                {"matches":[{"text":"原表述","fragments":["fragment1"],"confidence":0.9,"reason":"简要理由"}],"unmatched":["无法映射的表述"]}
                """);

        String raw = llm.chat(system, sb.toString());
        if (raw == null) {
            log.warn("[SymptomMapping] LLM 无响应，{} 项表述本轮未解析", pending.size());
            return out;
        }

        JsonNode root = parseJson(raw);
        if (root == null) {
            log.warn("[SymptomMapping] LLM 输出无法解析为 JSON，本轮降级。原文片段: {}", abbreviate(raw));
            return out;
        }

        JsonNode ms = root.path("matches");
        if (!ms.isArray()) return out;
        for (JsonNode m : ms) {
            String t = m.path("text").asText("").trim();
            if (t.isEmpty()) continue;
            double conf = m.path("confidence").asDouble(0.0);
            List<SymptomCatalog.Entry> picked = new ArrayList<>();
            JsonNode frs = m.path("fragments");
            if (frs.isArray()) {
                for (JsonNode fr : frs) {
                    String frag = fr.asText("").trim();
                    if (frag.isEmpty()) continue;
                    // 防幻觉：必须存在于本次候选清单（= 症状目录子集）中
                    SymptomCatalog.Entry e = byFrag.get(frag);
                    if (e == null) {
                        log.warn("[SymptomMapping] 丢弃候选清单外片段（防幻觉）: {}", frag);
                        continue;
                    }
                    picked.add(e);
                }
            }
            if (!picked.isEmpty()) out.put(t, new Pick(picked, conf));
        }
        return out;
    }

    /** 采纳一次 LLM 选择；低置信度项同时登记为待确认。 */
    private void acceptPick(Pending p, Pick pick,
                            Set<String> rejectedSet,
                            List<Match> matches,
                            List<Ambiguous> ambiguous) {
        for (SymptomCatalog.Entry e : pick.entries) {
            if (rejectedSet.contains(e.getFragment())) continue;
            double c = pick.confidence <= 0 ? 0.6 : pick.confidence;
            matches.add(new Match(e, c, "LLM", p.text));
            if (c < acceptThreshold) {
                List<Candidate> cands = new ArrayList<>();
                cands.add(new Candidate(e, c, "LLM"));
                for (Candidate l1c : p.l1) {
                    if (!l1c.entry.getFragment().equals(e.getFragment())) cands.add(l1c);
                }
                ambiguous.add(new Ambiguous(p.text, cands));
            }
        }
    }

    /** 一次 LLM 调用的采纳结果。 */
    private static class Pick {
        final List<SymptomCatalog.Entry> entries;
        final double confidence;

        Pick(List<SymptomCatalog.Entry> entries, double confidence) {
            this.entries = entries;
            this.confidence = confidence;
        }
    }

    private JsonNode parseJson(String raw) {
        String s = raw.trim();
        // 去掉可能的 markdown 代码块围栏
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            if (firstNl > 0) s = s.substring(firstNl + 1);
            int fence = s.lastIndexOf("```");
            if (fence >= 0) s = s.substring(0, fence);
            s = s.trim();
        }
        // 截取第一个 { 到最后一个 }
        int l = s.indexOf('{');
        int r = s.lastIndexOf('}');
        if (l >= 0 && r > l) s = s.substring(l, r + 1);
        try {
            return mapper.readTree(s);
        } catch (Exception e) {
            return null;
        }
    }

    // ============================================================
    // 文本工具
    // ============================================================

    private static final Pattern UNIT_SPLIT = Pattern.compile("[，,。.；;、！!？?\\n\\r\\t 　]+");

    private static final Pattern LEADING_FILLER = Pattern.compile(
            "^(我|还有|而且|并且|同时|另外|以及|加上|伴有|伴|就是|有点|比较|感觉|觉得|最近|一直|经常|老是|老|很|挺|特别|非常|稍微|有时|偶尔|还|又)+");

    /** 把自然语言切成表述单元。 */
    public static List<String> splitUnits(String input) {
        List<String> out = new ArrayList<>();
        if (input == null) return out;
        for (String part : UNIT_SPLIT.split(input)) {
            String s = part.trim();
            if (s.isEmpty()) continue;
            for (int i = 0; i < 3; i++) {
                String stripped = LEADING_FILLER.matcher(s).replaceFirst("");
                if (stripped.equals(s) || stripped.length() < 2) break;
                s = stripped;
            }
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.trim().replaceAll("\\s+", "");
    }

    /** 字符 Dice 系数（多重集）。 */
    static double dice(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        Map<Character, Integer> ca = new HashMap<>();
        for (char c : a.toCharArray()) ca.merge(c, 1, Integer::sum);
        Map<Character, Integer> cb = new HashMap<>();
        for (char c : b.toCharArray()) cb.merge(c, 1, Integer::sum);
        int inter = 0;
        for (Map.Entry<Character, Integer> e : ca.entrySet()) {
            Integer o = cb.get(e.getKey());
            if (o != null) inter += Math.min(e.getValue(), o);
        }
        return 2.0 * inter / (a.length() + b.length());
    }

    private static Set<String> fragmentSet(List<String> iris) {
        Set<String> s = new LinkedHashSet<>();
        if (iris == null) return s;
        for (String i : iris) {
            if (i == null || i.isBlank()) continue;
            String f = i.trim();
            int hash = f.lastIndexOf('#');
            if (hash >= 0) f = f.substring(hash + 1);
            s.add(f);
        }
        return s;
    }

    private static double round(double d) {
        return Math.round(d * 1000.0) / 1000.0;
    }

    private static String abbreviate(String s) {
        if (s == null) return "";
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }
}
