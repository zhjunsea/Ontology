package com.ocean.ontologyframework.ziweidoushu;

import com.ocean.ontopobdahandler.OBDAHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A 类·{@code loadStateFromObda} 真数据成功分支（进程内真连 Ontop:8080 / MySQL:3306）。
 *
 * <p>此前的 ObdaFaultInjectionTest 已覆盖 null/异常/空结果/满额不足等**容错**分支；本类补齐
 * 「各查表成功返回真实行 → 缓存非空填充」的**成功路径**分支。
 *
 * <p>守卫：Ontop(8080)/MySQL(3306) 任一不可达即 {@code assumeTrue} 跳过（不阻断离线构建）；
 * 仅当真实环境就绪时实跑并断言 {@code ONTO_BRIGHTNESS}/{@code ONTO_SIHUA} 等缓存被真实数据填充。
 * 因 {@code OBDAHandler} 为进程级单例静态，本类对 {@code ONTO_*} 快照/复原，避免污染其它测试类。
 */
@DisplayName("A类·loadStateFromObda 真数据成功分支（Ontop:8080/MySQL:3306，真环境就绪时实跑）")
class ZiWeiDouShuObdaRealLoadTest {

    private final List<Field> ontoFields = new ArrayList<>();
    private final List<Object> ontoOriginal = new ArrayList<>();

    private static boolean tcp(String host, int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 800);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeEach
    void snapshot() throws Exception {
        ontoFields.clear();
        ontoOriginal.clear();
        for (Field f : ZiWeiDouShuOntologyJobWorker.class.getDeclaredFields()) {
            if (f.getName().startsWith("ONTO_") && java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                f.setAccessible(true);
                ontoFields.add(f);
                ontoOriginal.add(f.get(null));
                f.set(null, null);
            }
        }
    }

    @AfterEach
    void restore() throws Exception {
        for (int i = 0; i < ontoFields.size(); i++) {
            ontoFields.get(i).set(null, ontoOriginal.get(i));
        }
    }

    private static Object getStatic(String name) throws Exception {
        Field f = ZiWeiDouShuOntologyJobWorker.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(null);
    }

    @Test
    @DisplayName("loadStateFromObda：真连 Ontop 各查表成功 → ONTO_* 缓存被真实数据填充")
    @SuppressWarnings("unchecked")
    void loadStateFromObdaRealData() throws Exception {
        Assumptions.assumeTrue(tcp("127.0.0.1", 8080) && tcp("127.0.0.1", 3306),
                "Ontop:8080 / MySQL:3306 未就绪，跳过真实 loadStateFromObda 用例");

        try {
            OBDAHandler.init("ontology/database/ziweidoushu.properties", "ontology/database/ziweidoushu.obda");
        } catch (IllegalStateException alreadyInitialized) {
            // 同 JVM 已初始化则复用
        }
        assertThat(OBDAHandler.getInstance()).isNotNull();

        ZiWeiDouShuOntologyJobWorker w = new ZiWeiDouShuOntologyJobWorker();
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("loadStateFromObda");
        m.setAccessible(true);
        m.invoke(w);

        Object brightness = getStatic("ONTO_BRIGHTNESS");
        Object sihua = getStatic("ONTO_SIHUA");
        assertThat(brightness).as("ONTO_BRIGHTNESS 应由真实 OBDA 填充而非空").isNotNull();
        assertThat(((Map<String, ?>) brightness).size()).isGreaterThan(0);
        assertThat(sihua).as("ONTO_SIHUA 应由真实 OBDA 填充而非空").isNotNull();
        assertThat(((Map<String, ?>) sihua).size()).isGreaterThan(0);
    }
}
