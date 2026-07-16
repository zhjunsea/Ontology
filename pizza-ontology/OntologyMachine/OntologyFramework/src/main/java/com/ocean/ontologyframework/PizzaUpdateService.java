package com.ocean.ontologyframework;

public class PizzaUpdateService {
/*
    private final OWLOntology tboxOntology;
    private final OWLOntology aboxOntology;
    private final GenericDbWriter dbWriter;

    public PizzaUpdateService(OWLOntology tbox, OWLOntology abox, GenericDbWriter writer) {
        this.tboxOntology = tbox;
        this.aboxOntology = abox;
        this.dbWriter = writer;
    }

    public boolean safeUpdate(OWLNamedIndividual individual, Set<OWLAxiom> newTripleAxioms) {
        OWLOntologyManager manager = tboxOntology.getOWLOntologyManager();
        OWLReasoner reasoner = null;

        try {
            // ========== 1. 临时加入新三元组到 ABox ==========
            manager.addAxioms(tboxOntology, newTripleAxioms);

            // ========== 2. 创建 Openllet 推理机 ==========
            // ✅ Openllet 专属工厂类
            OpenlletReasonerFactory factory = OpenlletReasonerFactory.getInstance();

            // 可选：配置进度监控器（适合长时间推理时给前端反馈）
            SimpleConfiguration config = new SimpleConfiguration(new ReasonerProgressMonitor() {
                @Override public void reasonerTaskStarted(String taskName) {
                    System.out.println("🔄 [Openllet] " + taskName);
                }
                @Override public void reasonerTaskStopped() {}
                @Override public void reasonerTaskProgressChanged(int value, int max) {}
                @Override public void reasonerTaskBusy() {}
            });

            reasoner = factory.createReasoner(tboxOntology, config);

            // ========== 3. 一致性检查 ==========
            // Openllet 的 isConsistent() 内部会自动触发必要的预计算
            boolean isConsistent = reasoner.isConsistent();

            if (isConsistent) {
                System.out.println("✅ Openllet 一致性检查通过，写入数据库...");
                dbWriter.persistTriples(individual, newTripleAxioms);
                return true;
            } else {
                System.err.println("❌ Openllet 检测到本体不一致！拒绝写入。");
                // 💡 Openllet 支持获取不一致解释
                    var explanations = reasoner.getExplanationService()
                         .getExplanations(aboxOntology.getInconsistentOntology());
                return false;
            }

        } catch (Exception e) {
            System.err.println("更新异常: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            // ========== 4. 无论成功失败，清理临时公理 ==========
            cleanupTemporaryAxioms(manager, newTripleAxioms);

            // ⚠️ Openllet 必须显式 dispose，否则线程池不会释放
            if (reasoner != null) {
                reasoner.dispose();
            }
        }
    }

    private void cleanupTemporaryAxioms(OWLOntologyManager manager, Set<OWLAxiom> tempAxioms) {
        try {
            manager.removeAxioms(aboxOntology, tempAxioms);
            System.out.println("🧹 临时三元组已清除，ABox 恢复干净状态。");
        } catch (OWLOntologyChangeException e) {
            System.err.println("⚠️ 清理失败: " + e.getMessage());
        }
    }*/
}