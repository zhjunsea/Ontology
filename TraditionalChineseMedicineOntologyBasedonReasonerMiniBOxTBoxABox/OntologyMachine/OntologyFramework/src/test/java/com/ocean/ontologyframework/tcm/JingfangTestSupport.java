package com.ocean.ontologyframework.tcm;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceResult;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 经方辨证测试框架 —— 与具体用例完全解耦。
 */
public final class JingfangTestSupport {

    public static final String NS = "http://www.tcm-classics.org/jingfang#";
    public static final String PROCESS_ID = "Process_Jingfang_Diagnosis";

    public static final Set<String> SIX_CHANNELS = Set.of(
            "Taiyangbing", "Yangmingbing", "Shaoyangbing",
            "Taiyinbing", "Shaoyinbing", "Jueyinbing"
    );

    public static final Map<String, List<String>> LJ_ANCHOR_SYMPTOMS = Map.of(
            "Taiyangbing",  List.of("Ehan"),
            "Yangmingbing", List.of("Kouke"),
            "Shaoyangbing", List.of("Wanglaihanre", "Xiongxiekuman"),
            "Taiyinbing",   List.of("Fuman"),
            "Shaoyinbing",  List.of("Danyumei"),
            "Jueyinbing",   List.of("Xiaoke", "Shouzuleng")
    );

    public static final Map<String, List<String>> LJ_ANCHOR_PULSES = Map.of(
            "Taiyangbing",  List.of("Fumai"),
            // 阳明锚点脉象给出「洪脉 + 大脉」两条路：
            // Panju_B7 原文即「口渴 + 洪脉/大脉/黄苔」，三者等价；
            // 仅给洪脉时，病例脉象若含微脉（洪⊥微，如鸡屎白散证之微弦脉）会使锚点被跳过，
            // 补大脉（大⊥小，不与微/弦/沉/紧互斥）后仍有 B7 可用的脉路。
            "Yangmingbing", List.of("Hongmai", "Damai"),
            "Shaoyangbing", List.of("Xianmai"),
            "Taiyinbing",   List.of("Ruomai"),
            // 少阴锚点脉象用「微细脉」而非「沉微脉」：
            // 《伤寒论》281 条提纲原文为「少阴之为病，脉微细，但欲寐也」——
            // 微细脉（Weiximai ⊑ 微脉,细脉,虚）才是提纲脉；
            // 且微细脉不与浮脉互斥（互斥索引仅 浮⊥沉/洪⊥微/滑⊥涩/浮⊥伏），
            // 故少阴咽痛诸证（310–313 条，病例脉浮）不再因锚点被跳过而丢失病性证据。
            "Shaoyinbing",  List.of("Weiximai"),
            "Jueyinbing",   List.of("Weiximai")
    );

    /**
     * 脉象锚点<b>被全部跳过</b>时的「无脉替代症状」——按六经给出不依赖脉象的八纲证据。
     *
     * <p><b>为什么需要</b>：锚点是对六经的粗粒度近似，而某些经的八纲证据<b>全部由脉象承担</b>。
     * 太阳病即典型：太阳病 ≡ 表 ⊓ 阳，而锚点里
     * <ul>
     *   <li>「表」由浮脉承担（{@code Fumai ⊑ Biao}，定义性公理「浮脉主表」）；</li>
     *   <li>「阳」由 {@code Panju_D8}（恶寒+浮脉）承担。</li>
     * </ul>
     * 病例若自带沉脉（浮⊥沉），浮脉锚点被跳过（铁律 63/64），「表」与「阳」<b>同时</b>失去来源，
     * 太阳即推不出 → 六经 null。桂枝新加、大陷胸、桃核承气、栝蒌桂枝等 13 例即此。
     *
     * <p><b>为什么是「项强 + 发热」</b>：
     * <ul>
     *   <li>定「表」：本体中 ⊑ Biao 的单症状只有两个 —— 浮脉（会冲突）与<b>项强</b>
     *       （{@code Xiangqiang ⊑ Biao}，定义性公理；《伤寒论》1条太阳提纲「头项强痛而恶寒」）。</li>
     *   <li>定「阳」：{@code Panju_D7}（恶寒+发热）。《伤寒论》3条「太阳病，或已发热，
     *       或未发热，必恶寒」——发热属太阳病常候，注入不违医理。</li>
     * </ul>
     *
     * <p><b>为什么不直接用 Panju_A5 症状组</b>（恶寒+发热+身痛+腰痛+骨节疼痛）：
     * 其「身痛/腰痛/骨节疼痛」会抬高麻黄汤证、桂枝汤证的命中，实测把小青龙汤证挤成麻黄汤证、
     * 桂枝加葛根汤证挤成桂枝汤证。故只用最小集。
     *
     * <p><b>为什么必须「被全部跳过」才注入</b>：病例自带浮脉时，{@code Panju_D8} 已能定阳，
     * 此时再注入发热会平白抬高桂枝汤证（其证含发热）的命中，把桂枝加葛根汤证、桂枝加厚朴杏子汤证
     * 挤成桂枝汤证。故仅在脉路彻底断绝时才补替代症状。
     *
     * <p>各项仍逐条做互斥检查，与病例四诊冲突者跳过（铁律 63/64）。
     */
    public static final Map<String, List<String>> LJ_ANCHOR_PULSE_FALLBACK_SYMPTOMS = Map.of(
            "Taiyangbing", List.of("Xiangqiang", "Fare")
    );

    private static ZeebeClient client;
    private static volatile boolean initialized = false;

    private JingfangTestSupport() {}

    public static synchronized void ensureInitialized() {
        if (initialized) return;

        Yaml yaml = new Yaml();
        Map<String, Object> config;
        try (InputStream is = JingfangTestSupport.class.getClassLoader()
                .getResourceAsStream("application.yml")) {
            assertThat(is).as("application.yml must exist on classpath").isNotNull();
            config = yaml.load(is);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load application.yml", e);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> ontology = (Map<String, Object>) config.get("ontology");
        String bpmnPath = (String) ontology.get("bpmn-path");
        assertThat(bpmnPath).as("ontology.bpmn-path must be configured").isNotBlank();

        @SuppressWarnings("unchecked")
        Map<String, Object> camunda = (Map<String, Object>) config.get("camunda");
        @SuppressWarnings("unchecked")
        Map<String, Object> clientCfg = (Map<String, Object>) camunda.get("client");
        String grpcAddress = (String) clientCfg.get("grpc-address");
        assertThat(grpcAddress).as("camunda.client.grpc-address must be configured").isNotBlank();

        boolean useTls = grpcAddress.startsWith("https://");
        String hostPort = grpcAddress.replaceFirst("^https?://", "");

        client = useTls
                ? ZeebeClient.newClientBuilder()
                .gatewayAddress(hostPort)
                .defaultRequestTimeout(Duration.ofSeconds(60)).build()
                : ZeebeClient.newClientBuilder()
                .gatewayAddress(hostPort)
                .usePlaintext()
                .defaultRequestTimeout(Duration.ofSeconds(60)).build();

        System.out.println("📂 Deploying BPMN from: " + bpmnPath);
        DeploymentEvent deployment = client.newDeployResourceCommand()
                .addResourceFile(bpmnPath).send().join();
        assertThat(deployment.getProcesses()).hasSize(1);
        System.out.println("✅ 流程已部署，key=" + deployment.getKey()
                + ", version=" + deployment.getProcesses().get(0).getVersion());

        initialized = true;
    }

    public static ProcessInstanceResult startProcessAndGetResult(Map<String, Object> variables) {
        ensureInitialized();
        return client.newCreateInstanceCommand()
                .bpmnProcessId(PROCESS_ID)
                .latestVersion()
                .variables(toProcessVariables(variables))
                .withResult()
                .requestTimeout(Duration.ofSeconds(120))
                .send()
                .join();
    }

    /** 四诊通道：测试预置的是「完整 IRI」，而 symptom-mapping 的输入是 fragment。 */
    private static final List<String> SIZHEN_CHANNELS =
            List.of("symptomIris", "pulseIris", "tongueIris", "fuzhengIris");

    /**
     * 把测试用例预置的「四诊 IRI」翻译成流程首节点 {@code Task_SymptomMapping} 的输入契约。
     *
     * <p><b>为什么必须翻译</b>：改造后的流程在开始事件之后新增了首个节点
     * {@code Task_SymptomMapping}（jobType {@code symptom-mapping}），它的
     * <ul>
     *   <li><b>输入</b>是 {@code userInput}（自然语言）+ {@code confirmed} / {@code rejected} /
     *       {@code extra}（fragment）；</li>
     *   <li><b>输出</b>是 {@code symptomIris} / {@code pulseIris} / {@code tongueIris} /
     *       {@code fuzhengIris}（完整 IRI）。</li>
     * </ul>
     * 测试若仍按改造前的契约直接预置这四个输出变量，映射节点读到的 {@code userInput} 为空、
     * {@code confirmed} 为空，于是映射出<b>空结果并覆盖</b>测试预置的 IRI —— 下游
     * {@code sizhen-input} 拿到空四诊，八纲/六经/方证全部塌陷。
     *
     * <p>因此这里把预置的四诊 IRI 拆成 fragment，走 {@code extra}（文档定义的
     * 「用户手工补充的 fragment」通道，置信度 1.0 直接采纳），既保留测试对下游推理的
     * 精确控制（含六经锚点注入），又不绕过映射节点的契约。
     *
     * <p>若用例已按新契约直接传 {@code userInput}，则原样透传、不做任何改写。
     */
    static Map<String, Object> toProcessVariables(Map<String, Object> variables) {
        Map<String, Object> vars = new LinkedHashMap<>(variables);

        Object ui = vars.get("userInput");
        if (ui instanceof String s && !s.isBlank()) {
            return vars;   // 已按新契约传自然语言，原样透传
        }

        List<String> extra = new ArrayList<>();
        for (String key : SIZHEN_CHANNELS) {
            for (String iri : asStringList(vars.remove(key))) {
                String frag = fragmentOf(iri);
                if (!frag.isBlank() && !extra.contains(frag)) extra.add(frag);
            }
        }
        for (String e : asStringList(vars.get("extra"))) {
            String frag = fragmentOf(e);
            if (!frag.isBlank() && !extra.contains(frag)) extra.add(frag);
        }
        if (!extra.isEmpty()) vars.put("extra", extra);
        vars.putIfAbsent("mappingRound", 0);
        return vars;
    }

    /** 取 IRI 的 fragment 部分（{@code ...#Fare_instance} → {@code Fare_instance}）。 */
    private static String fragmentOf(String iri) {
        if (iri == null) return "";
        String s = iri.trim();
        int hash = s.lastIndexOf('#');
        return hash >= 0 ? s.substring(hash + 1) : s;
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object v) {
        if (v == null) return List.of();
        if (v instanceof List<?> l) {
            List<String> out = new ArrayList<>(l.size());
            for (Object o : l) if (o != null) out.add(String.valueOf(o));
            return out;
        }
        String s = String.valueOf(v);
        return s.isBlank() ? List.of() : List.of(s.split("\\s*,\\s*"));
    }

    @SuppressWarnings("unchecked")
    public static void printResult(String caseName, ProcessInstanceResult result) {
        Map<String, Object> vars = result.getVariablesAsMap();
        Map<String, Object> bagang = (Map<String, Object>) vars.get("bagangResult");

        System.out.println("\n===== " + caseName + " =====");
        if (bagang != null) {
            Map<String, Object> cn = new LinkedHashMap<>();
            cn.put("表里", bagang.get("表里"));
            cn.put("寒热", bagang.get("寒热"));
            cn.put("虚实", bagang.get("虚实"));
            cn.put("阴阳", bagang.get("阴阳"));
            cn.put("bagangTypes", bagang.get("bagangTypesCn") != null
                    ? bagang.get("bagangTypesCn") : bagang.get("bagangTypes"));
            System.out.println("八纲：" + cn);
        } else {
            System.out.println("八纲：null");
        }

        System.out.println("六经：" + getChineseOrOriginal(vars, "sixChannel", "sixChannelCn"));
        System.out.println("方证：" + getChineseOrOriginal(vars, "fangzheng", "fangzhengCn"));
        System.out.println("推荐方剂：" + getChineseOrOriginal(vars, "finalFormula", "finalFormulaCn"));
        System.out.println("药物组成：" + getChineseListOrOriginal(vars, "herbs", "herbsCn"));
        System.out.println("六经列表：" + getChineseListOrOriginal(vars, "liujingTypes", "liujingTypesCn"));
        System.out.println("合病标记：" + vars.get("combinedDiseaseMark"));

        // 命中证据（四诊）：结论方证的命中主证 / 命中或然证
        if (vars.get("matchedMainSymptomsCn") != null) {
            System.out.println("命中主证："
                    + getChineseListOrOriginal(vars, "matchedMainSymptoms", "matchedMainSymptomsCn"));
        }
        if (vars.get("matchedPossSymptomsCn") != null) {
            System.out.println("命中或然证："
                    + getChineseListOrOriginal(vars, "matchedPossSymptoms", "matchedPossSymptomsCn"));
        }
        // 候选方证的证据明细（与候选方证同序）：命中主证 / 缺口 / 命中或然证
        if (vars.get("candidateMissingMainCn") != null) {
            System.out.println("候选命中主证：" + vars.get("candidateMatchedMainCn"));
            System.out.println("候选缺口：" + vars.get("candidateMissingMainCn"));
            System.out.println("候选命中或然证：" + vars.get("candidateMatchedPossCn"));
        }

        if (vars.get("candidateFangzhengs") != null) {
            System.out.println("候选方证："
                    + getChineseListOrOriginal(vars, "candidateFangzhengs", "candidateFangzhengsCn"));
            System.out.println("候选方证打分：" + vars.get("candidateScores"));
        }
        if (vars.get("jianJiaZhengs") != null) {
            System.out.println("兼夹证："
                    + getChineseListOrOriginal(vars, "jianJiaZhengs", "jianJiaZhengsCn"));
        }
        if (vars.get("addedHerb") != null) {
            System.out.println("加味药物："
                    + getChineseListOrOriginal(vars, "addedHerb", "addedHerbCn"));
        }
        if (vars.get("removedHerb") != null) {
            List<String> r = getChineseListOrOriginal(vars, "removedHerb", "removedHerbCn");
            if (!r.isEmpty()) System.out.println("减味药物：" + r);
        }
        if (vars.get("warnings") != null) {
            System.out.println("配伍禁忌警告：" + vars.get("warnings"));
        }
    }

    private static String getChineseOrOriginal(Map<String, Object> vars,
                                               String origKey, String cnKey) {
        Object cn = vars.get(cnKey);
        if (cn instanceof String s && !s.isEmpty()) return s;
        Object orig = vars.get(origKey);
        return orig != null ? orig.toString() : null;
    }

    private static List<String> getChineseListOrOriginal(Map<String, Object> vars,
                                                         String origKey, String cnKey) {
        Object cn = vars.get(cnKey);
        if (cn instanceof List<?> list) {
            return list.stream().map(Object::toString).collect(Collectors.toList());
        }
        Object orig = vars.get(origKey);
        if (orig instanceof List<?> list) {
            return list.stream().map(Object::toString).collect(Collectors.toList());
        }
        return List.of();
    }

    /**
     * 契约守护：首节点 {@code symptom-mapping} 必须把测试预置的四诊 fragment 原样映射回 IRI。
     *
     * <p>测试框架通过 {@code extra} 通道把预置 IRI 的 fragment 交给映射节点
     * （见 {@link #toProcessVariables}）。映射服务对 {@code extra} 的处理是
     * 「查目录 → 命中则以置信度 1.0 采纳」（{@code SymptomMappingService.map} 的 USER_EXTRA 分支），
     * 因此只要 fragment 在 {@code SymptomCatalog} 的四诊目录内，就必然原样出现在输出通道里。
     *
     * <p>若本断言失败，说明两件事之一：
     * <ol>
     *   <li>映射节点的输入/输出契约又被改动（例如不再读 {@code extra}、或覆盖了预置变量）——
     *       此时下游的八纲/六经/方证断言失败只是「症状」，真正的原因在这里；</li>
     *   <li>该 fragment 虽在某个 {@code *.owl} 里存在实例，但<b>不在 SymptomCatalog 加载的四个
     *       ABox 文件</b>（{@code tcm-zhengzhuang/maixiang/shexiang/fuzheng-abox.owl}）内，
     *       于是被映射服务静默丢弃。这种情况必须改测试参数或补 ABox，不能改算法。</li>
     * </ol>
     */
    private static void assertSizhenMapped(ProcessInstanceResult result,
                                           List<String> expectedSyms,
                                           List<String> expectedPulses) {
        Map<String, Object> vars = result.getVariablesAsMap();

        Set<String> actual = new LinkedHashSet<>();
        for (String key : SIZHEN_CHANNELS) {
            actual.addAll(asStringList(vars.get(key)));
        }

        List<String> expected = new ArrayList<>();
        expected.addAll(expectedSyms);
        expected.addAll(expectedPulses);

        List<String> missing = expected.stream()
                .distinct()
                .filter(iri -> !actual.contains(iri))
                .collect(Collectors.toList());

        if (!missing.isEmpty()) {
            List<String> frags = missing.stream().map(JingfangTestSupport::fragmentOf)
                    .collect(Collectors.toList());
            throw new AssertionError(
                    "❌ symptom-mapping 未把预置四诊映射回输出通道（契约破坏）。\n"
                            + "   缺失 IRI     : " + missing + "\n"
                            + "   缺失 fragment: " + frags + "\n"
                            + "   映射摘要     : " + vars.get("mappingSummary") + "\n"
                            + "   未匹配文本   : " + vars.get("unmatchedTexts") + "\n"
                            + "   可能原因：(1) 首节点不再读 extra / 覆盖了预置变量；"
                            + "(2) 该 fragment 不在 SymptomCatalog 的四个 ABox 文件内，被静默丢弃。");
        }
    }

    @SuppressWarnings("unchecked")
    public static void assertBasicResult(ProcessInstanceResult result,
                                         String expectedSixChannel,
                                         String expectedFangzheng,
                                         String expectedFormula) {
        Map<String, Object> vars = result.getVariablesAsMap();

        if (expectedSixChannel != null) {
            List<String> lj = (List<String>) vars.get("liujingTypes");
            if (lj != null && lj.size() > 1) {
                assertThat((String) vars.get("sixChannel"))
                        .isEqualTo(vars.get("combinedDiseaseMark"));
                assertThat(lj).contains(expectedSixChannel);
            } else {
                assertThat(vars.get("sixChannel")).isEqualTo(expectedSixChannel);
            }
        }

        assertThat(vars.get("fangzheng")).isEqualTo(expectedFangzheng);
        assertThat(vars.get("finalFormula")).isEqualTo(expectedFormula);
    }

    @SuppressWarnings("unchecked")
    public static void assertBagang(ProcessInstanceResult result,
                                    List<String> expectedBiaoli,
                                    List<String> expectedXushi,
                                    List<String> expectedYinyang) {
        Map<String, Object> bagang = (Map<String, Object>)
                result.getVariablesAsMap().get("bagangResult");
        assertThat(bagang).isNotNull();

        if (expectedBiaoli != null) {
            assertThat((List<String>) bagang.get("表里"))
                    .containsExactlyInAnyOrderElementsOf(expectedBiaoli);
        }
        if (expectedXushi != null) {
            assertThat((List<String>) bagang.get("虚实"))
                    .containsExactlyInAnyOrderElementsOf(expectedXushi);
        }
        if (expectedYinyang != null) {
            assertThat((List<String>) bagang.get("阴阳"))
                    .containsExactlyInAnyOrderElementsOf(expectedYinyang);
        }
    }

    /** 杂病 → 六经映射（用于锚点注入） */
    public static final Map<String, String> LIUJING_OF_MISC = Map.ofEntries(
            Map.entry("Shibing", "Taiyangbing"),
            Map.entry("Xiongbibing", "Taiyinbing"),
            Map.entry("Feizhangbing", "Taiyangbing"),
            Map.entry("Shuiqibing", "Taiyangbing"),
            Map.entry("Bentunbing", "Taiyangbing"),
            Map.entry("Xuebibing", "Taiyangbing"),
            Map.entry("Jingbing", "Taiyangbing"),
            Map.entry("Jingjibing", "Taiyinbing"),
            Map.entry("Taiyangzhongye", "Taiyangbing"),
            Map.entry("Xulaobing", "Shaoyinbing"),
            Map.entry("Nuebing", "Shaoyangbing"),
            Map.entry("Tanyinbing", "Taiyinbing"),
            Map.entry("Shuixiebing", "Taiyinbing"),
            Map.entry("Outuoyuexialibing", "Taiyinbing"),
            Map.entry("Furenzabing", "Taiyinbing"),
            Map.entry("Furenchanhoubing", "Taiyinbing"),
            Map.entry("Chanhoubing", "Taiyinbing"),
            Map.entry("Renshengbing", "Taiyinbing"),
            Map.entry("Jinchuangbing", "Taiyinbing"),
            Map.entry("Zhuanjinbing", "Taiyinbing"),
            Map.entry("Feiweibing", "Taiyinbing"),
            Map.entry("Ganzhuobing", "Taiyinbing"),
            Map.entry("Feiweifeiyongkesoushangqi", "Taiyinbing"),
            Map.entry("Feiyongbing", "Taiyinbing"),
            Map.entry("Kesoushangqibing", "Taiyinbing"),
            Map.entry("Hanshanbing", "Shaoyinbing"),
            Map.entry("Huangdanbing", "Yangmingbing"),
            Map.entry("Changyongbing", "Yangmingbing"),
            Map.entry("Tunvxiaxuebing", "Yangmingbing"),
            Map.entry("Xiaxuebing", "Taiyinbing"),
            Map.entry("Yinyangdu", "Jueyinbing"),
            Map.entry("Huhuobing", "Jueyinbing"),
            Map.entry("Zhongfengbing", "Jueyinbing"),
            Map.entry("Yinhushanbing", "Jueyinbing"),
            Map.entry("Huichongbing", "Jueyinbing"),
            Map.entry("Chuangyongchangyongjinyinbing", "Jueyinbing"),
            Map.entry("Baihebing", "Shaoyangbing"),
            Map.entry("Lijiebing", "Jueyinbing"),
            Map.entry("Fumanbing", "Yangmingbing"),
            Map.entry("Chahoulaofubing", "Yangmingbing")
    );

    /** 合病名（含分号连写）→ 六经列表 */
    public static final Map<String, List<String>> LIUJING_OF_HEBING = Map.of(
            "Taiyangyangminghebing", List.of("Taiyangbing", "Yangmingbing"),
            "Taiyangshaoyanghebing", List.of("Taiyangbing", "Shaoyangbing"),
            "Yangmingshaoyanghebing", List.of("Yangmingbing", "Shaoyangbing"),
            "TaiyinYangmingHebing",  List.of("Taiyinbing", "Yangmingbing"),
            "ShaoyangTaiyinHebing",  List.of("Shaoyangbing", "Taiyinbing"),
            "TaiyangYangmingHebing", List.of("Taiyangbing", "Yangmingbing"),
            "TaiyangTaiyinHebing",   List.of("Taiyangbing", "Taiyinbing"),
            "Sanyanghebing",         List.of("Taiyangbing", "Yangmingbing", "Shaoyangbing")
    );

    public static boolean isLiujing(String lj) {
        if (lj == null) return false;
        if (SIX_CHANNELS.contains(lj)) return true;
        if (lj.endsWith("hebing") || lj.endsWith("Hebing")) return true;
        return LIUJING_OF_MISC.containsKey(lj);
    }

    /** 解析 lj 为用于锚点注入的六经列表（支持杂病、合病） */
    public static List<String> resolveLiujingForAnchor(String lj) {
        if (lj == null) return List.of();
        // 1. 直接六经
        if (SIX_CHANNELS.contains(lj)) return List.of(lj);
        // 2. 合病（含分号连写）
        List<String> hebing = LIUJING_OF_HEBING.get(lj);
        if (hebing != null) return hebing;
        // 尝试分号拆分（如 "Taiyangbing;Yangmingbing"）
        if (lj.contains(";")) {
            return Arrays.stream(lj.split(";"))
                    .filter(SIX_CHANNELS::contains)
                    .collect(Collectors.toList());
        }
        // 3. 杂病 → 六经
        String misc = LIUJING_OF_MISC.get(lj);
        if (misc != null) return List.of(misc);
        // 4. 连写合病（无分号）—— 按六经名逐段提取
        List<String> result = new ArrayList<>();
        for (String ch : SIX_CHANNELS) {
            String shortName = ch.replace("bing", "");
            if (lj.contains(shortName)) result.add(ch);
        }
        if (!result.isEmpty()) return result;
        return List.of();
    }

    public static List<String> parseIris(String s) {
        if (s == null || s.isBlank()) return List.of();
        List<String> iris = Arrays.stream(s.split(";"))
                .filter(x -> !x.isBlank())
                .map(x -> NS + x + "_instance")
                .collect(Collectors.toList());
        assertInstancesExist(iris);
        return iris;
    }

    // ==================== 症状/脉象/舌象实例存在性校验 ====================
    private static volatile Set<String> knownInstances;

    /** 从 application.yml 的 ontology.main-path 推导本体目录，扫描全部 *.owl 收集实例名 */
    private static Set<String> loadKnownInstances() {
        if (knownInstances != null) return knownInstances;
        synchronized (JingfangTestSupport.class) {
            if (knownInstances != null) return knownInstances;
            Yaml yaml = new Yaml();
            String mainPath;
            try (InputStream is = JingfangTestSupport.class.getClassLoader()
                    .getResourceAsStream("application.yml")) {
                assertThat(is).as("application.yml must exist on classpath").isNotNull();
                @SuppressWarnings("unchecked")
                Map<String, Object> config = yaml.load(is);
                @SuppressWarnings("unchecked")
                Map<String, Object> ontology = (Map<String, Object>) config.get("ontology");
                mainPath = (String) ontology.get("main-path");
            } catch (Exception e) {
                throw new RuntimeException("读取 ontology.main-path 失败", e);
            }
            assertThat(mainPath).as("ontology.main-path must be configured").isNotBlank();

            java.nio.file.Path dir = java.nio.file.Paths.get(mainPath).getParent();
            Set<String> found = new HashSet<>();
            java.util.regex.Pattern p =
                    java.util.regex.Pattern.compile("rdf:about=\"#([A-Za-z0-9_]+)_instance\"");
            try (java.util.stream.Stream<java.nio.file.Path> files =
                         java.nio.file.Files.list(dir)) {
                for (java.nio.file.Path f : files
                        .filter(x -> x.toString().endsWith(".owl"))
                        .collect(Collectors.toList())) {
                    String content = new String(java.nio.file.Files.readAllBytes(f),
                            java.nio.charset.StandardCharsets.UTF_8);
                    java.util.regex.Matcher m = p.matcher(content);
                    while (m.find()) found.add(m.group(1));
                }
            } catch (Exception e) {
                throw new RuntimeException("扫描本体实例失败: " + dir, e);
            }
            knownInstances = found;
            System.out.println("🔍 已加载本体实例清单: " + found.size() + " 个（来源 " + dir + "）");
            return knownInstances;
        }
    }

    // ==================== 互斥（owl:disjointWith）索引 ====================

    private static volatile Map<String, Set<String>> huchiIndex;

    /**
     * 读取本体中的互斥公理（{@code owl:disjointWith}），并按「子类闭包」展开为
     * fragment 级互斥表（与 Worker 的 {@code buildHuchiIndex} 同一口径）。
     *
     * <p><b>为什么测试框架需要它</b>：{@link #assertFangzheng} 会注入「六经锚点」
     * （如 太阳病 → 恶寒 + 浮脉）以固定六经。但锚点是对六经的**粗粒度近似**，
     * 对某些方证并不成立——例如「十枣汤证」属太阳病篇却脉沉弦，若再注入太阳锚点
     * 浮脉，就构造出「浮脉 + 沉弦脉」这种**临床自相矛盾**的输入；同理「大黄甘遂汤证」
     * 不渴，却注入阳明锚点口渴。矛盾输入会被引擎如实判为「四诊参合矛盾」而中止诊断。
     *
     * <p>故此处按本体互斥关系**跳过与病例自身四诊互斥的锚点**——这不是放宽断言，
     * 而是不让测试脚手架编造出医理上不可能存在的四诊组合（铁律 63 / 64）。
     */
    private static Map<String, Set<String>> loadHuchiIndex() {
        if (huchiIndex != null) return huchiIndex;
        synchronized (JingfangTestSupport.class) {
            if (huchiIndex != null) return huchiIndex;
            java.nio.file.Path dir = ontologyDir();
            Map<String, Set<String>> children = new HashMap<>();
            List<String[]> disjoint = new ArrayList<>();
            java.util.regex.Pattern cls = java.util.regex.Pattern.compile(
                    "<owl:Class[^>]*rdf:about=\"#([A-Za-z0-9_]+)\"[^>]*>(.*?)</owl:Class>",
                    java.util.regex.Pattern.DOTALL);
            java.util.regex.Pattern sub = java.util.regex.Pattern.compile(
                    "<rdfs:subClassOf rdf:resource=\"#([A-Za-z0-9_]+)\"");
            java.util.regex.Pattern dis = java.util.regex.Pattern.compile(
                    "<owl:disjointWith rdf:resource=\"#([A-Za-z0-9_]+)\"");
            try (java.util.stream.Stream<java.nio.file.Path> files =
                         java.nio.file.Files.list(dir)) {
                for (java.nio.file.Path f : files
                        .filter(x -> x.toString().endsWith(".owl"))
                        .collect(Collectors.toList())) {
                    String content = new String(java.nio.file.Files.readAllBytes(f),
                            java.nio.charset.StandardCharsets.UTF_8);
                    java.util.regex.Matcher m = cls.matcher(content);
                    while (m.find()) {
                        String self = m.group(1);
                        String body = m.group(2);
                        java.util.regex.Matcher sm = sub.matcher(body);
                        while (sm.find()) {
                            children.computeIfAbsent(sm.group(1), k -> new HashSet<>())
                                    .add(self);
                        }
                        java.util.regex.Matcher dm = dis.matcher(body);
                        while (dm.find()) {
                            disjoint.add(new String[]{self, dm.group(1)});
                        }
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("读取互斥公理失败: " + dir, e);
            }

            Map<String, Set<String>> idx = new HashMap<>();
            for (String[] pair : disjoint) {
                Set<String> a = descendants(pair[0], children);
                Set<String> b = descendants(pair[1], children);
                for (String x : a) {
                    Set<String> s = idx.computeIfAbsent(x, k -> new HashSet<>());
                    s.addAll(b);
                    s.remove(x);
                }
                for (String y : b) {
                    Set<String> s = idx.computeIfAbsent(y, k -> new HashSet<>());
                    s.addAll(a);
                    s.remove(y);
                }
            }
            huchiIndex = idx;
            System.out.println("🔍 已加载互斥索引: " + disjoint.size() + " 对，展开后涉及 "
                    + idx.size() + " 个 fragment（来源 " + dir + "）");
            return idx;
        }
    }

    /** 自身 + 全部具名子类（向下闭包）。 */
    private static Set<String> descendants(String cls, Map<String, Set<String>> children) {
        Set<String> out = new LinkedHashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        stack.push(cls);
        while (!stack.isEmpty()) {
            String c = stack.pop();
            if (!out.add(c)) continue;
            Set<String> kids = children.get(c);
            if (kids != null) kids.forEach(stack::push);
        }
        return out;
    }

    /** 本体目录（由 application.yml 的 ontology.main-path 推导）。 */
    private static java.nio.file.Path ontologyDir() {
        Yaml yaml = new Yaml();
        String mainPath;
        try (InputStream is = JingfangTestSupport.class.getClassLoader()
                .getResourceAsStream("application.yml")) {
            assertThat(is).as("application.yml must exist on classpath").isNotNull();
            @SuppressWarnings("unchecked")
            Map<String, Object> config = yaml.load(is);
            @SuppressWarnings("unchecked")
            Map<String, Object> ontology = (Map<String, Object>) config.get("ontology");
            mainPath = (String) ontology.get("main-path");
        } catch (Exception e) {
            throw new RuntimeException("读取 ontology.main-path 失败", e);
        }
        assertThat(mainPath).as("ontology.main-path must be configured").isNotBlank();
        return java.nio.file.Paths.get(mainPath).getParent();
    }

    /** 由实例 IRI 取 fragment（去掉命名空间与 _instance 后缀）。 */
    private static String fragOf(String iri) {
        String s = iri.startsWith(NS) ? iri.substring(NS.length()) : iri;
        return s.endsWith("_instance") ? s.substring(0, s.length() - "_instance".length()) : s;
    }

    /** frag 是否与 present 中任一 fragment 互斥。 */
    private static boolean conflicts(String frag, Collection<String> present,
                                     Map<String, Set<String>> idx) {
        Set<String> excl = idx.get(frag);
        if (excl == null) return false;
        for (String p : present) {
            if (excl.contains(p)) return true;
        }
        return false;
    }

    /** 校验实例 IRI 是否在本体中真实存在，缺失则抛异常并打印缺失名 */
    private static void assertInstancesExist(List<String> iris) {
        Set<String> known = loadKnownInstances();
        List<String> missing = iris.stream()
                .map(i -> i.substring(NS.length(), i.length() - "_instance".length()))
                .filter(name -> !known.contains(name))
                .distinct()
                .collect(Collectors.toList());
        if (!missing.isEmpty()) {
            throw new AssertionError("❌ 症状/脉象/舌象实例不存在于本体，请核对名称: " + missing);
        }
    }

    public static void assertFangzheng(String name, String lj, String fz, String formula,
                                       String syms, String pulses) {
        assertFangzheng(name, lj, fz, formula, syms, pulses, "", "");
    }

    public static void assertFangzheng(String name, String lj, String fz, String formula,
                                       String syms, String pulses,
                                       String tongues, String fuzhengs) {
        List<String> symList = new ArrayList<>(parseIris(syms));
        List<String> pulseList = new ArrayList<>(parseIris(pulses));

        // 注入六经锚点症状/脉象（支持杂病、合病）。
        // 锚点是对六经的粗粒度近似，对个别方证并不成立（如十枣汤证属太阳病篇却脉沉弦）。
        // 若锚点与病例自身四诊互斥，则跳过——否则会构造出临床自相矛盾的输入，
        // 被引擎如实判为「四诊参合矛盾」而中止诊断（铁律 63/64）。
        Map<String, Set<String>> huchi = loadHuchiIndex();
        Set<String> present = new LinkedHashSet<>();
        symList.forEach(i -> present.add(fragOf(i)));
        pulseList.forEach(i -> present.add(fragOf(i)));
        List<String> anchorChannels = resolveLiujingForAnchor(lj);
        for (String ch : anchorChannels) {
            List<String> anchorSyms = LJ_ANCHOR_SYMPTOMS.get(ch);
            if (anchorSyms != null) {
                for (String s : anchorSyms) {
                    if (conflicts(s, present, huchi)) {
                        System.out.println("⚠️ 跳过与病例四诊互斥的六经锚点症状: " + s + "（" + ch + "）");
                        continue;
                    }
                    String iri = NS + s + "_instance";
                    if (!symList.contains(iri)) { symList.add(iri); present.add(s); }
                }
            }
            List<String> anchorPulses = LJ_ANCHOR_PULSES.get(ch);
            int pulseInjected = 0;
            if (anchorPulses != null) {
                for (String p : anchorPulses) {
                    if (conflicts(p, present, huchi)) {
                        System.out.println("⚠️ 跳过与病例四诊互斥的六经锚点脉象: " + p + "（" + ch + "）");
                        continue;
                    }
                    String iri = NS + p + "_instance";
                    if (!pulseList.contains(iri)) { pulseList.add(iri); present.add(p); }
                    pulseInjected++;
                }
                // 脉路彻底断绝（该经脉象锚点全部被跳过）→ 补「无脉替代症状」。
                // 否则该经的八纲证据（如太阳的「表」与「阳」全由浮脉承担）一并落空，
                // 六经推不出。仅在脉路断绝时补，避免平白扰动方证打分。
                if (pulseInjected == 0) {
                    List<String> fallback = LJ_ANCHOR_PULSE_FALLBACK_SYMPTOMS.get(ch);
                    if (fallback != null) {
                        System.out.println("ℹ️ " + ch + " 脉象锚点全部被跳过，改用无脉替代症状: " + fallback);
                        for (String s : fallback) {
                            if (conflicts(s, present, huchi)) {
                                System.out.println("⚠️ 跳过与病例四诊互斥的替代症状: " + s + "（" + ch + "）");
                                continue;
                            }
                            String iri = NS + s + "_instance";
                            if (!symList.contains(iri)) { symList.add(iri); present.add(s); }
                        }
                    }
                }
            }
        }

        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("symptomIris", symList);
        vars.put("pulseIris", pulseList);
        vars.put("tongueIris", parseIris(tongues));
        vars.put("fuzhengIris", parseIris(fuzhengs));

        ProcessInstanceResult result = startProcessAndGetResult(vars);
        printResult(name, result);

        // 契约守护：首节点 symptom-mapping 必须把 extra 里的四诊 fragment 原样映射回 IRI。
        // 若这里失败，说明映射节点的输入/输出契约又被改动了（例如覆盖了预置变量），
        // 此时下游的八纲/六经/方证断言失败只是「症状」，真正的原因在这里。
        assertSizhenMapped(result, symList, pulseList);

        // 计算期望的六经：优先用解析后的六经（杂病→六经），否则用原lj
        String expectedSix = null;
        if (isLiujing(lj)) {
            List<String> resolved = resolveLiujingForAnchor(lj);
            if (resolved.size() == 1) {
                expectedSix = resolved.get(0);
            } else if (resolved.size() > 1) {
                // 合病：取第一个作为主六经（用于sixChannel断言）
                expectedSix = resolved.get(0);
            } else {
                expectedSix = lj;
            }
        }
        assertBasicResult(result, expectedSix, fz, NS + formula);
    }
}