package com.ocean.ontologyframework.tmsd.web;

/**
 * 配置录入网页的传输对象。
 *
 * <p>字段对应 application.yaml 中的：
 * <ul>
 *   <li>{@code ontology.main-path} → {@link #ontologyMainPath}</li>
 *   <li>{@code ontology.bpmn-path} → {@link #ontologyBpmnPath}</li>
 *   <li>{@code tmsd.historical-geo-path} → {@link #historicalGeoPath}</li>
 *   <li>{@code tmsd.historical-layout-path} → {@link #historicalLayoutPath}</li>
 *   <li>{@code tmsd.output-dir} → {@link #outputDir}</li>
 * </ul>
 *
 * <p>注：平台内径匹配容差已迁移至本体 {@code TowerMidSection.owl} 表达，不再作为配置项。
 */
public record TmsdConfigView(
        String ontologyMainPath,
        String ontologyBpmnPath,
        String historicalGeoPath,
        String historicalLayoutPath,
        String outputDir) {
}
