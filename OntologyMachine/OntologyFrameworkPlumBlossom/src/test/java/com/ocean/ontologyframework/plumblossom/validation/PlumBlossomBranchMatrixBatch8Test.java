package com.ocean.ontologyframework.plumblossom.validation;

import com.ocean.ontologyframework.plumblossom.PlumBlossomOntologyJobWorker;
import com.ocean.ontologyframework.plumblossom.web.PlumBlossomController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 第八批·Web 控制层（standalone MockMvc）+ 残余纯 helper 反射矩阵（离线）。
 *
 * <p>两部分，均纯离线、不依赖 Spring 容器 / Camunda 引擎 / RabbitMQ / MySQL：
 * <ul>
 *   <li><b>Controller</b>：以 `MockMvcBuilders.standaloneSetup(...)` 启动 `PlumBlossomController`（`CamundaClient` 保持 null），
 *       覆盖入参校验（subject 空→400）、离线启动失败降级（→503）、状态查询降级（fetchInstance 捕获异常→RUNNING、变量空表→200）、健康检查。</li>
 *   <li><b>残余纯 helper</b>：反射直调 `applyAppendixFullText`/`applyJuanTextOverrides`（原著全文/五卷正文外置回写装配）、
 *       `splitCharacters` 5~10 字各档、`objectDivinationText` 空体用、附录 getter（`appendixDoctrineNote`/`appendixTextNote`/`appendixTextCount`）、
 *       `objectDaikaoText`/`baGuaJueCounts`/`yiLunNote`/`zhanBuKeYingJueNote`。</li>
 * </ul>
 */
public class PlumBlossomBranchMatrixBatch8Test {

    private PlumBlossomOntologyJobWorker worker;

    @BeforeEach
    void setUp() {
        worker = new PlumBlossomOntologyJobWorker();
    }

    private Object call(String name, Class<?>[] types, Object... args) {
        try {
            Method m = PlumBlossomOntologyJobWorker.class.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(worker, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable c = e.getCause();
            throw new RuntimeException(name + " 抛出异常: " + c, c);
        } catch (Exception e) {
            throw new RuntimeException("反射调用失败: " + name, e);
        }
    }

    private String str(String name, Class<?>[] types, Object... args) {
        return String.valueOf(call(name, types, args));
    }

    // ======================================================================
    // 一、PlumBlossomController standalone MockMvc（CamundaClient=null，离线走降级）
    // ======================================================================

    private MockMvc mvc;

    private MockMvc controllerMvc() throws Exception {
        PlumBlossomController controller = new PlumBlossomController();
        Field f = PlumBlossomController.class.getDeclaredField("bpmnPath");
        f.setAccessible(true);
        f.set(controller, "dummy/plumBlossomProcess.bpmn");
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("B8-01 Controller·POST /divination：body 空 / subject 空白 → 400 且 error 提示")
    void controller_divination_blankSubject() throws Exception {
        mvc = controllerMvc();
        mvc.perform(post("/api/plumblossom/divination")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("subject 不能为空"));
        mvc.perform(post("/api/plumblossom/divination")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"subject\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("subject 不能为空"));
    }

    @Test
    @DisplayName("B8-02 Controller·POST /divination：subject 合法但引擎 null → 503 降级（error 非空）")
    void controller_divination_engineNull() throws Exception {
        mvc = controllerMvc();
        mvc.perform(post("/api/plumblossom/divination")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"subject\":\"测求职\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("B8-03 Controller·GET /divination/{key}：引擎 null → fetchInstance 降级 RUNNING、变量空表、200")
    void controller_status_offline() throws Exception {
        mvc = controllerMvc();
        mvc.perform(get("/api/plumblossom/divination/123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceKey").value(123))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.variables").exists());
    }

    @Test
    @DisplayName("B8-04 Controller·GET /health：engineAvailable=false / processId 常量 / bpmnPath")
    void controller_health() throws Exception {
        mvc = controllerMvc();
        mvc.perform(get("/api/plumblossom/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.engineAvailable").value(false))
                .andExpect(jsonPath("$.processId").value("PlumBlossomDivinationProcess"))
                .andExpect(jsonPath("$.bpmnPath").value("dummy/plumBlossomProcess.bpmn"));
    }

    // ======================================================================
    // 二、原著全文/五卷正文外置回写装配（applyAppendixFullText / applyJuanTextOverrides）
    // ======================================================================

    @Test
    @DisplayName("B8-05 applyAppendixFullText：null/空表/空值项/短数组/无同名字段/非 String 字段 均安全跳过")
    void applyAppendixFullText_guardBranches() {
        call("applyAppendixFullText", new Class<?>[]{Map.class}, (Object) null);
        call("applyAppendixFullText", new Class<?>[]{Map.class}, new LinkedHashMap<String, String[]>());

        Map<String, String[]> m = new LinkedHashMap<>();
        m.put("A_NULL", null);
        m.put("B_SHORT", new String[]{"x"});
        m.put("NOT_A_FIELD", new String[]{"a", "b", "c"});
        m.put("APPENDIX_TEXT", new String[]{"a", "b", "c"}); // 存在但为 Map 非 String
        call("applyAppendixFullText", new Class<?>[]{Map.class}, m);
    }

    @Test
    @DisplayName("B8-06 applyJuanTextOverrides：null/空表/键空/非 JUAN_ 前缀/文本空/字段不存在 均安全跳过")
    void applyJuanTextOverrides_guardBranches() {
        call("applyJuanTextOverrides", new Class<?>[]{Map.class}, (Object) null);
        call("applyJuanTextOverrides", new Class<?>[]{Map.class}, new LinkedHashMap<String, String>());

        Map<String, String> m = new LinkedHashMap<>();
        m.put("NOT_JUAN", "abc");
        m.put("JUAN_NOT_EXIST", "abc");
        call("applyJuanTextOverrides", new Class<?>[]{Map.class}, m);

        Map<String, String> m2 = new LinkedHashMap<>();
        m2.put("JUAN_X", "  ");
        call("applyJuanTextOverrides", new Class<?>[]{Map.class}, m2);
    }

    // ======================================================================
    // 三、字占取数 splitCharacters 5~10 字各档
    // ======================================================================

    @Test
    @DisplayName("B8-07 splitCharacters：5/6/7/8/9/10 字各档均返回两数且为正")
    void splitCharacters_fiveToTen() {
        Class<?>[] t = {String.class};
        String[] texts = {"一二三四五", "一二三四五六", "一二三四五六七",
                "一二三四五六七八", "一二三四五六七八九", "一二三四五六七八九十"};
        for (String text : texts) {
            int[] r = (int[]) call("splitCharacters", t, text);
            assertEquals(2, r.length, text);
            assertTrue(r[0] > 0 && r[1] > 0, text);
        }
    }

    // ======================================================================
    // 四、占物类断法 objectDivinationText 空体用分派
    // ======================================================================

    @Test
    @DisplayName("B8-08 objectDivinationText：体用皆空 → 仅由变卦/时节/外应成篇")
    void objectDivinationText_blankBodyUse() {
        Class<?>[] od = {String.class, String.class, int.class, String.class,
                String.class, String.class, String.class, String.class, String.class};
        String r = str("objectDivinationText", od,
                "占动物", "天风姤", 1, "", "", "", "天风姤", "秋", "见圆物");
        assertTrue(r.contains("占物类断法"));
    }

    // ======================================================================
    // 五、附录 getter / 占物备考 / 诸论 getter
    // ======================================================================

    @Test
    @DisplayName("B8-09 附录 getter：appendixDoctrineNote/appendixTextNote/appendixTextCount 非空与分派")
    void appendix_getters() {
        String doctrine = str("appendixDoctrineNote", new Class<?>[]{});
        assertTrue(doctrine.contains("附录"));
        int cnt = (Integer) call("appendixTextCount", new Class<?>[]{});
        assertTrue(cnt > 0);

        Class<?>[] t = {String.class};
        assertTrue(str("appendixTextNote", t, "八卦诀").contains("八卦"));
        assertTrue(str("appendixTextNote", t, "渔樵对问").contains("渔樵"));
        assertTrue(str("appendixTextNote", t, "邵雍").contains("邵雍"));
        assertEquals("", str("appendixTextNote", t, "无关"));
        assertEquals("", str("appendixTextNote", t, (Object) null));
    }

    @Test
    @DisplayName("B8-10 objectDaikaoText 占物类/非占物 + baGuaJueCounts 八宫×八卦")
    void objectDaikao_and_bagua() {
        Class<?>[] t = {String.class};
        String obj = str("objectDaikaoText", t, "占动物");
        assertNotNull(obj);
        assertEquals("", str("objectDaikaoText", t, "年月日时起例"));
        int[] bgj = (int[]) call("baGuaJueCounts", new Class<?>[]{});
        assertEquals(2, bgj.length);
        assertEquals(8, bgj[0]);
        assertEquals(64, bgj[1]);
    }

    @Test
    @DisplayName("B8-11 诸论 getter：yiLunNote / zhanBuKeYingJueNote 非空")
    void lun_getters() {
        assertFalse(str("yiLunNote", new Class<?>[]{}).isBlank());
        assertFalse(str("zhanBuKeYingJueNote", new Class<?>[]{}).isBlank());
    }
}
