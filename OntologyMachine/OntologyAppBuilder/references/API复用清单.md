# API 复用清单（★铁律 21）

> **权威源**：`../../Reference/公共铁律.md` 铁律 21 及其 `21.1` 禁止重写清单。
> **约束**：新应用**必须优先复用**以下现存 API；**禁止**自己重写等价功能。新增/改代码前先对照 `Reference/` 三份 API 文档排查。
> 三份文档：`../Reference/OpenlletResolver-API.md`、`OntopOBDAHandler-API.md`、`Utilities-API.md`。

## 禁止重写的 API（必须复用）

| 功能 | 必须复用的现存 API | 禁止的写法 |
|---|---|---|
| 加载本体 | `OntologyService` / `BackendService.getInstance(path)` | `OWLManager.createOWLOntologyManager()` + `loadOntologyFromOntologyDocument` |
| 创建推理器 | `ReasonerService` / `BackendService.getReasonerService().getReasoner()` | `OpenlletReasonerFactory...createReasoner` |
| DataFactory | `BackendService.getDataFactory()` | `OWLManager.getOWLDataFactory()` |
| 构建 OWL 公理 | `GenericAxiomBuilder.buildAxioms(...)` / `buildAxiomsWithIRI(...)` | 逐条手写 `df.getOWLClassAssertionAxiom(...)` |
| 临时注入公理→推理→回滚 | `BackendService.withTempAxioms(...)` | 手动 add/remove/flush 三段式 |
| 定位 application.yaml | `ConfigFileLocator.resolve(...)` | `new File("src/main/resources/application.yaml")` |
| 更新 YAML | `YamlConfigUpdater.update(...)` | 手写 YAML 文本 |
| rdfs:label 匹配 | `OntologyLabelMatcher.resolveMatchedWord(...)` | 自己正则解析 OWL XML |
| RabbitMQ 发送 | `RabbitMqHandler.send(...)` | `new RabbitTemplate(...)` / `ConnectionFactory` |
| 目录归档 zip | `ArchiveSupport.zip(...)` | `new ZipOutputStream(...)` |
| 文件名清洗 | `FileTransferSupport.safeName(...)` | 自己 `replaceAll(...)` |
| 文件上传存盘 | `FileTransferSupport.save(...)` | `file.transferTo(...)` |
| Content-Disposition 头 | `FileTransferSupport.contentDisposition(...)` | 自己拼 `attachment; filename=...` |
| Camunda 部署流程 | `ProcessOrchestrator.deployIfAbsent(...)` | `client.newDeployResourceCommand()...` |
| Camunda 启动实例 | `ProcessOrchestrator.start(...)` | `client.newCreateInstanceCommand()...` |
| Camunda 读状态 | `ProcessOrchestrator.fetchInstance(...)` / `statusOf(...)` | `client.newProcessInstanceGetRequest()...` |
| Camunda 读变量 | `ProcessOrchestrator.readVariables(...)` | `client.newVariableSearchRequest()...` |
| IRI fragment 提取 | `ObdaQueryUtils.fragmentOf(...)` | `iri.substring(iri.lastIndexOf('/') + 1)` |
| IRI 补全 | `ObdaQueryUtils.toFullIri(...)` | 自己拼 `baseNs + fragment` |
| 查询 label | `ObdaQueryUtils.queryLabel(...)` | 自己执行 SPARQL 查 `rdfs:label` |
| SKOS 同义词 | `SkosSynonymReader.getAllSynonyms(...)` / `buildSynonymDictionary()` | 自己正则解析 SKOS TTL |
| Openllet 调优 | `OpenlletTuning.apply()` | 自己设 Openllet 系统属性 |
| 迷你推理上下文 | `MiniReasoningContextManager.withContext(...)` | 自建临时 manager + ontology + reasoner |
| JobWorker 初始化 | 继承 `OntologyWorkerSupport` | `@PostConstruct` 手写初始化链路 |
| 连接池 | `ConnectionPoolManager` | `new HikariDataSource(...)` |
| .obda 解析 | `OntopMappingResolver` | 自己解析 .obda 文本 |
| 参数化写库 | `GenericDbWriter` / `OBDAHandler.executeInTransaction(...)` | `DriverManager.getConnection` + `PreparedStatement` |
| 实例增删改 | `InsertService` / `UpdateService` / `DeleteService` | 自己拼 SQL + JDBC |
| SWRL 监听 | `SwrlRuleTriggerListener` | 自己注册本体变更监听器 |

## 合法例外（不视为违规）
1. **现存 API 功能不覆盖**：先**扩展现存 API**，再复用。
2. **架构代价过大**：如 `EnvPrepare` 为纯 JDK 模块引入 `Utilities` 会传递重型依赖 → 需**向用户报告并确认**。
3. **工程禁改约束**（★7）：因禁改 `OpenlletResolver`/`OntopOBDAHandler`，某些方法需在业务模块就近实现（如 `DefinitionGapUtils`），语义与现存 API 确有差异。

## 违规处理
- 先向用户**报告违规清单**（文件、行号、违规代码、应复用 API），确认后再修。
- 修复优先级：**生产代码 > 测试代码**；**全链路重写 > 单点未复用**。
