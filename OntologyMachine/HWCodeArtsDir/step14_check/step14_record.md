# 第 14 步　词汇表测试与全流程测试 — 执行记录

- 应用：SiZhuBaZi（四柱命理）
- 日期：2026-10-30
- 前置：第 13 步闸门⑥ 已通过（v1.2）

## 1. 词汇表测试（新增）
- 测试类：`src/test/java/com/ocean/ontologyframework/sizhubazi/validation/SiZhuBaZiSkosTerminologyTest.java`
- 思路参考：`OntologyFrameworkPizza` 的 `PizzaSkosTerminologyTest` / `OntologyFrameworkPlumBlossom` 的 `PlumBlossomSkosTerminologyTest`
- 用例（8）：
  - P-1 术语表声明为 SKOS ConceptScheme 且概念非空
  - P-2 每个概念均带 `@zh` 首选标签
  - P-3 每个概念均带 `@en` 首选标签（闸门⑥ 第 3 项裁定）
  - B-1 `skos:broader` 目标均已定义、无自环
  - X-1 每个概念 `skos:exactMatch` 命中本体类/属性（无孤立词条）
  - X-2 本体每个带 `rdfs:label` 的类/属性均有对应概念（无遗漏、无造词）
  - S-1 同义词为中文、不与首选标签重复
  - S-2 每条同义词均带「# 源:」可追溯来源注释

## 2. 全流程回归（统一套件入口）
- 入口：`AllSiZhuBaZiSuiteTest`（已将 `SiZhuBaZiSkosTerminologyTest` 并入）
- 结果：`Tests run: 16, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS
  - 本体合规 `SiZhuBaZiOntologyComplianceTest`：8/8 通过
  - SKOS 词汇表 `SiZhuBaZiSkosTerminologyTest`：8/8 通过
  - BPMN 内存/真实引擎、SWRL+RabbitMQ：依环境（无 RabbitMQ/Camunda）跳过，报告 0 run
- 命令：`mvn -o -Dtest=AllSiZhuBaZiSuiteTest test`（模块目录，离线）
- 构建链可复现（★铁律 1/2/19）：`mvn -o -f pom.xml -pl OntologyFrameworkSiZhuBaZi -am test -Dtest=AllSiZhuBaZiSuiteTest -Dsurefire.failIfNoSpecifiedTests=false`
  - Reactor：OntopOBDAHandler → OpenlletResolver → Utilities → OntologyFrameworkSiZhuBaZi 全部 SUCCESS，BUILD SUCCESS

## 3. 第 13 步回填（随本步完成）
- `yongshen:Tiaohou` 别名「调候」出处由【待考】回填为「《穷通宝鉴》（《造化元钥》）·五行总论及各干逐月取用」
- 归档：`design/四书原文/穷通宝鉴（清·余春台辑·徐乐吾评注）.pdf`、`design/四书全文整理/穷通宝鉴.txt`
- 清理：删除 7 条与首选标签重复的 `skos:altLabel`（SKOS 规范不允许）；别名 96 → 89

## 4. 产出
- 词汇表测试类：`SiZhuBaZiSkosTerminologyTest.java`
- 全流程回归结果：本目录 `regression` 证据（surefire）
