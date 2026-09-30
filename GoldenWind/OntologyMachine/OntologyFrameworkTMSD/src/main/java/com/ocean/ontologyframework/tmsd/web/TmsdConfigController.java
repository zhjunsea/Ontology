package com.ocean.ontologyframework.tmsd.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ocean.utilities.ConfigFileLocator;
import com.ocean.utilities.YamlConfigUpdater;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * 塔架中段设计（TMSD）配置录入接口。
 *
 * <ul>
 *   <li>{@code GET  /api/tmsd/config} —— 读取当前配置（缺省值即当前值）；</li>
 *   <li>{@code POST /api/tmsd/config} —— 保存并写回 {@code application.yaml}（重启应用后生效）。</li>
 * </ul>
 *
 * <p>页面入口为 {@code /tmsd-config.html}。
 */
@RestController
@RequestMapping("/api/tmsd/config")
public class TmsdConfigController {

    private static final Logger log = LoggerFactory.getLogger(TmsdConfigController.class);

    @Value("${ontology.main-path:}")
    private String ontologyMainPath;

    @Value("${ontology.bpmn-path:}")
    private String ontologyBpmnPath;

    @Value("${tmsd.historical-geo-path:}")
    private String historicalGeoPath;

    @Value("${tmsd.historical-layout-path:}")
    private String historicalLayoutPath;

    @Value("${tmsd.output-dir:}")
    private String outputDir;

    /** 可选：显式指定 application.yaml 路径（默认按工作目录/classpath 自动定位）。 */
    @Value("${tmsd.config-file:}")
    private String configFileOverride;

    @GetMapping
    public TmsdConfigView get() {
        return new TmsdConfigView(
                ontologyMainPath,
                ontologyBpmnPath,
                historicalGeoPath,
                historicalLayoutPath,
                outputDir);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> save(@RequestBody TmsdConfigView view) {
        try {
            Path file = ConfigFileLocator.resolve(configFileOverride);

            YamlConfigUpdater.update(file, "ontology", "main-path",
                    requireNotBlank(view.ontologyMainPath(), "本体路径（ontology.main-path）"));
            YamlConfigUpdater.update(file, "ontology", "bpmn-path",
                    requireNotBlank(view.ontologyBpmnPath(), "BPMN 路径（ontology.bpmn-path）"));
            YamlConfigUpdater.update(file, "tmsd", "historical-geo-path",
                    requireNotBlank(view.historicalGeoPath(), "塔架几何输入路径"));
            YamlConfigUpdater.update(file, "tmsd", "historical-layout-path",
                    requireNotBlank(view.historicalLayoutPath(), "项目布局表路径"));
            YamlConfigUpdater.update(file, "tmsd", "output-dir",
                    requireNotBlank(view.outputDir(), "输出目录"));

            log.info("TMSD 配置已写回 {}", file);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "已保存到 application.yaml，重启应用后生效。",
                    "file", file.toString()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "message", e.getMessage()));
        } catch (IOException | IllegalStateException e) {
            log.error("TMSD 配置写回失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false, "message", "保存失败：" + e.getMessage()));
        }
    }

    private static String requireNotBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        return value.strip();
    }
}
