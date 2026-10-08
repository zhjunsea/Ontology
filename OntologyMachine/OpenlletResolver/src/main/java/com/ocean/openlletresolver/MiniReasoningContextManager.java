package com.ocean.openlletresolver;

import openllet.owlapi.OpenlletReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 迷你本体上下文的缓存管理器：
 *  - 按 cacheKey 缓存 TBox 公理集合（避免每次重建）
 *  - 按 cacheKey 缓存 MiniContext（避免每次重新推理）
 *  - 异常时自动 dispose 对应上下文
 *  - 支持按前缀批量清理
 *
 * 与任何业务无关。
 */
public class MiniReasoningContextManager {

    private static final Logger log = LoggerFactory.getLogger(MiniReasoningContextManager.class);

    private final Map<String, Set<OWLAxiom>> tboxCache = new ConcurrentHashMap<>();
    private final Map<String, MiniContext> contextCache = new ConcurrentHashMap<>();

    /**
     * 以 cacheKey 为键获取/创建上下文，并对其执行 action。
     * 若 action 抛异常，则对应上下文会被 dispose 并移除（下次重建）。
     *
     * @param cacheKey     缓存键（例如 patientIri + "#STAGE_LJ"）
     * @param tboxSupplier 生成 TBox 公理集合（只在首次构建时调用）
     * @param aboxBuilder  依据 df 生成 ABox 公理集合
     * @param action       对 MiniContext 的操作
     */
    public <T> T withContext(String cacheKey,
                             Supplier<Set<OWLAxiom>> tboxSupplier,
                             Function<OWLDataFactory, Set<OWLAxiom>> aboxBuilder,
                             Function<MiniContext, T> action) {
        Set<OWLAxiom> tbox = tboxCache.computeIfAbsent(cacheKey, k -> tboxSupplier.get());
        MiniContext ctx = contextCache.computeIfAbsent(cacheKey,
                k -> buildContext(k, tbox, aboxBuilder));
        try {
            return action.apply(ctx);
        } catch (RuntimeException e) {
            MiniContext removed = contextCache.remove(cacheKey);
            if (removed != null) removed.dispose();
            throw e;
        }
    }

    /**
     * 与 withContext 相同，但只返回 MiniContext（适合需要在外部多次调用其方法的场景）。
     */
    public MiniContext getOrCreate(String cacheKey,
                                   Supplier<Set<OWLAxiom>> tboxSupplier,
                                   Function<OWLDataFactory, Set<OWLAxiom>> aboxBuilder) {
        Set<OWLAxiom> tbox = tboxCache.computeIfAbsent(cacheKey, k -> tboxSupplier.get());
        return contextCache.computeIfAbsent(cacheKey,
                k -> buildContext(k, tbox, aboxBuilder));
    }

    /** 清空指定前缀的所有缓存并 dispose 对应上下文。 */
    public void clearByPrefix(String prefix) {
        tboxCache.keySet().removeIf(k -> k.startsWith(prefix));
        contextCache.entrySet().removeIf(e -> {
            if (e.getKey().startsWith(prefix)) {
                try { e.getValue().dispose(); } catch (Exception ignored) {}
                return true;
            }
            return false;
        });
    }

    /** 清空全部缓存并 dispose 全部上下文。 */
    public void disposeAll() {
        contextCache.values().forEach(MiniContext::dispose);
        contextCache.clear();
        tboxCache.clear();
    }

    // ==================== 内部 ====================

    private MiniContext buildContext(String cacheKey,
                                     Set<OWLAxiom> tboxAxioms,
                                     Function<OWLDataFactory, Set<OWLAxiom>> aboxBuilder) {
        long t0 = System.currentTimeMillis();
        OWLOntologyManager mgr = OWLManager.createOWLOntologyManager();
        try {
            OWLOntology ont = mgr.createOntology(
                    IRI.create("urn:mini:" + System.nanoTime()));
            mgr.addAxioms(ont, tboxAxioms);

            OWLDataFactory df = mgr.getOWLDataFactory();
            Set<OWLAxiom> abox = aboxBuilder == null ? null : aboxBuilder.apply(df);
            if (abox != null && !abox.isEmpty()) mgr.addAxioms(ont, abox);

            OWLReasoner r = new OpenlletReasonerFactory().createReasoner(ont);
            r.precomputeInferences(InferenceType.CLASS_ASSERTIONS);

            log.info("[MiniContext:{}] axioms={}, realize 耗时 {} ms",
                    cacheKey, ont.getAxiomCount(), System.currentTimeMillis() - t0);
            return new MiniContext(mgr, ont, df, r);
        } catch (OWLOntologyCreationException e) {
            throw new RuntimeException("创建迷你本体失败: " + cacheKey, e);
        }
    }
}