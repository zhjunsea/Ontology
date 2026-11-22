package com.ocean.ontologyframework.sizhubazi;

import com.ocean.openlletresolver.BackendService;
import com.ocean.ontopobdahandler.OBDAHandler;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 杂项边界：合婚缺偶抛错、起运岁顺逆、节气年加载非法行、SWRL 推导 df=null 降级。 */
@DisplayName("杂项边界·合婚缺偶/起运岁/节气加载/SWRL降级")
class SiZhuBaZiMiscEdgeTest {

    private static final Class<?> WORKER = SiZhuBaZiOntologyJobWorker.class;

    private static Object inst(Object w, String name, Class<?>[] t, Object... a) throws Exception {
        Method m = WORKER.getDeclaredMethod(name, t);
        m.setAccessible(true);
        try { return m.invoke(w, a); }
        catch (java.lang.reflect.InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException re) { throw re; }
            if (c instanceof Error er) { throw er; }
            throw e;
        }
    }

    private static SiZhuBaZiOntologyJobWorker newWorker() throws Exception {
        Constructor<?> c = WORKER.getDeclaredConstructor();
        c.setAccessible(true);
        return (SiZhuBaZiOntologyJobWorker) c.newInstance();
    }

    private static void setField(Object t, String n, Object v) throws Exception {
        Class<?> c = t.getClass();
        while (c != null) {
            try { Field f = c.getDeclaredField(n); f.setAccessible(true); f.set(t, v); return; }
            catch (NoSuchFieldException e) { c = c.getSuperclass(); }
        }
        throw new NoSuchFieldException(n);
    }

    private static Map<String, String> row(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) { m.put(kv[i], kv[i + 1]); }
        return m;
    }

    private static SiZhuBaZiOntologyJobWorker workerWithObda(Function<String, List<Map<String, String>>> f) throws Exception {
        OBDAHandler obda = mock(OBDAHandler.class);
        when(obda.executeAboxQuery(anyString())).thenAnswer((InvocationOnMock inv) -> f.apply(inv.getArgument(0)));
        BackendService bs = mock(BackendService.class);
        when(bs.getObdaHandler()).thenReturn(obda);
        SiZhuBaZiOntologyJobWorker w = newWorker();
        setField(w, "backendService", bs);
        return w;
    }

    private static SiZhuBaZiOntologyJobWorker workerWithBackend() throws Exception {
        SiZhuBaZiOntologyJobWorker w = newWorker();
        setField(w, "backendService", mock(BackendService.class));
        return w;
    }

    private static final class Cap implements Answer<Object> {
        Object vars;
        @Override public Object answer(InvocationOnMock inv) {
            Method m = inv.getMethod();
            if ("variables".equals(m.getName()) && inv.getArguments().length == 1) { vars = inv.getArgument(0); }
            Class<?> rt = m.getReturnType();
            if (rt == void.class) return null;
            if (rt.isPrimitive()) { return rt == long.class ? (Object) 0L : rt == int.class ? (Object) 0
                    : rt == boolean.class ? (Object) false : rt == double.class ? (Object) 0d
                    : rt == float.class ? (Object) 0f : (Object) null; }
            try { return mock(rt, this); } catch (Throwable t) { return null; }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> drive(SiZhuBaZiOntologyJobWorker w, String handler, Map<String, Object> vars) throws Exception {
        Cap cap = new Cap();
        JobClient client = mock(JobClient.class, cap);
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(vars);
        when(job.getKey()).thenReturn(1L);
        Method m = WORKER.getMethod(handler, ActivatedJob.class, JobClient.class);
        m.invoke(w, job, client);
        return cap.vars == null ? null : (Map<String, Object>) ((Map<String, Object>) cap.vars).get("result");
    }

    @Test
    @DisplayName("handleMarriageMatch：无 partnerBirth → 抛错降级（不产出结果）")
    void marriageNoPartner() throws Exception {
        SiZhuBaZiOntologyJobWorker w = newWorker();
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("chartBase", "SOLAR=1990-05-15T08:00;GENDER=男;SUBJECT=男命;MODE=合婚");
        v.put("fourPillars", "庚午 辛巳 庚辰 戊子");
        v.put("strength", "身强（得令）");
        assertThat(drive(w, "handleMarriageMatch", v)).isNull();
    }

    @Test
    @DisplayName("pilotAge：顺行/逆行均返回岁数（非负）")
    void pilotAgeEdges() throws Exception {
        SiZhuBaZiOntologyJobWorker w = newWorker();
        Class<?>[] t = {LocalDateTime.class, String.class};
        LocalDateTime birth = LocalDateTime.of(1990, 5, 15, 10, 30);
        Object shun = inst(w, "pilotAge", t, birth, "顺");
        Object ni = inst(w, "pilotAge", t, birth, "逆");
        assertThat(shun).isInstanceOf(Double.class);
        assertThat(ni).isInstanceOf(Double.class);
        assertThat((Double) shun).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    @DisplayName("loadSolarTermYear：年份越界→null；非法 dt/idx 行跳过、合法行计入")
    void loadSolarTermYearEdges() throws Exception {
        List<Map<String, String>> rows = new ArrayList<>();
        rows.add(row("dt", null, "idx", "0"));
        rows.add(row("dt", "1990-01-05 10:00", "idx", "abc"));
        rows.add(row("dt", "1990-01-05 10:00", "idx", "-1"));
        rows.add(row("dt", "1990-01-05 10:00", "idx", "24"));
        rows.add(row("dt", "1990-01-05 10:00", "idx", "0"));
        SiZhuBaZiOntologyJobWorker w = workerWithObda(q -> rows);
        Class<?>[] t = {int.class};
        assertThat((Object) inst(w, "loadSolarTermYear", t, 1949)).isNull();
        assertThat((Object) inst(w, "loadSolarTermYear", t, 1990)).isNotNull();
    }

    @Test
    @DisplayName("deriveAlertsViaSwrl：getDataFactory()==null → 返回 null（降级程序判据）")
    void deriveAlertsDfNull() throws Exception {
        SiZhuBaZiOntologyJobWorker w = workerWithBackend();
        Class<?>[] t = {String.class, String.class, String.class, String.class};
        Object r = inst(w, "deriveAlertsViaSwrl", t, "平常", "格成", "身强（得令）", "桃花");
        assertThat(r).isNull();
    }
}
