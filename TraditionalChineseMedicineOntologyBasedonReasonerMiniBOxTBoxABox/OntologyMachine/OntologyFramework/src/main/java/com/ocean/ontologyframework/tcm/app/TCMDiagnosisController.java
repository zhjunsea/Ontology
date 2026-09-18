package com.ocean.ontologyframework.tcm.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 中医经方智能诊断 REST API。
 *
 * <pre>
 *   POST /api/diagnosis                       启动一次诊断（自然语言输入）
 *   GET  /api/diagnosis/{key}                 查询进度与结果
 *   POST /api/diagnosis/{key}/confirm         提交症状映射确认
 *   GET  /api/diagnosis/{key}/tasks           列出待办人工任务
 *   POST /api/diagnosis/{key}/tasks/{taskKey} 完成指定人工任务
 *   GET  /api/diagnosis/catalog               症状实例个体目录（前端自动补全）
 *   GET  /api/diagnosis/health                健康检查
 * </pre>
 */
@RestController
@RequestMapping("/api/diagnosis")
public class TCMDiagnosisController {

    private static final Logger log = LoggerFactory.getLogger(TCMDiagnosisController.class);

    private final TCMDiagnosisService service;
    private final SymptomCatalog catalog;

    public TCMDiagnosisController(TCMDiagnosisService service, SymptomCatalog catalog) {
        this.service = service;
        this.catalog = catalog;
    }

    // ============================================================
    // 诊断
    // ============================================================

    @PostMapping
    public ResponseEntity<?> start(@RequestBody Map<String, Object> body) {
        String text = body == null ? null : String.valueOf(body.getOrDefault("text", ""));
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().body(error("text 不能为空"));
        }
        try {
            return ResponseEntity.accepted().body(service.start(text));
        } catch (Exception e) {
            log.error("[API] 启动诊断失败", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error(e.getMessage()));
        }
    }

    @GetMapping("/{key}")
    public ResponseEntity<?> get(@PathVariable long key) {
        try {
            return ResponseEntity.ok(service.snapshot(key));
        } catch (Exception e) {
            log.error("[API] 查询诊断失败 key={}", key, e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error(e.getMessage()));
        }
    }

    @PostMapping("/{key}/confirm")
    public ResponseEntity<?> confirm(@PathVariable long key, @RequestBody Map<String, Object> body) {
        try {
            List<String> confirmed = strList(body == null ? null : body.get("confirmed"));
            List<String> rejected = strList(body == null ? null : body.get("rejected"));
            List<String> extra = strList(body == null ? null : body.get("extra"));
            return ResponseEntity.ok(service.confirmSymptoms(key, confirmed, rejected, extra));
        } catch (Exception e) {
            log.error("[API] 症状确认失败 key={}", key, e);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error(e.getMessage()));
        }
    }

    @GetMapping("/{key}/tasks")
    public ResponseEntity<?> tasks(@PathVariable long key) {
        try {
            return ResponseEntity.ok(service.pendingTasks(key));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error(e.getMessage()));
        }
    }

    @PostMapping("/{key}/tasks/{taskKey}")
    public ResponseEntity<?> completeTask(@PathVariable long key,
                                          @PathVariable long taskKey,
                                          @RequestBody(required = false) Map<String, Object> body) {
        try {
            return ResponseEntity.ok(service.completeTask(key, taskKey, body));
        } catch (Exception e) {
            log.error("[API] 完成任务失败 key={} taskKey={}", key, taskKey, e);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error(e.getMessage()));
        }
    }

    // ============================================================
    // 目录与健康检查
    // ============================================================

    @GetMapping("/catalog")
    public Map<String, Object> catalog(@RequestParam(required = false) String category,
                                       @RequestParam(required = false) String q,
                                       @RequestParam(defaultValue = "30") int limit) {
        SymptomCatalog.Category cat = SymptomCatalog.Category.fromCn(category);
        List<Map<String, Object>> items = new ArrayList<>();
        for (SymptomCatalog.Entry e : catalog.search(q, cat, Math.max(1, Math.min(limit, 500)))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("fragment", e.getFragment());
            m.put("iri", e.getIri());
            m.put("label", e.getLabel());
            m.put("category", e.getCategory().cn());
            items.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", catalog.size());
        out.put("counts", Map.of(
                "症状", catalog.countOf(SymptomCatalog.Category.ZHENGZHUANG),
                "脉象", catalog.countOf(SymptomCatalog.Category.MAIXIANG),
                "舌象", catalog.countOf(SymptomCatalog.Category.SHEXIANG),
                "腹证", catalog.countOf(SymptomCatalog.Category.FUZHENG)));
        out.put("items", items);
        return out;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("engineAvailable", service.isEngineAvailable());
        out.put("catalogSize", catalog.size());
        out.put("processId", TCMDiagnosisService.PROCESS_ID);
        return out;
    }

    // ============================================================
    // 工具
    // ============================================================

    private static Map<String, Object> error(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", msg == null ? "未知错误" : msg);
        return m;
    }

    private static List<String> strList(Object v) {
        List<String> out = new ArrayList<>();
        if (v == null) return out;
        if (v instanceof List<?> l) {
            for (Object o : l) {
                if (o == null) continue;
                String s = o.toString().trim();
                if (!s.isEmpty()) out.add(s);
            }
        } else {
            for (String s : v.toString().split("[,\\s]+")) {
                if (!s.isBlank()) out.add(s.trim());
            }
        }
        return out;
    }
}
