package com.ocean.ontologyframework.sizhubazi;

import org.apache.jena.query.QuerySolution;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionRemote;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 Ontop 集成测试：直连一个 sizhubazi Ontop 端点，用与应用代码完全相同的 SPARQL 谓词
 * 验证六书引据（条文/名录）确实可被读取（证明 OBDA 映射 + 真实 MySQL 数据 + 查询三者一致）。
 *
 * <p>端点由环境变量 {@code SIZHUBAZI_SPARQL_ENDPOINT} 指定，默认 {@code http://localhost:8080/sparql}
 * （与生产口径一致；如共享端点为其他应用占用可覆盖）。端点不可用/未提供 sizhubazi 本体时经 {@link Assumptions}
 * 如实跳过（不中断构建、不伪造通过）。
 */
@DisplayName("真实 Ontop 集成·六书引据 SPARQL 读取（需 sizhubazi Ontop，默认 :8080）")
class SiZhuBaZiRealObdaIntegrationTest {

    private static final String NS_R = "http://example.org/sizhubazi/reading/";
    private static final String NS_C = "http://example.org/sizhubazi/core/";
    private static final String NS_T = "http://example.org/sizhubazi/tengod/";
    private static final String NS_Y = "http://example.org/sizhubazi/yongshen/";
    private static final String RDFS_LABEL = "http://www.w3.org/2000/01/rdf-schema#label";

    private static RDFConnection conn;

    @BeforeAll
    static void connectOrSkip() {
        String ep = System.getenv().getOrDefault("SIZHUBAZI_SPARQL_ENDPOINT", "http://localhost:8080/sparql");
        RDFConnection c = null;
        boolean ok = false;
        try {
            c = RDFConnectionRemote.create().destination(ep).build();
            List<Map<String, String>> probe = query(c,
                    "SELECT ?s ?o WHERE { ?s <" + NS_R + "readingText> ?o } LIMIT 1");
            ok = !probe.isEmpty();
        } catch (Exception e) {
            ok = false;
        }
        conn = c;
        Assumptions.assumeTrue(ok,
                "需 sizhubazi Ontop 提供 " + ep + "（未提供 sizhubazi 本体/不可用则跳过，避免伪通过）");
    }

    @AfterAll
    static void close() {
        if (conn != null) {
            try { conn.close(); } catch (Exception ignored) { }
        }
    }

    private static List<Map<String, String>> query(RDFConnection c, String sparql) {
        List<Map<String, String>> out = new ArrayList<>();
        c.querySelect(sparql, (QuerySolution rs) -> {
            Map<String, String> m = new LinkedHashMap<>();
            rs.varNames().forEachRemaining(v -> {
                var n = rs.get(v);
                m.put(v, n.isLiteral() ? n.asLiteral().getString()
                        : n.isResource() ? n.asResource().getURI() : n.toString());
            });
            out.add(m);
        });
        return out;
    }

    @Test
    @DisplayName("引据族 CategoryFortune(fu_clause)：返回 label/cat/text/src 真实行")
    void categoryFortune() {
        List<Map<String, String>> rows = query(conn,
                "SELECT ?label ?cat ?text ?src WHERE { ?c a <" + NS_R + "CategoryFortune> ; "
                        + "<" + RDFS_LABEL + "> ?label ; <" + NS_R + "readingCategory> ?cat ; "
                        + "<" + NS_R + "readingText> ?text ; <" + NS_R + "readingSource> ?src . } LIMIT 3");
        assertThat(rows).isNotEmpty();
        assertThat(rows.get(0)).containsKeys("label", "cat", "text", "src");
        assertThat(rows.get(0).get("text")).isNotBlank();
    }

    @Test
    @DisplayName("日时断 DayHourClause：丁丑日丁未时 → text/source（与 clauseDayHour 一致）")
    void dayHourClause() {
        List<Map<String, String>> rows = query(conn,
                "SELECT ?text ?source WHERE { ?c a <" + NS_R + "DayHourClause> ; "
                        + "<" + NS_R + "clauseDayPillar> \"丁丑\" ; <" + NS_R + "clauseHourPillar> \"丁未\" ; "
                        + "<" + NS_R + "readingText> ?text ; <" + NS_R + "clauseSource> ?source . }");
        assertThat(rows).isNotEmpty();
        assertThat(rows.get(0).get("text")).isNotBlank();
        assertThat(rows.get(0).get("source")).isNotBlank();
    }

    @Test
    @DisplayName("六十甲子性质 sexagenary：core:GengChen → text（与 clauseSexagenaryNature 一致）")
    void sexagenaryNature() {
        List<Map<String, String>> rows = query(conn,
                "SELECT ?text WHERE { <" + NS_C + "GengChen> <" + NS_C + "sexagenaryNatureText> ?text . }");
        assertThat(rows).isNotEmpty();
        assertThat(rows.get(0).get("text")).isNotBlank();
    }

    @Test
    @DisplayName("十干坐支 StemBranchAuspice：返回 subject/text/source（与 clauseStemSeat 一致）")
    void stemBranchAuspice() {
        List<Map<String, String>> rows = query(conn,
                "SELECT ?subject ?text ?source WHERE { ?c a <" + NS_R + "StemBranchAuspice> ; "
                        + "<" + RDFS_LABEL + "> ?subject ; <" + NS_R + "readingText> ?text ; "
                        + "<" + NS_R + "readingSource> ?source . } LIMIT 3");
        assertThat(rows).isNotEmpty();
        assertThat(rows.get(0)).containsKeys("subject", "text", "source");
    }

    @Test
    @DisplayName("调候 TiaohouClause：返回 label/p/x/text/source（与 clauseTiaohou 一致）")
    void tiaohouClause() {
        List<Map<String, String>> rows = query(conn,
                "SELECT ?label ?p ?x ?text ?source WHERE { ?c a <" + NS_Y + "TiaohouClause> ; "
                        + "<" + RDFS_LABEL + "> ?label ; <" + NS_Y + "tiaohouPrimaryUse> ?p ; "
                        + "<" + NS_Y + "tiaohouSecondaryUse> ?x ; <" + NS_Y + "tiaohouText> ?text ; "
                        + "<" + NS_Y + "clauseSource> ?source . } LIMIT 3");
        assertThat(rows).isNotEmpty();
        assertThat(rows.get(0)).containsKeys("label", "p", "x", "text", "source");
    }

    @Test
    @DisplayName("十神映射 TenGodMap：返回 d/t/g（与 tenGodOntologyMap 一致）")
    void tenGodMap() {
        List<Map<String, String>> rows = query(conn,
                "SELECT ?d ?t ?g WHERE { ?m a <" + NS_T + "TenGodMap> ; <" + NS_T + "baseDayStem> ?d ; "
                        + "<" + NS_T + "targetStem> ?t ; <" + NS_T + "mapTenGodName> ?g . } LIMIT 5");
        assertThat(rows).isNotEmpty();
        assertThat(rows.get(0)).containsKeys("d", "t", "g");
        assertThat(rows.get(0).get("g")).isNotBlank();
    }
}

