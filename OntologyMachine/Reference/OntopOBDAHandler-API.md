# OntopOBDAHandler 模块 API 使用文档

> 模块坐标：`com.ocean:OntopOBDAHandler`
> 包名：`com.ocean.ontopobdahandler`
> 用途：封装 **Ontop 虚拟知识图谱（VKG）** 访问与 **关系数据库读写**，作为 ABox 数据层。

---

## 1. 模块定位与依赖

本模块是「本体 ↔ 数据库」的桥接层，核心职责：

- 通过 Ontop SPARQL Endpoint 执行 `SELECT` / `CONSTRUCT` 查询（虚拟 ABox）；
- 解析 `.obda` 映射文件，得到「属性 IRI → 物理表.列」的映射与 JOIN 键；
- 通过 JDBC（HikariCP 连接池）对物理表做参数化增删改，并支持事务；
- 把 Ontop 返回的 N-Triples 修复为语义正确的 OWL 公理（`ABoxTypeFixer`）。

对外提供 8 个类：

| 类 | 类型 | 职责 |
| --- | --- | --- |
| `OBDAHandler` | 单例类 | 查询 + 读写总入口 |
| `ABoxTypeFixer` | 工具类 | 修复 ABox 数据类型/对象属性断言 |
| `VkgController` | Spring `@RestController` | VKG 的 REST 端点（`/api/vkg`） |
| `ConnectionPoolManager` | 实例类 | HikariCP 连接池封装 |
| `ObdaQueryUtils` | 工具类 | IRI / fragment / label / 变量提取的通用工具 |
| `OntopMappingResolver` | 工具类 | 解析 `.obda`，产出属性→列映射与 JOIN 键 |
| `GenericDbWriter` | 实例类 | 通用参数化 INSERT/UPDATE/DELETE |
| `WriteResult` | 值对象 | 写入结果（accepted + message） |

> ⚠️ **打包注意**：本模块 pom 声明了 `spring-boot-maven-plugin`，普通 `package/install` 会触发 repackage 生成空的（不可依赖的）jar。作为库被其它模块依赖时，构建请加 `-Dspring-boot.repackage.skip=true`。

---

## 2. `OBDAHandler`

**定位**：VKG 单例门面。查询走 Ontop Endpoint（Jena `RDFConnectionRemote`），写入走 `GenericDbWriter`（JDBC）。

### 2.1 单例与初始化（生命周期）

```java
public static synchronized void init(String propsPath, String obdaPath)
public static OBDAHandler getInstance()
public static void shutdown()
```

- `init(propsPath, obdaPath)`：**全生命周期只能调用一次**，重复调用抛 `IllegalStateException("OBDAHandler 已经初始化过了，不允许重复调用 init()")`；参数为 `null`/空白分别抛 `IllegalArgumentException("参数 p 不能为空")` / `("参数 o 不能为空")`。
- `propsPath`：数据库 `.properties`（`jdbc.url`/`jdbc.user`/`jdbc.password`/`pool.maxSize`/`pool.minIdle`，或 `db.*` 别名）；
- `obdaPath`：`.obda` 映射文件（用于解析 SPARQL Endpoint 与映射缓存）。
- `getInstance()`：获取单例；**首次访问会触发 `Holder` 懒加载**，因此 `init(...)` 必须在任何查询/写入之前完成。
- `shutdown()`：关闭 SPARQL 连接与数据库连接池。

**`Holder` 内部类（懒加载，首次访问时执行）** 会：
1. 读取 `.properties`；
2. 从 `.obda` 解析 SPARQL Endpoint（正则 `(?:sparql\.)?endpoint\s*=\s*(.+)`，回退 `sparql.endpoint` / `ontop.sparql.endpoint` / `http://localhost:8080/sparql`）；
3. 通过 `OntopMappingResolver` 预加载 `MAPPING_CACHE`（属性 IRI → `ColumnMapping`）与 `JOIN_KEYS`；
4. 创建 `RDFConnectionRemote` 连接、`ConnectionPoolManager`，并据此构建 `GenericDbWriter`。

Holder 暴露的公开静态字段：`MAPPING_CACHE`、`JOIN_KEYS`、`DB_PROPS`、`SPARQL_ENDPOINT`（供外部读取，如 `OBDAHandler.Holder.MAPPING_CACHE.get(iri)`）。

### 2.2 静态配置与工具

| 方法 | 说明 |
| --- | --- |
| `getObdaPath()` | 读取当前 `.obda` 路径（也可用作「确保 Holder 已初始化」的触发点） |
| `queryConstruct(String sparql)` | 执行 CONSTRUCT，返回 Jena `Model`；异常抛 `RuntimeException("VKG CONSTRUCT 查询异常", e)` |
| `escapeSparqlUri(String)` | 转义 URI（`\` `>` `<` `"`） |
| `loadAboxFromOntop(String constructSparql, OWLOntology tboxOntology)` | 见下 |

`loadAboxFromOntop(...)`：执行 CONSTRUCT → 序列化为 N-Triples → 用 OWL-API 加载 → 调用 `ABoxTypeFixer` 修复数据/对象属性 → 把 ABox 公理并入 `tboxOntology` 并返回该 TBox。CONSTRUCT 结果为空时抛 `IllegalStateException("CONSTRUCT 查询返回空结果...")`。

### 2.3 查询 API（SELECT）

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `executeAboxQuery(String sparql)` | `List<Map<String,String>>` | 通用 SELECT；**资源取 `getLocalName()`** |
| `executeAboxQueryWithIRI(String sparql)` | `List<Map<String,String>>` | 同上，但资源取**完整 URI** |
| `getInstanceProperties(String prefix, String instanceUri)` | `List<Map<String,String>>` | 查询实例所有属性（`SELECT ?property ?value`） |
| `queryWithInference(String prefix, String className, int limit)` | `List<Map<String,String>>` | 查询某类的实例及类型（`a <class>`） |
| `queryAggregation(String prefix, String className, String groupByProp, String aggProp, int limit)` | `List<Map<String,Object>>` | 分组聚合（COUNT + AVG）；类名/属性名仅允许字母数字下划线 |

> 说明：`executeAboxQuery` 返回的「资源」值为 local name，如需完整 IRI 请使用 `executeAboxQueryWithIRI`（源码注释亦提示需自行拼接）。

### 2.4 写入 API（JDBC）

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `addComponent(String table, List<String> columns, List<Object> values)` | `int` | 参数化 INSERT；列/值数量须一致且非空 |
| `addComponentWithConnection(Connection conn, String sql, List<Object> values)` | `void` | 在**已有事务连接**上执行 INSERT（仅供事务内部调用），抛 `SQLException` |
| `updateComponent(String table, List<String> setColumns, List<Object> setValues, String whereCol, Object whereVal)` | `int` | 参数化 UPDATE |
| `deleteComponent(String table, String whereCol, Object whereVal)` | `int` | 参数化 DELETE |
| `executeUpdate(String sql, Object... params)` | `int` | 解析 DML 语句后交由 `GenericDbWriter`（自动事务管理） |
| `executeUpdate(Connection conn, String sql, Object... params)` | `int` | 在**已有事务连接**上执行 DML |
| `executeInTransaction(Consumer<Connection> action)` | `void` | 单连接内执行事务：`setAutoCommit(false)` → action → commit；异常则 rollback |

**`executeUpdate` 的 DML 解析约定**（内部用正则解析 SQL，再走 `GenericDbWriter`）：

- `INSERT INTO <table> (<cols>) VALUES (<placeholders>)`：第一列被视为**主键**；参数个数必须等于列数；
- `UPDATE <table> SET <col>=? ... WHERE <pk>=?`：最后一个参数是 WHERE 值；
- `DELETE FROM <table> WHERE <pk>=?`：恰好 1 个参数。
- 表名/列名经 `sanitize()` 校验（仅 `[A-Za-z_][A-Za-z0-9_]*`），非法抛 `IllegalArgumentException`。

### 2.5 其他

| 方法 | 说明 |
| --- | --- |
| `getAllMappedPropertiesWithVariables()` | 返回 `Map<属性IRI, Set<"table.column">>`（由 `MAPPING_CACHE` 转换，只读） |
| `persistToDatabase(List<Triple> triples)` | **空实现（`// TODO`）**，当前不生效 |

### 2.6 用法示例

```java
// 1. 仅初始化一次
OBDAHandler.init("/path/db.properties", "/path/mapping.obda");
OBDAHandler handler = OBDAHandler.getInstance();

// 2. 查询
List<Map<String,String>> rows = handler.executeAboxQuery(
        "PREFIX : <http://example.org/pizza/components/> " +
        "SELECT ?name WHERE { ?s :name ?name }");

// 3. 事务写入
handler.executeInTransaction(conn -> {
    handler.addComponentWithConnection(conn,
            "INSERT INTO pizza_components (name, price) VALUES (?, ?)",
            List.of("Margherita", 12.5));
});

// 4. 释放
OBDAHandler.shutdown();
```

---

## 3. `ABoxTypeFixer`

**定位**：修正 Ontop 返回的 N-Triples 在 OWL-API 加载后产生的「类型降级」问题。当谓词在 TBox 中已有声明时，OWL-API 可能把 `<S> <P> <O>` 解析成 `AnnotationAssertion`，导致推理器无法识别为数据/对象属性断言。本类据 TBox 声明重建正确公理。

| 方法 | 说明 |
| --- | --- |
| `static void fixDataPropertyTypes(OWLOntology tbox, OWLOntology abox, String rawNTriples)` | 依据 TBox 中声明的 `DataProperty`，把 N-Triples 重建为 `OWLDataPropertyAssertionAxiom`；清理同 S+P 的错误公理 |
| `static void fixObjectPropertyTypes(OWLOntology tbox, OWLOntology abox, String rawNTriples)` | 依据 TBox 中声明的 `ObjectProperty`，把 `<S> <P> <O>` 重建为 `OWLObjectPropertyAssertionAxiom` |

- 两个方法均**原地修改** `abox`（批量 `removeAxioms` + `addAxioms`）。
- 若 TBox 中没有任何对应声明，则记录警告并跳过（不报错）。
- 由 `OBDAHandler.loadAboxFromOntop(...)` 内部自动调用，通常无需直接使用。

---

## 4. `VkgController`

**定位**：Spring MVC 控制器，基路径 `/api/vkg`，持有 `OBDAHandler.getInstance()` 单例。

| HTTP | 路径 | 参数 | 返回 |
| --- | --- | --- | --- |
| GET | `/api/vkg/properties/{prefix}/{instanceUri}` | path: `prefix`, `instanceUri` | `List<Map<String,String>>` |
| GET | `/api/vkg/inference` | query: `prefix`(默认 `http://example.org/pizza/components/`), `className`(默认 `http://example.org/pizza/components/PizzaComponent`), `limit`(默认 20) | `List<Map<String,String>>` |
| GET | `/api/vkg/aggregation` | query: `prefix`, `className`(默认 `PizzaComponent`), `groupByProp`(默认 `supplier`), `aggProp`(默认 `price`), `limit`(默认 0) | `List<Map<String,Object>>` |
| POST | `/api/vkg/component` | body: `{name, supplier, price, type}` | `{affectedRows, message}` |
| PUT | `/api/vkg/component/{name}/price` | path: `name`; query: `price` | `{affectedRows}` |
| DELETE | `/api/vkg/component/{name}` | path: `name` | `{affectedRows}` |

> 该控制器示例性地操作 `pizza_components` 表，主要用于演示/联调。

---

## 5. `ConnectionPoolManager`

**定位**：HikariCP 连接池封装。

```java
public ConnectionPoolManager(String jdbcUrl, String username, String password, int maxPoolSize, int minIdle)
public DataSource getDataSource()
public void shutdown()          // 幂等关闭
```

池名固定为 `OntologyPool`。

---

## 6. `ObdaQueryUtils`

**定位**：与领域无关的 IRI / label / 变量提取工具（`final`，不可实例化）。

| 方法 | 说明 |
| --- | --- |
| `static String fragmentOf(String iri)` | 取 IRI fragment（`#` 或最后一个 `/` 之后）；空返回 `null` |
| `static String toFullIri(String iri, String baseNs)` | 非绝对 IRI 时用 `baseNs` 补全 |
| `static String frag(String iri, String baseNs, String instanceSuffix)` | 提取 fragment 并去掉可选实例后缀；不以 `baseNs` 开头则返回完整 IRI |
| `static Map<String,Set<String>> loadUndirectedRelationFromObda(OBDAHandler handler, String sparql)` | 执行 `SELECT ?a ?b`，构建**无向**关系表（双向填充） |
| `static String queryLabel(OBDAHandler handler, String iri, String baseNs, Logger log)` | 查询 `rdfs:label`，失败/不存在回退 fragment |
| `static List<String> getList(Map<String,Object> vars, String key)` | 从 Camunda 变量 Map 安全提取 `List<String>`（非 List 返回空表） |

---

## 7. `OntopMappingResolver`

**定位**：解析 `.obda` 映射，产出「属性 → 列」映射与 JOIN 键。**纯文本解析，无数据库依赖**，由 `OBDAHandler.Holder` 自动调用。

### 7.1 公共数据结构

```java
public record ColumnMapping(String tableName, String columnName) {}
public record JoinKeyInfo(String subjectVarName, Set<String> tableColumns) {}
```

### 7.2 方法

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `static Map<String,ColumnMapping> resolvePropertyToColumnMappings(String obdaFilePath, Properties props)` | 属性 IRI → 表.列 | throws `Exception` |
| `static List<JoinKeyInfo> resolveJoinKeys(String obdaFilePath, Properties props)` | JOIN 键列表（列数 ≥ 2 的 subject 变量） | throws `Exception` |

内部基于 Ontop `OntopMappingSQLAllConfiguration` 加载 `OBDASpecification`，遍历 `RDFAtomPredicate` 的 property 定义并解析 IQ Tree。

### 7.3 用法示例

```java
Map<String, OntopMappingResolver.ColumnMapping> map =
        OntopMappingResolver.resolvePropertyToColumnMappings(obdaPath, dbProps);
OntopMappingResolver.ColumnMapping cm = map.get("http://example.org/pizza/components/name");
// cm.tableName() = "pizza_components", cm.columnName() = "name"
```

---

## 8. `GenericDbWriter`

**定位**：通用参数化写入器，统一 `INSERT/UPDATE/DELETE` 逻辑，提供**非事务**与**事务感知**两套重载。

| 方法 | 说明 |
| --- | --- |
| `WriteResult insert(String tableName, String primaryKey, Map<String,Object> data)` | 非事务 INSERT（自取连接） |
| `WriteResult update(String tableName, String primaryKey, Object whereValue, Map<String,Object> data)` | 非事务 UPDATE |
| `WriteResult delete(String tableName, String primaryKey, Object whereValue)` | 非事务 DELETE |
| `WriteResult insert(Connection conn, String tableName, String primaryKey, Map<String,Object> data)` | 事务内 INSERT |
| `WriteResult update(Connection conn, String tableName, String primaryKey, Object whereValue, Map<String,Object> data)` | 事务内 UPDATE |
| `WriteResult delete(Connection conn, String tableName, String primaryKey, Object whereValue)` | 事务内 DELETE |

- `data` 为 `Map<列名, 值>`；为空则返回 `WriteResult.rejected("插入/更新数据不能为空")`。
- 非事务版本获取连接失败时返回 `rejected`（不抛异常）。
- 内部 SQL 均使用 `PreparedStatement` + `?` 占位符。

**函数式接口**：

```java
@FunctionalInterface
public interface DbWriteAction {
    void execute() throws Exception;
}
```

用于「先校验再写入」场景，把 DB 操作封装为可延迟执行的回调。

---

## 9. `WriteResult`

**定位**：写入结果值对象（不可变）。

```java
public static WriteResult accepted(String message)   // accepted = true
public static WriteResult rejected(String message)   // accepted = false
public boolean isAccepted()
public String getMessage()
```

---

## 10. 常见注意事项

1. **`OBDAHandler.init` 只能调用一次**；`getInstance()` 首次访问会触发 Holder 懒加载，请确保 `init` 在前。
2. `executeAboxQuery` 默认返回 **local name**，需要完整 IRI 时改用 `executeAboxQueryWithIRI`。
3. `executeUpdate(sql, ...)` 依赖 SQL 文本的正则解析，**请按约定格式书写 DML**（详见 2.4）。
4. 写入表名/列名受 `sanitize()` 白名单约束（仅字母数字下划线）。
5. 事务统一入口为 `executeInTransaction(...)`，其内部捕获异常后 `rollback` 并抛 `RuntimeException("❌ 跨表写入事务失败，已回滚", e)`。
6. `persistToDatabase(...)` 目前是空实现。
