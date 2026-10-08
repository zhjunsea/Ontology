package com.ocean.utilities;

import com.ocean.openlletresolver.BackendService;
import com.ocean.openlletresolver.QueryService;

/**
 * JobWorker 的「本体推理链路」初始化骨架。
 *
 * <p>把各 JobWorker 中重复的初始化顺序沉淀为模板方法 {@link #initOntologyPipeline()}：
 * <ol>
 *   <li>{@link #applyOpenlletTuning()} —— 应用 Openllet 库级调优（默认空实现，按需覆盖）；</li>
 *   <li>{@link #createBackendService()} —— 由子类按自身配置创建 {@link BackendService}；</li>
 *   <li>基于 {@link BackendService} 创建 {@link QueryService}；</li>
 *   <li>{@link #afterBackendServiceReady()} —— 子类扩展点（默认空实现）。</li>
 * </ol>
 *
 * <p>子类在 {@code @PostConstruct} 中调用本骨架，并将 {@code backendService} /
 * {@code queryService} 作为受保护字段共享给各 {@code @JobWorker} 方法。
 */
public abstract class OntologyWorkerSupport {

    protected BackendService backendService;
    protected QueryService queryService;

    /**
     * 初始化本体推理链路（模板方法）。子类应在 {@code @PostConstruct} 中调用。
     *
     * @throws Exception 初始化失败
     */
    protected final void initOntologyPipeline() throws Exception {
        applyOpenlletTuning();
        backendService = createBackendService();
        if (backendService == null) {
            throw new IllegalStateException("BackendService 初始化失败，请检查本体路径和 OBDA 连接");
        }
        queryService = new QueryService(backendService);
        afterBackendServiceReady();
    }

    /** 应用 Openllet 库级调优（默认空实现）。 */
    protected void applyOpenlletTuning() {
    }

    /** 由子类使用自身配置创建 {@link BackendService}。 */
    protected abstract BackendService createBackendService() throws Exception;

    /** {@link BackendService} / {@link QueryService} 就绪后的扩展点（默认空实现）。 */
    protected void afterBackendServiceReady() throws Exception {
    }
}
