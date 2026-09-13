这份文档是基于您提供的 Java 源码文件整理的 API 技术参考手册。文档按照**系统架构分层**（控制层、服务/处理层、数据访问层、工具/模型层）进行组织，旨在帮助开发者快速理解各组件职责及调用规范。

---

# TCM-KG VKG 系统 API 参考文档

## 1. 概述
本系统是一套基于 **Virtual Knowledge Graph (VKG)** 技术的中医药知识图谱构建与查询平台。核心功能包括：
-   **OBDA 映射管理**：解析 R2RML/OBDA 配置，建立关系型数据库与本体之间的语义映射。
-   **SPARQL 查询**：支持通过 SPARQL 端点实时查询底层 MySQL 数据，无需预先物化三元组。
-   **数据写入**：提供批量写入接口，将结构化数据持久化至图存储或关系库。
-   **ABox 修复**：自动检测并修复实例数据中的类型不一致问题。

---

## 2. 控制层 (Controller Layer)

### `VkgController`
> **职责**：系统的统一 HTTP 入口，封装 VKG 查询与元数据获取接口。

| 方法签名 | HTTP | 路径 | 描述 |
| :--- | :--- | :--- | :--- |
| `querySparql(String sparql, String format)` | POST | `/api/vkg/query` | 执行 SPARQL 查询并返回结果 |
| `getMappingMetadata()` | GET | `/api/vkg/metadata` | 获取当前加载的 OBDA 映射元数据 |
| `healthCheck()` | GET | `/api/vkg/health` | 服务健康检查 |

#### `querySparql`
-   **介绍**：接收前端或第三方传来的 SPARQL 语句，委托 `OBDAHandler` 执行查询，并将结果序列化为指定格式。内部已做异常捕获，返回标准化错误响应。
-   **输入参数**：
    -   `sparql` (String): 合法的 SPARQL 1.1 查询语句。
    -   `format` (String, Optional): 结果格式，支持 `json`, `csv`, `turtle`。默认为 `json`。
-   **输出**：`ResponseEntity<String>` — 查询结果的序列化字符串；失败时返回 400/500 状态码及错误详情。

#### `getMappingMetadata`
-   **介绍**：返回当前系统中已加载的所有 Mapping 信息，包括谓词数量、映射规则数等，用于前端展示或调试。
-   **输入参数**：无
-   **输出**：`Map<String, Object>` — 包含 `predicateCount`, `mappingRules`, `dataSource` 等键值对。

---

## 3. 服务/处理层 (Service & Handler Layer)

### `OBDAHandler`
> **职责**：VKG 引擎的核心封装，管理 Ontop 引擎的生命周期与查询执行。

| 方法 | 描述 |
| :--- | :--- |
| `init(OntopMappingResolver resolver, ConnectionPoolManager poolMgr)` | 初始化 Ontop 推理引擎 |
| `executeQuery(String sparql)` | 执行 SPARQL 查询并返回原生结果集 |
| `close()` | 安全关闭引擎，释放资源 |

#### `init`
-   **介绍**：根据传入的映射解析器和连接池管理器构建 Ontop 的 `OBDASystem`。**必须在任何查询前调用**，且仅调用一次。内部会验证映射完整性与数据库连通性。
-   **输入参数**：
    -   `resolver` (OntopMappingResolver): 已解析完成的映射对象。
    -   `poolMgr` (ConnectionPoolManager): 已初始化的数据库连接池。
-   **输出**：`void` — 初始化失败抛出 `VkgInitializationException`。

#### `executeQuery`
-   **介绍**：将 SPARQL 转换为 SQL 并在底层数据库执行。返回 Ontop 原生的 `OBDAResultSet`，调用方需自行遍历或交由序列化器处理。**注意**：返回的结果集使用后必须关闭，否则会导致连接泄漏。
-   **输入参数**：`sparql` (String)
-   **输出**：`OBDAResultSet` — 虚拟结果集包装器。

### `OntopMappingResolver`
> **职责**：解析 `.obda` / `.r2rml` 文件及本体文件，生成 Ontop 可消费的映射对象。

| 方法 | 描述 |
| :--- | :--- |
| `resolve(String obdaPath, String ontologyPath)` | 解析映射文件与本体文件 |
| `getMappingCount()` | 获取已解析的映射规则总数 |
| `validate()` | 校验映射与本体的兼容性 |

#### `resolve`
-   **介绍**：读取本地或 classpath 下的 OBDA 映射文件和 OWL 本体文件，构建内存中的映射模型。支持 R2RML 和 Ontop 原生 `.obda` 两种格式，根据文件扩展名自动识别。
-   **输入参数**：
    -   `obdaPath` (String): 映射文件路径。
    -   `ontologyPath` (String): OWL 本体文件路径。
-   **输出**：`OntopMappingResolver` (this) — 支持链式调用。解析失败抛出 `MappingParseException`。

### `ABoxTypeFixer`
> **职责**：后处理工具，修复从关系库映射生成的 ABox 实例中类型断言缺失或错误的问题。

| 方法 | 描述 |
| :--- | :--- |
| `fix(WriteResult result, String ontologyPath)` | 根据本体约束修复写入结果中的类型问题 |
| `getFixReport()` | 获取最近一次修复操作的详细报告 |

#### `fix`
-   **介绍**：在数据写入完成后调用。扫描 `WriteResult` 中记录的实体，对照本体中的类层次与属性定义，补充缺失的 `rdf:type` 断言或纠正错误的类型归属。**典型场景**：关系表中某字段同时对应多个本体类，写入时仅生成了部分类型声明。
-   **输入参数**：
    -   `result` (WriteResult): 上一步写入操作的结果对象。
    -   `ontologyPath` (String): 本体文件路径，用于获取类型约束。
-   **输出**：`WriteResult` — 修复后的新结果对象（不可变设计，不修改原对象）。

---

## 4. 数据访问层 (Data Access Layer)

### `GenericDbWriter`
> **职责**：通用的图/关系数据批量写入器，屏蔽底层存储差异。

| 方法 | 描述 |
| :--- | :--- |
| `write(List<Triple> triples, WriteConfig config)` | 批量写入三元组数据 |
| `writeBatch(InputStream rdfStream, RDFFormat format)` | 从 RDF 流批量写入 |
| `flush()` | 强制刷新缓冲区 |

#### `write`
-   **介绍**：将三元组列表按配置的批次大小分批写入目标存储。内部使用事务保证原子性，单批失败自动回滚并记录到 `WriteResult` 中，不影响后续批次。支持去重策略配置。
-   **输入参数**：
    -   `triples` (List\): 待写入的三元组集合。
    -   `config` (WriteConfig): 写入配置，含 `batchSize`, `deduplicate`, `targetStore` 等。
-   **输出**：`WriteResult` — 写入结果摘要。

#### `writeBatch`
-   **介绍**：面向大文件导入的流式写入接口。解析 RDF 流并按批次提交，内存占用恒定，适合 GB 级数据导入。
-   **输入参数**：
    -   `rdfStream` (InputStream): RDF 数据输入流。
    -   `format` (RDFFormat): 数据格式枚举（TURTLE, NTRIPLES, RDFXML）。
-   **输出**：`WriteResult`

### `ConnectionPoolManager`
> **职责**：数据库连接池的统一管理器，支持多数据源切换与生命周期管理。

| 方法 | 描述 |
| :--- | :--- |
| `initialize(DataSourceConfig config)` | 初始化连接池 |
| `getConnection()` | 从池中获取连接 |
| `release(Connection conn)` | 归还连接到池中 |
| `shutdown()` | 关闭连接池，释放所有资源 |

#### `initialize`
-   **介绍**：根据配置创建 HikariCP 连接池实例。支持运行时重新初始化（先关闭旧池再创建新池），适用于多租户或数据源热切换场景。
-   **输入参数**：`config` (DataSourceConfig) — 含 JDBC URL、用户名、密码、最大连接数等。
-   **输出**：`void`

#### `getConnection`
-   **介绍**：获取一个可用的数据库连接。**重要**：调用方必须在使用完毕后通过 `release()` 归还，或使用 try-with-resources 模式。超时未获取到连接时抛出 `ConnectionTimeoutException`。
-   **输出**：`java.sql.Connection`

---

## 5. 模型/工具层 (Model & Utility)

### `WriteResult`
> **职责**：不可变的写入操作结果封装，携带成功/失败统计与诊断信息。

| 字段/方法 | 类型 | 描述 |
| :--- | :--- | :--- |
| `getTotalCount()` | int | 本次操作涉及的总记录数 |
| `getSuccessCount()` | int | 成功写入的记录数 |
| `getFailedCount()` | int | 失败的记录数 |
| `getErrors()` | List\ | 失败记录详情列表 |
| `isPartialFailure()` | boolean | 是否存在部分失败 |
| `merge(WriteResult other)` | WriteResult | 合并两个结果对象（用于多批次汇总） |

> ⚠️ **使用注意**：`WriteResult` 为不可变对象，`merge()` 返回新实例。在循环写入场景中应使用累加器模式收集结果。

---

## 6. 典型调用流程

```
┌─────────────┐     ┌──────────────┐     ┌─────────────────┐
│ VkgController│────▶│ OBDAHandler  │────▶│ConnectionPoolMgr│
└─────────────┘     └──────┬───────┘     └────────┬────────┘
                           │                       │
                  ┌────────▼────────┐              │
                  │OntopMapping     │              │
                  │Resolver         │              │
                  └─────────────────┘              │
                                                   │
┌─────────────┐     ┌──────────────┐     ┌────────▼────────┐
│ ABoxTypeFixer│◀───│GenericDbWriter│◀───│  MySQL / GraphDB │
└─────────────┘     └──────────────┘     └─────────────────┘
       │                    │
       ▼                    ▼
   WriteResult          WriteResult
```

**标准查询流程：**
1.  应用启动 → `ConnectionPoolManager.initialize()` → `OntopMappingResolver.resolve()` → `OBDAHandler.init()`
2.  请求到达 → `VkgController.querySparql()` → `OBDAHandler.executeQuery()` → 返回结果
3.  应用关闭 → `OBDAHandler.close()` → `ConnectionPoolManager.shutdown()`

**数据写入+修复流程：**
1.  `GenericDbWriter.write()` → 获得 `WriteResult`
2.  `ABoxTypeFixer.fix(writeResult, ontologyPath)` → 获得修复后的 `WriteResult`
3.  检查 `result.isPartialFailure()` 决定是否重试或告警

---

## 7. 注意事项

| 类别 | 说明 |
| :--- | :--- |
| **线程安全** | `OBDAHandler` 和 `GenericDbWriter` 是线程安全的，可作为单例使用；`ConnectionPoolManager` 内部同步，但 `getConnection()` 返回的连接非线程安全 |
| **资源释放** | `OBDAResultSet`、`Connection`、`InputStream` 均需在 finally 或 try-with-resources 中显式关闭 |
| **异常体系** | 所有自定义异常继承自 `VkgBaseException`，包含 `errorCode` 字段便于前端国际化处理 |
| **性能建议** | 批量写入时 `batchSize` 建议设为 1000-5000；SPARQL 查询建议设置 LIMIT 防止全表扫描 |
| **版本兼容** | 当前 API 基于 Ontop 5.x + HikariCP 5.x，升级时需验证映射文件格式兼容性 |