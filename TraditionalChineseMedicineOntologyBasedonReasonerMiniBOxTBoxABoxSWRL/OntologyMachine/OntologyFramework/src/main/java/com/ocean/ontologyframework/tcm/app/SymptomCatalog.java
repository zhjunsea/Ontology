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
 *   <li>{@code tcm-zhengzhuang-abox.owl} → 症状（576）</li>
 *   <li>{@code tcm-maixiang-abox.owl}    → 脉象（68）</li>
 *   <li>{@code tcm-shexiang-abox.owl}    → 舌象（72）</li>
 * </ul>
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
     * 加载时会校验目标 label 是否存在，不存在则丢弃（避免指向不存在的实例）。
     */
    private void buildAliases() {
        Map<String, String> raw = new LinkedHashMap<>();
        // —— 寒热 ——
        raw.put("发烧", "发热");
        raw.put("低烧", "微热");
        raw.put("高烧", "大热");
        raw.put("怕冷", "恶寒");
        raw.put("畏寒", "恶寒");
        raw.put("怕风", "恶风");
        raw.put("怕热", "恶热");
        raw.put("忽冷忽热", "往来寒热");
        raw.put("一阵冷一阵热", "往来寒热");
        // —— 汗 ——
        raw.put("出汗", "汗出");
        raw.put("出虚汗", "自汗");
        raw.put("夜里出汗", "盗汗");
        raw.put("睡觉出汗", "盗汗");
        raw.put("不出汗", "无汗");
        // —— 头面 ——
        raw.put("头晕", "头眩");
        raw.put("眩晕", "头眩");
        raw.put("头疼", "头痛");
        raw.put("偏头痛", "头痛");
        raw.put("脸发红", "面赤");
        // —— 五官 ——
        raw.put("口干", "口燥");
        raw.put("嘴干", "口燥");
        raw.put("嗓子干", "咽干");
        raw.put("嗓子疼", "咽痛");
        raw.put("喉咙痛", "咽痛");
        raw.put("耳朵听不见", "耳聋");
        // —— 胸腹 ——
        raw.put("心慌", "心悸");
        raw.put("心跳快", "心悸");
        raw.put("胸闷", "胸满");
        raw.put("胸口堵", "胸中窒");
        raw.put("两边肋骨胀痛", "胸胁苦满");
        raw.put("肋骨下胀痛", "胸胁苦满");
        raw.put("胁肋胀痛", "胸胁苦满");
        raw.put("肚子胀", "腹满");
        raw.put("腹胀", "腹满");
        raw.put("肚子疼", "腹痛");
        raw.put("肚子痛", "腹痛");
        raw.put("胃疼", "心下痛");
        raw.put("胃痛", "心下痛");
        raw.put("胃胀", "心下痞");
        raw.put("胃里堵得慌", "心下痞");
        raw.put("小肚子疼", "少腹痛");
        // —— 消化 ——
        raw.put("没胃口", "不欲食");
        raw.put("食欲不振", "不欲食");
        raw.put("吃不下", "不欲食");
        raw.put("不想吃饭", "不欲食");
        raw.put("恶心", "欲呕");
        raw.put("想吐", "欲呕");
        raw.put("反酸", "吞酸");
        raw.put("烧心", "吞酸");
        raw.put("打嗝", "噫气");
        raw.put("拉肚子", "下利");
        raw.put("腹泻", "下利");
        raw.put("泄泻", "下利");
        raw.put("便秘", "大便难");
        raw.put("大便干", "大便硬");
        raw.put("大便稀", "大便溏");
        raw.put("尿频", "小便数");
        raw.put("尿少", "小便不利");
        raw.put("排尿不畅", "小便不利");
        // —— 呼吸 ——
        raw.put("气短", "短气");
        raw.put("喘不上气", "上气");
        raw.put("有痰", "痰多");
        // —— 神志 ——
        raw.put("睡不着", "不得眠");
        raw.put("失眠", "不得眠");
        raw.put("烦躁", "烦躁");
        raw.put("乏力", "少气");
        raw.put("没劲", "少气");
        raw.put("浑身没劲", "少气");
        raw.put("总想睡觉", "但欲寐");
        raw.put("没精神", "但欲寐");
        // —— 四肢 ——
        raw.put("手脚冰凉", "手足厥逆");
        raw.put("手脚冷", "手足冷");
        raw.put("手脚发凉", "手足冷");
        raw.put("腿肿", "脚肿");
        raw.put("浮肿", "水肿");
        raw.put("身上肿", "身体肿");
        // —— 舌象 ——
        raw.put("舌苔厚", "厚苔");
        raw.put("舌苔白", "白苔");
        raw.put("舌苔黄", "黄苔");
        raw.put("舌头胖", "胖大舌");
        raw.put("舌边有齿痕", "齿痕舌");
        raw.put("舌头红", "红舌");
        raw.put("舌头淡", "淡白舌");
        // —— 脉象 ——
        raw.put("脉搏快", "数脉");
        raw.put("脉搏慢", "迟脉");
        raw.put("脉细", "细脉");
        raw.put("脉弦", "弦脉");

        for (Map.Entry<String, String> e : raw.entrySet()) {
            if (byLabel.containsKey(e.getValue())) {
                aliases.put(e.getKey(), e.getValue());
            }
        }
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
