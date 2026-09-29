package com.ocean.openlletresolver;

import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 通用 SWRL 规则推导结果监听器
 * ⚠️ 必须与 BackendService 共享同一个 OWLOntologyManager 才能收到变更事件
 * @param <T> 外部业务回调接收的参数类型
 */
public class SwrlRuleTriggerListener<T> {

    private static final Logger log = LoggerFactory.getLogger(SwrlRuleTriggerListener.class);

    private final Config<T> config;
    private final BackendService backendService;  // ← 复用全局服务
    private Set<String> previousSnapshot;
    private final ExecutorService asyncExecutor;
    private volatile boolean running = false;

    // ─── 配置类 ───────────────
    public static class Config<T> {
        String targetClassIri;         // 要监听的推导结果类 IRI
        Consumer<T> onTriggered;       // 规则激发后的外部回调函数
        boolean watchABoxOnly = true;  // 是否仅响应 ABox 变化
        int threadPoolSize = 4;        // 异步回调线程池大小
        Class<T> callbackParamType;    // 回调参数类型

        // ✅ 不再需要 ontologyPath，本体由 BackendService 统一管理
        public Config(String targetClassIri, Consumer<T> onTriggered, Class<T> callbackParamType) {
            this.targetClassIri = targetClassIri;
            this.onTriggered = onTriggered;
            this.callbackParamType = callbackParamType;
        }
    }

    // ✅ 通过构造函数注入 BackendService
    public SwrlRuleTriggerListener(Config<T> config, BackendService backendService) {
        this.config = Objects.requireNonNull(config, "config 不能为null");
        this.backendService = Objects.requireNonNull(backendService, "backendService 不能为null");
        this.asyncExecutor = Executors.newFixedThreadPool(config.threadPoolSize);
    }

    /**
     * 启动监听（非阻塞）
     */
    public void start() {
        if (running) {
            log.warn("[SwrlListener] 已在运行中，忽略重复启动");
            return;
        }

        // ✅ 复用 BackendService 的全局 Manager 和 Ontology
        OWLOntologyManager manager = backendService.getOntologyService().getManager();
        OWLOntology ontology = backendService.getOntologyService().gettBoxOntology();

        // ✅ 复用 BackendService 的全局 Reasoner（不重新创建）
        previousSnapshot = queryTargetInstances(ontology);

        log.info("🚀 [SwrlListener] 已启动 | 监控类: {} | 初始实例数: {} | Manager哈希: {}",
                config.targetClassIri, previousSnapshot.size(), System.identityHashCode(manager));

        // ✅ 注册到【同一个】Manager 上，这样才能收到 handleChange
        manager.addOntologyChangeListener(changes -> handleChange(changes, ontology));
        running = true;
    }

    /**
     * 变更处理核心逻辑
     */
    private void handleChange(List<? extends OWLOntologyChange> changes,
                              OWLOntology ontology) {
        if (!running) return;

        try {
            // 过滤无关变更
            if (config.watchABoxOnly) {
                boolean hasRelevantChange = changes.stream()
                        .anyMatch(c -> c instanceof AddAxiom || c instanceof RemoveAxiom);
                if (!hasRelevantChange) return;
            }

            log.debug("📨 [SwrlListener] 收到 {} 条本体变更事件", changes.size());

            // ✅ 复用全局 Reasoner，flush 后查询
            backendService.getReasonerService().getReasoner().flush();
            Set<String> current = queryTargetInstances(ontology);
            Set<String> newlyTriggered = diff(previousSnapshot, current);

            if (!newlyTriggered.isEmpty()) {
                log.info("🔔 [SwrlListener] 检测到 {} 个新触发实例: {}",
                        newlyTriggered.size(), newlyTriggered);
                for (String instanceName : newlyTriggered) {
                    asyncExecutor.submit(() -> {
                        try {
                            config.onTriggered.accept(buildCallbackParam(instanceName));
                        } catch (Exception e) {
                            log.error("❌ [SwrlListener] 回调执行失败 [{}]: {}",
                                    instanceName, e.getMessage(), e);
                        }
                    });
                }
            }

            previousSnapshot = current;
        } catch (Exception e) {
            log.error("❌ [SwrlListener] 变更处理异常: {}", e.getMessage(), e);
        }
    }

    // ✅ 复用全局 Reasoner 查询
    private Set<String> queryTargetInstances(OWLOntology ontology) {
        OWLDataFactory df = ontology.getOWLOntologyManager().getOWLDataFactory();
        OWLClass targetClass = df.getOWLClass(IRI.create(config.targetClassIri));
        NodeSet<OWLNamedIndividual> instances =
                backendService.getReasonerService().getReasoner().getInstances(targetClass, false);
        return instances.getFlattened().stream()
                .map(i -> i.getIRI().toString())
                .collect(Collectors.toSet());
    }

    private Set<String> diff(Set<String> oldSet, Set<String> newSet) {
        Set<String> result = new HashSet<>(newSet);
        result.removeAll(oldSet);
        return result;
    }

    @SuppressWarnings("unchecked")
    protected T buildCallbackParam(String instanceIri) {
        return (T) instanceIri;
    }

    public void shutdown() {
        running = false;
        asyncExecutor.shutdown();
        // ⚠️ 不要 dispose reasoner！它是 BackendService 的全局资源
        // 只移除监听器即可
        try {
            OWLOntologyManager manager = backendService.getOntologyService().getManager();
            manager.removeOntologyChangeListener(changes -> {});
        } catch (Exception ignored) {}
        log.info("🛑 [SwrlListener] 已关闭");
    }
}