package com.ocean.ontologyframework.tcm.app;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 症状实例个体目录（Symptom Catalog）。
 *
 * <p>从 ABox 文件直接加载本体中的<b>实例个体</b>（不是类）：
 * <ul>
 *   <li>{@code tcm-zhengzhuang-abox.owl} → 症状（593）</li>
 *   <li>{@code tcm-maixiang-abox.owl}    → 脉象（68）</li>
 *   <li>{@code tcm-shexiang-abox.owl}    → 舌象（72）</li>
 *   <li>{@code tcm-fuzheng-abox.owl}     → 腹证（4）</li>
 * </ul>
 *
 * <p>口语/文言同义词来自配套的 <b>SKOS 词表</b>
 * （{@value #SKOS_FILE}，与 ABox 同目录）：每个 {@code skos:Concept} 的
 * {@code prefLabel} 为规范名，{@code altLabel}（经方/文言同义词）与
 * {@code hiddenLabel}（患者白话）为待映射的表面形式。词表覆盖四诊全部通道
 * （症状 / 脉象 / 舌象 / 腹证），是表面形式映射的<b>唯一权威来源</b>，
 * 匹配引擎不再自行编造或硬编码任何同义词。
 *
 * <p>为什么直接解析文件而不用推理机：目录是<b>静态枚举</b>，不需要推理；
 * 直接解析可让本类独立于 {@code BackendService}（后者初始化依赖 OBDA/MySQL），
 * 从而可被单元测试直接构造。
 */
@Component
public class SymptomCatalog {

    private static final Logger log = LoggerFactory.getLogger(SymptomCatalog.class);

    /** 本体命名空间 */
    public static final String BASE_NS = "http://www.tcm-classics.org/jingfang#";

    /** 症状 SKOS 词表文件名（与 ABox 同目录），症状同义词的唯一权威来源。 */
    public static final String SKOS_FILE = "tcm-zhengzhuang_skos.ttl";

    /** 类别 */
    public enum Category {
        ZHENGZHUANG("症状", "tcm-zhengzhuang-abox.owl"),
        MAIXIANG("脉象", "tcm-maixiang-abox.owl"),
        SHEXIANG("舌象", "tcm-shexiang-abox.owl"),
        FUZHENG("腹证", "tcm-fuzheng-abox.owl");

        private final String cn;
        private final String aboxFile;

        Category(String cn, String aboxFile) {
            this.cn = cn;
            this.aboxFile = aboxFile;
        }

        public String cn() {
            return cn;
        }

        public String aboxFile() {
            return aboxFile;
        }

        /** 从中文名反查类别，找不到返回 null。 */
        public static Category fromCn(String cn) {
            if (cn == null) return null;
            for (Category c : values()) {
                if (c.cn.equals(cn.trim())) return c;
            }
            return null;
        }
    }

    /** 目录项：一个症状实例个体。 */
    public static class Entry {
        private final String fragment;
        private final String label;
        private final Category category;

        public Entry(String fragment, String label, Category category) {
            this.fragment = fragment;
            this.label = label;
            this.category = category;
        }

        public String getFragment() {
            return fragment;
        }

        public String getIri() {
            return BASE_NS + fragment;
        }

        public String getLabel() {
            return label;
        }

        public Category getCategory() {
            return category;
        }

        @Override
        public String toString() {
            return label + "[" + fragment + "]";
        }
    }

    @Value("${ontology.abox-dir:}")
    private String aboxDir;

    @Value("${ontology.main-path:}")
    private String mainOntologyPath;

    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, Entry> byFragment = new LinkedHashMap<>();
    private final Map<String, Entry> byLabel = new LinkedHashMap<>();

    /** 口语 → 规范症状名（仅保留目标 label 确实存在于目录中的条目） */
    private final Map<String, String> aliases = new LinkedHashMap<>();

    public SymptomCatalog() {
    }

    /** 供单元测试直接构造。 */
    public SymptomCatalog(String aboxDir) {
        this.aboxDir = aboxDir;
        load();
    }

    @PostConstruct
    public void init() {
        if (entries.isEmpty()) {
            load();
        }
    }

    // ============================================================
    // 加载
    // ============================================================

    public synchronized void load() {
        entries.clear();
        byFragment.clear();
        byLabel.clear();

        Path dir = resolveAboxDir();
        if (dir == null) {
            log.warn("[SymptomCatalog] 未找到 ABox 目录，症状目录为空（映射将不可用）");
            return;
        }

        for (Category cat : Category.values()) {
            Path f = dir.resolve(cat.aboxFile());
            if (!Files.isRegularFile(f)) {
                log.info("[SymptomCatalog] 跳过不存在的 ABox 文件: {}", f);
                continue;
            }
            int n = loadFile(f, cat);
            log.info("[SymptomCatalog] {} ← {} 个实例个体", cat.cn(), n);
        }

        buildAliases();
        log.info("[SymptomCatalog] 目录加载完成：{} 个实例个体，{} 条口语同义词", entries.size(), aliases.size());
    }

    private Path resolveAboxDir() {
        if (aboxDir != null && !aboxDir.isBlank()) {
            Path p = Paths.get(aboxDir.trim());
            if (Files.isDirectory(p)) return p;
        }
        if (mainOntologyPath != null && !mainOntologyPath.isBlank()) {
            Path main = Paths.get(mainOntologyPath.trim());
            Path parent = main.getParent();
            if (parent != null && Files.isDirectory(parent)) return parent;
        }
        return null;
    }

    private static final Pattern INDIVIDUAL = Pattern.compile(
            "<owl:NamedIndividual\\s+rdf:about=\"#([^\"]+)\"\\s*>(.*?)</owl:NamedIndividual>",
            Pattern.DOTALL);
    private static final Pattern LABEL = Pattern.compile(
            "<rdfs:label\\s+xml:lang=\"zh\">([^<]*)</rdfs:label>");

    private int loadFile(Path file, Category category) {
        String xml;
        try {
            xml = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("[SymptomCatalog] 读取失败: {}", file, e);
            return 0;
        }
        int n = 0;
        Matcher m = INDIVIDUAL.matcher(xml);
        while (m.find()) {
            String fragment = m.group(1).trim();
            String body = m.group(2);
            Matcher lm = LABEL.matcher(body);
            String label = lm.find() ? lm.group(1).trim() : "";
            if (fragment.isEmpty() || label.isEmpty()) continue;
            Entry e = new Entry(fragment, label, category);
            entries.add(e);
            byFragment.put(fragment, e);
            byLabel.putIfAbsent(label, e);
            n++;
        }
        return n;
    }

    /**
     * 口语同义词表。键为用户可能输入的口语表达，值为本体中的规范 label。
     *
     * <p><b>唯一来源：SKOS 词表</b>（{@value #SKOS_FILE}）。
     * 每个 {@code skos:Concept} 的 {@code prefLabel} 为规范名，
     * {@code altLabel}（经方/文言同义词）与 {@code hiddenLabel}（患者白话）
     * 为待映射的表面形式。词表已覆盖四诊全部通道（症状 / 脉象 / 舌象 / 腹证），
     * 因此匹配引擎<b>不再保留任何硬编码同义词表</b>，一切以 SKOS 为准。
     *
     * <p>加载时校验目标 label 是否存在，不存在则丢弃（避免指向不存在的实例）。
     */
    private void buildAliases() {
        int skosN = loadSkosAliases();
        log.info("[SymptomCatalog] 同义词全部来自 SKOS 词表（{}）：{} 条", SKOS_FILE, aliases.size());
    }

    // SKOS TTL 解析（直接读文本，保持本类不依赖推理机）
    private static final Pattern SKOS_CONCEPT = Pattern.compile(
            "^zzskos:\\w+ a skos:Concept ;(.*?)(?=^zzskos:\\w+ a skos:Concept ;|\\Z)",
            Pattern.DOTALL | Pattern.MULTILINE);
    private static final Pattern SKOS_PREF = Pattern.compile(
            "skos:prefLabel\\s+\"([^\"]+)\"@zh");
    private static final Pattern SKOS_LABELS = Pattern.compile(
            "skos:(?:altLabel|hiddenLabel)([^;]*);");
    private static final Pattern SKOS_LABEL_VAL = Pattern.compile(
            "\"([^\"]+)\"@zh");

    /**
     * 从 SKOS 词表加载「表面形式 → 规范症状名」映射。
     *
     * <p>提取每个 {@code skos:Concept} 的 prefLabel / altLabel / hiddenLabel，
     * 仅保留规范名确实存在于目录中的条目（孤儿类因无 ABox 个体而被丢弃）。
     *
     * @return 成功载入的映射条数
     */
    private int loadSkosAliases() {
        Path dir = resolveAboxDir();
        if (dir == null) {
            return 0;
        }
        Path f = dir.resolve(SKOS_FILE);
        if (!Files.isRegularFile(f)) {
            log.warn("[SymptomCatalog] 未找到 SKOS 词表 {}，症状同义词将为空", f);
            return 0;
        }
        String ttl;
        try {
            ttl = Files.readString(f, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("[SymptomCatalog] 读取 SKOS 失败: {}", f, e);
            return 0;
        }
        int n = 0;
        Matcher cm = SKOS_CONCEPT.matcher(ttl);
        while (cm.find()) {
            String body = cm.group(1);
            Matcher pm = SKOS_PREF.matcher(body);
            if (!pm.find()) {
                continue;
            }
            String canonical = pm.group(1).trim();
            if (canonical.isEmpty() || !byLabel.containsKey(canonical)) {
                continue; // 孤儿类（TBox 有类、ABox 无个体）→ 丢弃
            }
            Matcher lm = SKOS_LABELS.matcher(body);
            while (lm.find()) {
                Matcher vm = SKOS_LABEL_VAL.matcher(lm.group(1));
                while (vm.find()) {
                    String surface = vm.group(1).trim();
                    if (surface.isEmpty() || surface.equals(canonical)) {
                        continue;
                    }
                    if (aliases.putIfAbsent(surface, canonical) == null) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    // ============================================================
    // 查询
    // ============================================================

    public List<Entry> all() {
        return Collections.unmodifiableList(entries);
    }

    public int size() {
        return entries.size();
    }

    public long countOf(Category c) {
        return entries.stream().filter(e -> e.getCategory() == c).count();
    }

    public Optional<Entry> byFragment(String fragment) {
        if (fragment == null) return Optional.empty();
        String f = fragment.trim();
        int hash = f.lastIndexOf('#');
        if (hash >= 0) f = f.substring(hash + 1);
        return Optional.ofNullable(byFragment.get(f));
    }

    public Optional<Entry> byLabel(String label) {
        if (label == null) return Optional.empty();
        return Optional.ofNullable(byLabel.get(label.trim()));
    }

    /** 口语同义词 → 规范 label，无则返回 null。 */
    public String aliasTarget(String text) {
        if (text == null) return null;
        return aliases.get(text.trim());
    }

    public Map<String, String> aliases() {
        return Collections.unmodifiableMap(aliases);
    }

    /** 目录中所有可用于「切词」的表面形式：规范 label + 口语同义词键（长度 ≥ 2）。 */
    public List<String> surfaceForms() {
        List<String> forms = new ArrayList<>();
        for (Entry e : entries) {
            if (e.getLabel().length() >= 2) forms.add(e.getLabel());
        }
        for (String k : aliases.keySet()) {
            if (k.length() >= 2) forms.add(k);
        }
        forms.sort((a, b) -> Integer.compare(b.length(), a.length()));
        return forms;
    }

    /** 前端自动补全：按 label 包含关系检索。 */
    public List<Entry> search(String q, Category category, int limit) {
        String key = q == null ? "" : q.trim();
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) {
            if (category != null && e.getCategory() != category) continue;
            if (!key.isEmpty() && !e.getLabel().contains(key) && !e.getFragment().toLowerCase(Locale.ROOT)
                    .contains(key.toLowerCase(Locale.ROOT))) {
                continue;
            }
            out.add(e);
            if (out.size() >= limit) break;
        }
        return out;
    }
}
