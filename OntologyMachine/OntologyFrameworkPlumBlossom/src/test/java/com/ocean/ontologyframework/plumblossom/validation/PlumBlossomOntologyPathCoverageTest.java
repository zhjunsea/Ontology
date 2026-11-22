package com.ocean.ontologyframework.plumblossom.validation;

import com.ocean.ontologyframework.plumblossom.PlumBlossomOntologyJobWorker;
import com.ocean.openlletresolver.BackendService;
import com.ocean.ontopobdahandler.OBDAHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 本体域知识装载 + 「本体优先」分支全覆盖（A 类·离线，假 OBDA 返回行驱动）。
 *
 * <p>以 Mockito 桩 BackendService/OBDAHandler，按 SPARQL 体返回受控行，反射驱动私有
 * loadOntologyDomain()/loadDomainRelations()/loadTrigramKnowledge()/verifyOntologyKnowledge()，
 * 覆盖「装载成功 / 条目不足 / 异常回退 / 互校不一致」诸分支；并在字段被本体数据覆盖后，逐一驱动
 * generates/overcomes/vitalityOf/fivePhaseOf/trigramNumberOf/directionOf/dirToTrigram/fivePhaseOfColor/
 * loreMap/loreMapInt/categoryRulePair/qianKunDiseaseYao/tuXiangBuLu/scoreToLevel/jw/resolvePreceptName/
 * preceptAdmonition 的「本体优先」分支。纯离线；测试后还原全部静态字段，避免污染同 JVM 其它测试。
 */
@DisplayName("本体域知识装载与本体优先分支（假 OBDA 驱动·离线）")
public class PlumBlossomOntologyPathCoverageTest {

    private PlumBlossomOntologyJobWorker worker;
    private OBDAHandler obda;
    private BackendService backend;

    private final Map<String, Object> snapshot = new LinkedHashMap<>();

    private static final String[] STATIC_FIELDS = {
            "ontoGenerates", "ontoOvercomes", "ontoFiveDir", "ontoFiveColor", "ontoFiveSeason",
            "ontoFiveObject", "ontoSeasonVitality", "ontoBodyUseName", "ontoBodyUseLabel",
            "ontoAppendixText", "ontoZuoDuan", "ontoShiDaYing", "ontoGuaYing", "ontoXiangZi",
            "ontoLore", "ontoJuanText", "TRI_NAME", "TRI_FIVE", "TRI_DIR", "TRI_ROLE",
            "TRI_NATURE", "TRI_MAIN", "TRI_KEYWORDS"
    };

    @BeforeEach
    void setUp() throws Exception {
        worker = new PlumBlossomOntologyJobWorker();
        obda = mock(OBDAHandler.class);
        backend = mock(BackendService.class);
        when(backend.getObdaHandler()).thenReturn(obda);
        for (String f : STATIC_FIELDS) {
            snapshot.put(f, getStatic(f));
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        for (String f : STATIC_FIELDS) {
            setStatic(f, snapshot.get(f));
        }
    }

    private static Object getStatic(String name) throws Exception {
        Field f = PlumBlossomOntologyJobWorker.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(null);
    }

    private static void setStatic(String name, Object val) throws Exception {
        Field f = PlumBlossomOntologyJobWorker.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(null, val);
    }

    private void setBackend(BackendService b) throws Exception {
        Class<?> sup = PlumBlossomOntologyJobWorker.class.getSuperclass();
        Field f = sup.getDeclaredField("backendService");
        f.setAccessible(true);
        f.set(worker, b);
    }

    private Object call(String name, Class<?>[] types, Object... args) throws Exception {
        Method m = PlumBlossomOntologyJobWorker.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(worker, args);
    }

    private static Object callStatic(String name, Class<?>[] types, Object... args) throws Exception {
        Method m = PlumBlossomOntologyJobWorker.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    private void setInstance(String name, Object val) throws Exception {
        Field f = PlumBlossomOntologyJobWorker.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(worker, val);
    }

    private Object getInstanceBool(String name) throws Exception {
        Field f = PlumBlossomOntologyJobWorker.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(worker);
    }

    private static Map<String, String> row(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    /** 假 OBDA：按 SPARQL 体路由返回受控行。 */
    private static List<Map<String, String>> route(String q) {
        List<Map<String, String>> out = new ArrayList<>();
        if (q.contains("core:trigramFamilyRole")) {
            String[][] d = {
                    {"1", "乾", "金", "西北", "父", "健", "天"}, {"2", "兑", "金", "西", "少女", "说", "泽"},
                    {"3", "离", "火", "南", "中女", "丽", "火"}, {"4", "震", "木", "东", "长男", "动", "雷"},
                    {"5", "巽", "木", "东南", "长女", "入", "风"}, {"6", "坎", "水", "北", "中男", "陷", "水"},
                    {"7", "艮", "土", "东北", "少男", "止", "山"}, {"8", "坤", "土", "西南", "母", "顺", "地"}};
            for (String[] r : d) out.add(row("num", r[0], "lab", r[1], "fl", r[2], "dl", r[3], "role", r[4], "nat", r[5], "main", r[6]));
        } else if (q.contains("rel:hasFivePhase ?fp")) {
            for (String[] r : new String[][]{{"乾", "金"}, {"兑", "金"}, {"离", "火"}, {"震", "木"}, {"巽", "木"}, {"坎", "水"}, {"艮", "土"}, {"坤", "土"}})
                out.add(row("label", r[0], "five", r[1]));
        } else if (q.contains("core:hasDirection ?d . ?d rdfs:label ?dir")) {
            for (String[] r : new String[][]{{"乾", "西北"}, {"兑", "西"}, {"离", "南"}, {"震", "东"}, {"巽", "东南"}, {"坎", "北"}, {"艮", "东北"}, {"坤", "西南"}})
                out.add(row("label", r[0], "dir", r[1]));
        } else if (q.contains("core:trigramNumber ?num")) {
            for (String[] r : new String[][]{{"乾", "1"}, {"兑", "2"}, {"离", "3"}, {"震", "4"}, {"巽", "5"}, {"坎", "6"}, {"艮", "7"}, {"坤", "8"}})
                out.add(row("label", r[0], "num", r[1]));
        } else if (q.contains("div:Precept")) {
            out.add(row("label", "无事不占", "adm", "无具体事项不可起卦。"));
            out.add(row("label", "不诚不占", "adm", "意念不纯，请诚心再问。"));
            out.add(row("label", "不义不占", "adm", "此事悖于道义，不可占。"));
            out.add(row("label", "重卦不占", "adm", "一事一卦，反复求卦反失其机。"));
        } else if (q.contains("jud:Auspiciousness")) {
            out.add(row("label", "大吉", "lv", "5")); out.add(row("label", "吉", "lv", "4"));
            out.add(row("label", "平", "lv", "3")); out.add(row("label", "凶", "lv", "2"));
            out.add(row("label", "大凶", "lv", "1"));
        } else if (q.contains("div:AppendixText") || q.contains("div:JuanTextEntry")) {
            // 留空，避免反射回写内置常量（非本任务缺口）
        } else if (q.contains("div:ZuoDuanEntry")) {
            out.add(row("k", "乾", "t", "乾宫应兆"));
        } else if (q.contains("ome:TenResponses")) {
            out.add(row("k", "裹头", "t", "七裹头断辞"));
        } else if (q.contains("sym:ImageSymbol")) {
            out.add(row("k", "乾", "t", "卦应断辞"));
        } else if (q.contains("sym:XiangZiRule")) {
            out.add(row("cat", "分类断法", "k", "某事", "t", "某事诀"));
        } else if (q.contains("div:LoreTextEntry")) {
            out.add(row("c", "RI_YING", "k", "木", "t", "寅卯木日应"));
            out.add(row("c", "SHENG_SHU", "k", "木", "t", "3"));
            out.add(row("c", "SHENG_SHU", "k", "火", "t", "非法值"));
            out.add(row("c", "CATEGORY_RULE_GOOD", "k", "求财", "t", "体克用有财"));
            out.add(row("c", "CATEGORY_RULE_BAD", "k", "求财", "t", "克体破财"));
            out.add(row("c", "QIAN_KUN_DISEASE_YAO", "k", "1", "t", "初爻动变震木"));
            out.add(row("c", "TU_XIANG_NAME", "k", "图一", "t", "八卦方位图"));
        } else if (q.contains("jud:JudgeWeight")) {
            for (String it : new String[]{"base_1", "base_2", "base_3", "base_4", "base_5", "clamp_max", "clamp_min",
                    "sheng_body", "bihe_body", "ke_body", "tishi_body", "vitality_wang_xiang", "vitality_si", "omen_good", "omen_bad"})
                out.add(row("item", it, "w", "1"));
        } else if (q.contains("rel:generates")) {
            for (String[] r : new String[][]{{"Jin", "Shui"}, {"Shui", "Mu"}, {"Mu", "Huo"}, {"Huo", "Tu"}, {"Tu", "Jin"}})
                out.add(row("s", "http://example.org/plumblossom/relations/" + r[0], "o", "http://example.org/plumblossom/relations/" + r[1]));
        } else if (q.contains("rel:overcomes")) {
            for (String[] r : new String[][]{{"Jin", "Mu"}, {"Mu", "Tu"}, {"Tu", "Shui"}, {"Shui", "Huo"}, {"Huo", "Jin"}})
                out.add(row("s", "http://example.org/plumblossom/relations/" + r[0], "o", "http://example.org/plumblossom/relations/" + r[1]));
        } else if (q.contains("rel:fiveDirection")) {
            for (String[] r : new String[][]{{"木", "东方", "青、碧、绿", "春", "草木"}, {"火", "南方", "红、紫、赤", "夏", "火"},
                    {"土", "中央", "黄", "四季", "土"}, {"金", "西方", "白", "秋", "金"}, {"水", "北方", "黑", "冬", "水"}})
                out.add(row("lab", r[0], "d", r[1], "c", r[2], "sea", r[3], "ob", r[4]));
        } else if (q.contains("rel:SeasonVitality")) {
            for (String p : new String[]{"木", "火", "土", "金", "水"})
                for (String s : new String[]{"春", "夏", "秋", "冬", "四季"})
                    out.add(row("lab", p, "sea", s, "v", p.equals("木") && s.equals("春") ? "旺" : "平"));
        } else if (q.contains("rel:BodyUseLevel")) {
            for (String[] r : new String[][]{{"1", "用生体", "大吉"}, {"2", "比和", "吉"}, {"3", "体克用", "平"}, {"4", "体生用", "凶"}, {"5", "用克体", "大凶"}})
                out.add(row("lv", r[0], "nm", r[1], "au", r[2]));
        } else if (q.contains("yijing:HexagramSequence")) {
            out.add(row("label", "1.乾为天")); out.add(row("label", "2.坤为地"));
        }
        return out;
    }

    private void stubOk() {
        when(obda.executeAboxQuery(anyString())).thenAnswer(inv -> route(inv.getArgument(0)));
    }

    @Test
    @DisplayName("OP-01 loadOntologyDomain 全量装载成功 → 域知识就绪且各关系域装载 + 本体优先分支")
    @SuppressWarnings("unchecked")
    void loadAllSuccess() throws Exception {
        stubOk();
        setBackend(backend);
        call("loadOntologyDomain", new Class<?>[]{});
        assertTrue((Boolean) getInstanceBool("ontologyDomainReady"), "域知识应就绪");
        assertEquals(5, ((Set<String>) getStatic("ontoGenerates")).size());
        assertEquals(5, ((Set<String>) getStatic("ontoOvercomes")).size());
        assertEquals(25, ((Map<String, String>) getStatic("ontoSeasonVitality")).size());
        assertEquals(5, ((Map<String, String>) getStatic("ontoFiveColor")).size());
        assertEquals(5, ((Map<Integer, String>) getStatic("ontoBodyUseName")).size());

        assertTrue((Boolean) callStatic("generates", new Class<?>[]{String.class, String.class}, "金", "水"));
        assertTrue((Boolean) callStatic("overcomes", new Class<?>[]{String.class, String.class}, "金", "木"));
        assertEquals("旺", callStatic("vitalityOf", new Class<?>[]{String.class, String.class}, "木", "春"));
        assertEquals("金", call("fivePhaseOf", new Class<?>[]{String.class}, "乾"));
        assertEquals(1, call("trigramNumberOf", new Class<?>[]{String.class}, "乾"));
        assertEquals("西北", call("directionOf", new Class<?>[]{String.class}, "乾"));
        assertEquals(1, call("dirToTrigram", new Class<?>[]{String.class}, "西北"));
        assertEquals(4, call("dirToTrigram", new Class<?>[]{String.class}, "东")); // 单字方位循环（L2587-2590）
        assertEquals("用生体", call("shengKeName", new Class<?>[]{int.class}, 1));   // 本体体用名（L4895）
        assertEquals("大吉", call("shengKeLabel", new Class<?>[]{int.class}, 1));    // 本体体用吉凶（L4911）
        assertEquals("东方", callStatic("fiveDirOf", new Class<?>[]{String.class}, "木"));
        assertEquals("红、紫、赤", callStatic("fiveColorOf", new Class<?>[]{String.class}, "火"));
        assertEquals("春", callStatic("fiveSeasonOf", new Class<?>[]{String.class}, "木"));
        assertEquals("草木", callStatic("fiveObjectOf", new Class<?>[]{String.class}, "木"));
        assertEquals("木", callStatic("fivePhaseOfColor", new Class<?>[]{String.class}, "见青色"));
        assertEquals("土", callStatic("fivePhaseOfColor", new Class<?>[]{String.class}, "毫无色泽"));
        assertEquals("寅卯木日应", String.valueOf(callStatic("riYingNote", new Class<?>[]{String.class}, "寅")).replaceAll(".*，", "").replaceAll("（.*", ""));
        assertNotNull(callStatic("loreMapInt", new Class<?>[]{String.class, Map.class}, "SHENG_SHU", new LinkedHashMap<String, Integer>()));
        String[] cr = (String[]) callStatic("categoryRulePair", new Class<?>[]{String.class}, "求财");
        assertEquals("体克用有财", cr[0]);
        assertEquals("初爻动变震木", callStatic("qianKunDiseaseYao", new Class<?>[]{int.class}, 1));
        Map<String, String[]> tx = (Map<String, String[]>) callStatic("tuXiangBuLu", new Class<?>[]{});
        assertTrue(tx.containsKey("图一"));
        assertEquals(1, call("jw", new Class<?>[]{String.class}, "base_3"));
        assertEquals("大吉", call("scoreToLevel", new Class<?>[]{int.class}, 5));
        call("resolvePreceptName", new Class<?>[]{String.class}, "某未声明戒律");
        assertEquals("无具体事项不可起卦。", call("preceptAdmonition", new Class<?>[]{String.class}, "无事不占"));
    }

    @Test
    @DisplayName("OP-02 verifyOntologyKnowledge 与内置表逐例一致（mism=0）+ 名录匹配")
    void verifyConsistent() throws Exception {
        stubOk();
        setBackend(backend);
        call("loadOntologyDomain", new Class<?>[]{});
        call("verifyOntologyKnowledge", new Class<?>[]{});
        assertTrue((Boolean) getInstanceBool("ontologyDomainReady"));
    }

    @Test
    @DisplayName("OP-03 verifyOntologyKnowledge 不一致（mism>0）与空 label 跳过")
    void verifyMismatch() throws Exception {
        when(obda.executeAboxQuery(anyString())).thenAnswer(inv -> {
            String q = inv.getArgument(0);
            List<Map<String, String>> rows = new ArrayList<>();
            if (q.contains("yijing:HexagramSequence")) {
                rows.add(row("label", ""));
                rows.add(row("label", "1.乾为天"));
            }
            return rows;
        });
        setBackend(backend);
        setInstance("ontologyDomainReady", true);
        Map<String, String> five = new LinkedHashMap<>();
        Map<String, String> dir = new LinkedHashMap<>();
        Map<String, Integer> num = new LinkedHashMap<>();
        String[] tn = {"乾", "兑", "离", "震", "巽", "坎", "艮", "坤"};
        for (int i = 1; i <= 8; i++) {
            five.put(tn[i - 1], "错");
            dir.put(tn[i - 1], "错");
            num.put(tn[i - 1], i);
        }
        setInstance("ontoTrigramFive", five);
        setInstance("ontoTrigramDir", dir);
        setInstance("ontoTrigramNumber", num);
        call("verifyOntologyKnowledge", new Class<?>[]{});
    }

    @Test
    @DisplayName("OP-04 loadOntologyDomain OBDA 未就绪（backendService=null）→ 跳过")
    void loadSkipWhenNoBackend() throws Exception {
        call("loadOntologyDomain", new Class<?>[]{});
        assertFalse((Boolean) getInstanceBool("ontologyDomainReady"));
    }

    @Test
    @DisplayName("OP-05 loadOntologyDomain 装载异常 → catch 回退（L531）")
    void loadException() throws Exception {
        when(obda.executeAboxQuery(anyString())).thenThrow(new RuntimeException("boom"));
        setBackend(backend);
        call("loadOntologyDomain", new Class<?>[]{});
        assertFalse((Boolean) getInstanceBool("ontologyDomainReady"));
    }

    @Test
    @DisplayName("OP-06 loadDomainRelations 异常 → catch 回退（L621）")
    void domainRelationsException() throws Exception {
        when(obda.executeAboxQuery(anyString())).thenThrow(new RuntimeException("boom"));
        call("loadDomainRelations", new Class<?>[]{OBDAHandler.class}, obda);
    }

    @Test
    @DisplayName("OP-07 loadTrigramKnowledge 异常 → catch 回退（L687）")
    void trigramKnowledgeException() throws Exception {
        when(obda.executeAboxQuery(anyString())).thenThrow(new RuntimeException("boom"));
        call("loadTrigramKnowledge", new Class<?>[]{OBDAHandler.class}, obda);
    }

    @Test
    @DisplayName("OP-08 verifyOntologyKnowledge 异常 → catch 跳过（L772）")
    void verifyException() throws Exception {
        when(obda.executeAboxQuery(anyString())).thenThrow(new RuntimeException("boom"));
        setBackend(backend);
        call("verifyOntologyKnowledge", new Class<?>[]{});
    }

    @Test
    @DisplayName("OP-09 loadTrigramKnowledge 条目不足 8（含非法 num）→ 不覆盖内置")
    void trigramKnowledgeInsufficient() throws Exception {
        when(obda.executeAboxQuery(anyString())).thenAnswer(inv -> {
            String q = inv.getArgument(0);
            List<Map<String, String>> rows = new ArrayList<>();
            if (q.contains("core:trigramFamilyRole")) {
                rows.add(row("num", "1", "lab", "乾", "fl", "金", "dl", "西北", "role", "父", "nat", "健", "main", "天"));
                rows.add(row("num", "x", "lab", "兑", "fl", "金", "dl", "西", "role", "少女", "nat", "说", "main", "泽"));
            }
            return rows;
        });
        call("loadTrigramKnowledge", new Class<?>[]{OBDAHandler.class}, obda);
    }

    @Test
    @DisplayName("OP-10 loadDomainRelations 条目不足以覆盖（<5）→ 保留 null 内置兜底")
    void domainRelationsInsufficient() throws Exception {
        when(obda.executeAboxQuery(anyString())).thenAnswer(inv -> new ArrayList<Map<String, String>>());
        call("loadDomainRelations", new Class<?>[]{OBDAHandler.class}, obda);
    }

    @Test
    @DisplayName("OP-11 verifyOntologyKnowledge OBDA 未就绪 → 跳过（L723-724）")
    void verifyNoBackend() throws Exception {
        call("verifyOntologyKnowledge", new Class<?>[]{});
    }

    @Test
    @DisplayName("OP-12 loadOntologyDomain 全空装载：各集合条目不足→走 else/空守卫诸 false 侧")
    void loadAllEmpty() throws Exception {
        when(obda.executeAboxQuery(anyString())).thenAnswer(inv -> new ArrayList<Map<String, String>>());
        setBackend(backend);
        call("loadOntologyDomain", new Class<?>[]{});
        assertFalse((Boolean) getInstanceBool("ontologyDomainReady"));
    }

    @Test
    @DisplayName("OP-13 loadDomainRelations 空字段行（lab=null / lv 非数）→ continue 跳过（L576/L604）")
    void loadRelationsNullFields() throws Exception {
        when(obda.executeAboxQuery(anyString())).thenAnswer(inv -> {
            String q = inv.getArgument(0);
            List<Map<String, String>> rows = new ArrayList<>();
            if (q.contains("rel:fiveDirection")) {
                rows.add(row("lab", null, "d", "x", "c", "x", "sea", "x", "ob", "x")); // lab null → continue
                for (String[] r : new String[][]{{"木", "东方", "青", "春", "草木"}, {"火", "南方", "红", "夏", "火"},
                        {"土", "中央", "黄", "四季", "土"}, {"金", "西方", "白", "秋", "金"}, {"水", "北方", "黑", "冬", "水"}})
                    rows.add(row("lab", r[0], "d", r[1], "c", r[2], "sea", r[3], "ob", r[4]));
            } else if (q.contains("rel:BodyUseLevel")) {
                rows.add(row("lv", "x", "nm", "n", "au", "a")); // lv 非数 → continue
                for (String[] r : new String[][]{{"1", "用生体", "大吉"}, {"2", "比和", "吉"}, {"3", "体克用", "平"}, {"4", "体生用", "凶"}, {"5", "用克体", "大凶"}})
                    rows.add(row("lv", r[0], "nm", r[1], "au", r[2]));
            }
            return rows;
        });
        call("loadDomainRelations", new Class<?>[]{OBDAHandler.class}, obda);
    }

    @Test
    @DisplayName("OP-14 loadTrigramKnowledge：ontoLore 提供 TRI_KEYWORDS(≥8) → 覆盖内置（L670-683）")
    @SuppressWarnings("unchecked")
    void loadTrigramKeywords() throws Exception {
        when(obda.executeAboxQuery(anyString())).thenAnswer(inv -> {
            String q = inv.getArgument(0);
            List<Map<String, String>> rows = route(q);
            if (q.contains("div:LoreTextEntry")) {
                for (int i = 1; i <= 8; i++) rows.add(row("c", "TRI_KEYWORDS", "k", String.valueOf(i), "t", "天、地、人"));
            }
            return rows;
        });
        setBackend(backend);
        call("loadOntologyDomain", new Class<?>[]{});
        Object kw = getStatic("TRI_KEYWORDS");
        assertTrue(kw instanceof Map && ((Map<?, ?>) kw).size() >= 8);
    }

    @Test
    @DisplayName("OP-15 backend 非空但 getObdaHandler()==null → load/verify 均跳过（短路次操作数）")
    void backendNoHandler() throws Exception {
        BackendService b2 = mock(BackendService.class);
        when(b2.getObdaHandler()).thenReturn(null);
        setBackend(b2);
        call("loadOntologyDomain", new Class<?>[]{});
        call("verifyOntologyKnowledge", new Class<?>[]{});
        assertFalse((Boolean) getInstanceBool("ontologyDomainReady"));
    }
}
