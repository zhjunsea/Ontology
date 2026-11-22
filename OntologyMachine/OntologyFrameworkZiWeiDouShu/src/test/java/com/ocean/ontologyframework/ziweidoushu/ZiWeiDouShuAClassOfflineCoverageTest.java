 package com.ocean.ontologyframework.ziweidoushu;

import com.ocean.openlletresolver.BackendService;
import com.ocean.ontopobdahandler.OBDAHandler;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A 类「编排/基础设施」方法离线可补分支（无需真实 Ontop/MySQL/Zeebe/LLM）。
 *
 * <p>对以下方法以临时文件 / 注入语料 / Mockito 桩驱动，覆盖「可离线达」的判定与容错分支：
 * <ul>
 *   <li>{@code verifyObdaTableCoverage}：obdaPath 空 / 文件不可读降级 / 全映射 INFO / 孤儿表 fail-fast 抛错；</li>
 *   <li>{@code loadPalaceClassic}：缓存命中快路径 / OBDA 语料注入（含 null、列不足、有效行）/ 资源 TSV 兜底；</li>
 *   <li>{@code handlePersistAndReturn}：backendService 为空跳过 / 就绪写入(case_record) / 短生年脱敏；</li>
 *   <li>{@code handleSendAlert}：未触发 → SKIPPED_NO_ALERT；</li>
 *   <li>{@code extractAlertTypeFromReading}：命中=/无/无括号/为空 全分支。</li>
 * </ul>
 * 真正依赖真实 Ontop/DB/Zeebe/MQ 的其余分支（loadStateFromObda 真数据解析、MQ 实发、DB 实写）不在本类范围。
 */
@DisplayName("A类补齐·编排/基础设施 离线可补分支（verifyObda/loadPalaceClassic/persist/sendAlert/extract）")
class ZiWeiDouShuAClassOfflineCoverageTest {

    private static final long JOB_KEY = 9301L;

    // loadPalaceClassic 依赖的两个静态字段快照（避免污染其它测试类）
    private Object origPalaceClassic;
    private Object origOnClassicCorpus;

    @BeforeEach
    void snapshot() throws Exception {
        origPalaceClassic = getStatic("PALACE_CLASSIC");
        origOnClassicCorpus = getStatic("ONTO_CLASSIC_CORPUS");
    }

    @AfterEach
    void restore() throws Exception {
        setStatic("PALACE_CLASSIC", origPalaceClassic);
        setStatic("ONTO_CLASSIC_CORPUS", origOnClassicCorpus);
    }

    // ---------------- 反射 / 桩工具 ----------------

    private static Object getStatic(String name) throws Exception {
        Field f = ZiWeiDouShuOntologyJobWorker.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(null);
    }

    private static void setStatic(String name, Object value) throws Exception {
        Field f = ZiWeiDouShuOntologyJobWorker.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(null, value);
    }

    private static void setInstanceField(Object target, String name, Object value) throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void invokeInstanceNoArg(Object target, String name) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod(name);
        m.setAccessible(true);
        try {
            m.invoke(target);
        } catch (java.lang.reflect.InvocationTargetException e) {
            // 解包反射包装，令断言反映生产真实抛出的异常类型
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) { throw (RuntimeException) cause; }
            if (cause instanceof Error) { throw (Error) cause; }
            throw e;
        }
    }

    private ActivatedJob jobWith(Map<String, Object> vars) {
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getKey()).thenReturn(JOB_KEY);
        when(job.getVariablesAsMap()).thenReturn(vars);
        return job;
    }

    private static JobClient client() {
        return mock(JobClient.class, RETURNS_DEEP_STUBS);
    }

    @Test
    @DisplayName("verifyObdaTableCoverage：空路径/不可读降级/全映射INFO/孤儿表fail-fast 全分支")
    void verifyObdaTableCoverageBranches() throws Exception {
        ZiWeiDouShuOntologyJobWorker w = new ZiWeiDouShuOntologyJobWorker();
        // 1) obdaPath=null → 守卫 return
        setInstanceField(w, "obdaPath", null);
        assertThatCode(() -> invokeInstanceNoArg(w, "verifyObdaTableCoverage")).doesNotThrowAnyException();
        // 2) obdaPath=空白 → 守卫 return
        setInstanceField(w, "obdaPath", "   ");
        assertThatCode(() -> invokeInstanceNoArg(w, "verifyObdaTableCoverage")).doesNotThrowAnyException();

        Path dir = Files.createTempDirectory("zwds_obda");
        Path ddl = dir.resolve("createdb_tables.ddl");
        Path obda = dir.resolve("ziweidoushu.obda");
        // 3) 全映射 → INFO 全覆盖（不抛）
        Files.writeString(ddl, "CREATE TABLE a (id int);\nCREATE TABLE b (id int);\n", StandardCharsets.UTF_8);
        Files.writeString(obda, "SELECT * FROM a\nSELECT * FROM b\n", StandardCharsets.UTF_8);
        setInstanceField(w, "obdaPath", obda.toString());
        assertThatCode(() -> invokeInstanceNoArg(w, "verifyObdaTableCoverage")).doesNotThrowAnyException();
        // 4) 孤儿表(ddl 有而 obda 未映射) → fail-fast 抛 IllegalStateException
        Files.writeString(ddl, "CREATE TABLE a (id int);\nCREATE TABLE orphan (x int);\n", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> invokeInstanceNoArg(w, "verifyObdaTableCoverage"))
                .isInstanceOf(IllegalStateException.class);
        // 5) ddl 不可读(删除) → 降级跳过
        Files.deleteIfExists(ddl);
        assertThatCode(() -> invokeInstanceNoArg(w, "verifyObdaTableCoverage")).doesNotThrowAnyException();
        // 6) obda 文件不存在 → 降级跳过
        setInstanceField(w, "obdaPath", dir.resolve("nope.obda").toString());
        assertThatCode(() -> invokeInstanceNoArg(w, "verifyObdaTableCoverage")).doesNotThrowAnyException();
    }
    @Test
    @DisplayName("loadPalaceClassic：缓存快路径 / OBDA语料注入(null/短行/有效) / 资源TSV兜底")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void loadPalaceClassicBranches() throws Exception {
        // 1) 缓存已就绪 → 直接返回同一实例
        Map<String, List<String[]>> cache = new LinkedHashMap<>();
        cache.put("命宫", new ArrayList<>());
        setStatic("PALACE_CLASSIC", cache);
        Map<String, List<String[]>> r1 = ZiWeiDouShuOntologyJobWorker.loadPalaceClassic();
        assertThat(r1).isSameAs(cache);

        // 2) OBDA 语料注入：null 行跳过、列不足跳过、有效行归并
        setStatic("PALACE_CLASSIC", null);
        List<String[]> corpus = new ArrayList<>();
        corpus.add(new String[]{"命宫", "459", "紫微土，在命宫…"});
        corpus.add(null);
        corpus.add(new String[]{"命宫", "460"});
        corpus.add(new String[]{"财帛", "500", "武曲金，在财帛…"});
        setStatic("ONTO_CLASSIC_CORPUS", corpus);
        Map<String, List<String[]>> r2 = ZiWeiDouShuOntologyJobWorker.loadPalaceClassic();
        assertThat(r2).containsKeys("命宫", "财帛");
        assertThat(r2.get("命宫")).hasSize(1);   // 短行被 continue 跳过
        assertThat(r2.get("财帛")).hasSize(1);

        // 3) 双空 → 资源 TSV 兜底（palace_classic.tsv 813 行，12 宫）
        setStatic("PALACE_CLASSIC", null);
        setStatic("ONTO_CLASSIC_CORPUS", null);
        Map<String, List<String[]>> r3 = ZiWeiDouShuOntologyJobWorker.loadPalaceClassic();
        assertThat(r3).isNotEmpty();
        assertThat(r3).containsKey("命宫");
    }
    @Test
    @DisplayName("handlePersistAndReturn：backendService 为空跳过 / 就绪写入 case_record / 短生年脱敏")
    void handlePersistAndReturnBranches() throws Exception {
        // 1) backendService 为空 → 跳过落库（else 分支），仅返回 caseRecord
        ZiWeiDouShuOntologyJobWorker w1 = new ZiWeiDouShuOntologyJobWorker();
        Map<String, Object> v1 = new LinkedHashMap<>();
        v1.put("chartBase", "SOLAR=1990-05-15T10:30;GENDER=男");
        v1.put("chartSkeleton", "命宫=子;水二局");
        v1.put("reading", "命书正文");
        JobClient c1 = client();
        w1.handlePersistAndReturn(jobWith(v1), c1);
        verify(c1).newCompleteCommand(JOB_KEY);

        // 2) 短生年(SOLAR<4) → yearOnly 三元假侧
        Map<String, Object> v2 = new LinkedHashMap<>();
        v2.put("chartBase", "SOLAR=90;GENDER=女");
        v2.put("chartSkeleton", "");
        JobClient c2 = client();
        w1.handlePersistAndReturn(jobWith(v2), c2);
        verify(c2).newCompleteCommand(JOB_KEY);

        // 3) backendService 就绪(桩) → 写入 case_record（addComponent 被调用）
        BackendService bs = mock(BackendService.class);
        OBDAHandler obda = mock(OBDAHandler.class);
        when(bs.getObdaHandler()).thenReturn(obda);
        when(obda.addComponent(anyString(), anyList(), anyList())).thenReturn(1);
        ZiWeiDouShuOntologyJobWorker w3 = new ZiWeiDouShuOntologyJobWorker();
        setInstanceField(w3, "backendService", bs);
        Map<String, Object> v3 = new LinkedHashMap<>();
        v3.put("chartBase", "SOLAR=1985-08-01T08:00;GENDER=男");
        v3.put("chartSkeleton", "命宫=午;火六局");
        v3.put("reading", "命书正文");
        v3.put("aiInterpretation", "详解");
        v3.put("aiInterpretationPlain", "白话");
        JobClient c3 = client();
        w3.handlePersistAndReturn(jobWith(v3), c3);
        verify(c3).newCompleteCommand(JOB_KEY);
        verify(obda).addComponent(anyString(), anyList(), anyList());
    }

    @Test
    @DisplayName("handleSendAlert：未触发 → SKIPPED_NO_ALERT（离线不触 MQ）")
    void handleSendAlertSkip() {
        ZiWeiDouShuOntologyJobWorker w = new ZiWeiDouShuOntologyJobWorker();
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("chartBase", "SOLAR=1990-05-15T10:30;GENDER=男");
        JobClient c = client();
        w.handleSendAlert(jobWith(vars), c);
        verify(c).newCompleteCommand(JOB_KEY);
    }

    @Test
    @DisplayName("extractAlertTypeFromReading：null/空/无命中=/命中=类别/命中=无/无括号 全分支")
    void extractAlertTypeBranches() {
        assertThat(ZiWeiDouShuOntologyJobWorker.extractAlertTypeFromReading(null)).isEmpty();
        assertThat(ZiWeiDouShuOntologyJobWorker.extractAlertTypeFromReading("")).isEmpty();
        assertThat(ZiWeiDouShuOntologyJobWorker.extractAlertTypeFromReading("并无命中项")).isEmpty();
        assertThat(ZiWeiDouShuOntologyJobWorker.extractAlertTypeFromReading("九、告警判据 命中=疾病（触发外发）")).isEqualTo("疾病");
        assertThat(ZiWeiDouShuOntologyJobWorker.extractAlertTypeFromReading("命中=无（不触发）")).isEmpty();
        assertThat(ZiWeiDouShuOntologyJobWorker.extractAlertTypeFromReading("命中=横死\n后续说明")).isEqualTo("横死");
    }
}
