# 结构校验报告

- 应用目录：`D:\work\Ontology\OntologyMachine\OntologyFrameworkSiZhuBaZi`
- 应用简称：SiZhuBaZi
- 生成时间：2026-11-01T18:20:08
- 汇总：ERROR=0 WARN=0

[INFO ] (completeness) 存在：构建文件 pom.xml → pom.xml
[INFO ] (completeness) 存在：环境脚本 start-env.cmd → start-env.cmd
[INFO ] (completeness) 存在：环境启动器 EnvPrepare-*.jar → EnvPrepare-0.0.1-SNAPSHOT.jar
[INFO ] (completeness) 存在：设计·顶层设计（第 1 步） → design\SiZhuBaZi应用-顶层设计.md
[INFO ] (completeness) 存在：设计·流程设计（第 2 步·闸门①） → design\SiZhuBaZi应用-流程设计.md
[INFO ] (completeness) 存在：设计·本体顶层设计（第 3 步·闸门②；⑧⑨留痕） → design\SiZhuBaZi应用-本体顶层设计.md
[INFO ] (completeness) 存在：设计·本体核心设计（第 4 步·闸门③；⑦留痕） → design\SiZhuBaZi应用-本体核心设计.md
[INFO ] (completeness) 存在：设计·应用架构设计（第 7 步·闸门④；⑩⑪留痕） → design\SiZhuBaZi应用-应用架构设计.md
[INFO ] (completeness) 存在：设计·测试设计文档（第 8 步·闸门⑤） → design\SiZhuBaZi应用-测试设计文档.md
[INFO ] (completeness) 存在：本体·总入口 → ontology\sizhubazi-all.owl
[INFO ] (completeness) 存在：本体·IRI 目录 catalog-v001.xml → ontology\catalog-v001.xml
[INFO ] (completeness) 存在：本体·SKOS 术语表 → ontology\sizhubazi-terminology.ttl
[INFO ] (completeness) 存在：本体·BPMN 流程文件 → ontology\bpmn\sizhubaziProcess.bpmn
[INFO ] (completeness) 存在：配置 application.yaml → src\main\resources\application.yaml
[INFO ] (completeness) 存在：前端 static/index.html → src\main\resources\static\index.html
[INFO ] (completeness) 存在：主代码·Application → src\main\java\com\ocean\ontologyframework\sizhubazi\SiZhuBaZiApplication.java
[INFO ] (completeness) 存在：主代码·JobWorker → src\main\java\com\ocean\ontologyframework\sizhubazi\SiZhuBaZiOntologyJobWorker.java
[INFO ] (completeness) 存在：主代码·Controller → src\main\java\com\ocean\ontologyframework\sizhubazi\web\SiZhuBaZiController.java
[INFO ] (completeness) 存在：测试·BPMN 端到端 → src\test\java\com\ocean\ontologyframework\sizhubazi\SiZhuBaZiBpmnInMemoryTest.java
[INFO ] (completeness) 存在：测试·聚合套件入口 → src\test\java\com\ocean\ontologyframework\sizhubazi\AllSiZhuBaZiSuiteTest.java
[INFO ] (completeness) 存在：本体·库环境目录 database/ → ontology\database
[INFO ] (completeness) 存在：IDEA 运行配置（工作区根） → .idea\runConfigurations\SiZhuBaZiApplication.xml
[INFO ] (contract) BPMN serviceTask 作业类型 22 个（节点实例 22 个）；@JobWorker 22 个
[INFO ] (contract) 绑定 OK：`aggregateReading` → @JobWorker(SiZhuBaZiOntologyJobWorker)
[INFO ] (contract) 绑定 OK：`analyzeChildChart` → @JobWorker(SiZhuBaZiOntologyJobWorker)
[INFO ] (contract) 绑定 OK：`analyzeFemaleChart` → @JobWorker(SiZhuBaZiOntologyJobWorker)
[INFO ] (contract) 绑定 OK：`analyzeFortuneCycles` → @JobWorker(SiZhuBaZiOntologyJobWorker)
[INFO ] (contract) 绑定 OK：`analyzeGanzhiRelations` → @JobWorker(SiZhuBaZiOntologyJobWorker)
[INFO ] (contract) 绑定 OK：`analyzeMarriageMatch` → @JobWorker(SiZhuBaZiOntologyJobWorker)
[INFO ] (contract) 绑定 OK：`analyzeNayinHiddenStems` → @JobWorker(SiZhuBaZiOntologyJobWorker)
[INFO ] (contract) 绑定 OK：`arrangeDeities` → @JobWorker(SiZhuBaZiOntologyJobWorker)
[INFO ] (contract) 绑定 OK：`assessDayMasterStrength` → @JobWorker(SiZhuBaZiOntologyJobWorker)
[INFO ] (contract) 绑定 OK：`assignTenGodsKin` → @JobWorker(SiZhuBaZiOntologyJobWorker)
[INFO ] (contract) 绑定 OK：`castFourPillars` → @JobWorker(SiZhuBaZiOntologyJobWorker)
[INFO ] (contract) 绑定 OK：`determinePattern` → @JobWorker(SiZhuBaZiOntologyJobWorker)
[INFO ] (contract) 绑定 OK：`generateCategoryVerdicts` → @JobWorker(SiZhuBaZiOntologyJobWorker)
[INFO ] (contract) 绑定 OK：`generateMoralAdvice` → @JobWorker(SiZhuBaZiOntologyJobWorker)
[INFO ] (contract) 绑定 OK：`inferTimingAndStages` → @JobWorker(SiZhuBaZiOntologyJobWorker)
[INFO ] (contract) 绑定 OK：`interpretResult` → @JobWorker(SiZhuBaZiOntologyJobWorker)
[INFO ] (contract) 绑定 OK：`judgeChartLevel` → @JobWorker(SiZhuBaZiOntologyJobWorker)
[INFO ] (contract) 绑定 OK：`parseBirthInput` → @JobWorker(SiZhuBaZiOntologyJobWorker)
[INFO ] (contract) 绑定 OK：`persistAndReturn` → @JobWorker(SiZhuBaZiOntologyJobWorker)
[INFO ] (contract) 绑定 OK：`selectYongShen` → @JobWorker(SiZhuBaZiOntologyJobWorker)
[INFO ] (contract) 绑定 OK：`sendAlert` → @JobWorker(SiZhuBaZiOntologyJobWorker)
[INFO ] (contract) 绑定 OK：`synthesizeFortuneChart` → @JobWorker(SiZhuBaZiOntologyJobWorker)
[INFO ] (gates) 闸门①（第 2 步·流程设计） 留痕节存在：design/SiZhuBaZi应用-流程设计.md
[INFO ] (gates) 闸门②（第 3 步·本体顶层设计） 留痕节存在：design/SiZhuBaZi应用-本体顶层设计.md
[INFO ] (gates) 闸门③（第 4 步·本体核心设计） 留痕节存在：design/SiZhuBaZi应用-本体核心设计.md
[INFO ] (gates) 闸门④（第 7 步·应用架构设计） 留痕节存在：design/SiZhuBaZi应用-应用架构设计.md
[INFO ] (gates) 闸门⑤（第 8 步·测试设计文档） 留痕节存在：design/SiZhuBaZi应用-测试设计文档.md
[INFO ] (gates) 闸门⑦（第 6 步·典型 ABox 确认） 留痕标记存在：design/SiZhuBaZi应用-本体核心设计.md
[INFO ] (gates) 闸门⑧（第 9 步前·是否 TBox/ABox 分离） 留痕标记存在：design/SiZhuBaZi应用-本体顶层设计.md
[INFO ] (gates) 闸门⑨（第 10 步·是否 TBox 拆分） 留痕标记存在：design/SiZhuBaZi应用-本体顶层设计.md
[INFO ] (gates) 闸门⑩（第 11 步前·是否使用数据库） 留痕标记存在：design/SiZhuBaZi应用-应用架构设计.md
[INFO ] (gates) 闸门⑪（第 12 步前·如何导入/生成完整数据） 留痕标记存在：design/SiZhuBaZi应用-应用架构设计.md
[INFO ] (rules) 检测到 ABox 文件 1 个（TBox/ABox 分离）
[INFO ] (rules) 主本体未活动导入 ABox（符合 ★55）
[INFO ] (rules) ontology/ 中发现 owl:Restriction（★12 OK）
[INFO ] (rules) catalog-v001.xml 可解析