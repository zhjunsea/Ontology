# OpenlletResolver 模块 API 使用文档

> 模块坐标：`com.ocean:OpenlletResolver`
> 包名：`com.ocean.openlletresolver`
> 用途：基于 **OWL-API + Openllet 推理器** 的本体（TBox）与实例（ABox）核心服务层。

---

## 1. 模块定位与依赖

本模块是系统的「本体推理内核」，负责：

- 加载、合并、模块化抽取本体（`OntologyService`）；
- 创建并管理 Openllet 推理器、一致性检查与冲突解释（`ReasonerService`）；
- 对外的统一业务门面：类/个体/属性的推理查询、公理增删、**本体一致性预校验 + 数据库写入**（`BackendService`）；
- 面向 ABox 的通用实例查询（`QueryService`）；
- 本体实例的增/删/改（`InsertService` / `UpdateService` / `DeleteService`）；
- 三元组 → OWL 公理构建（`GenericAxiomBuilder`）、JOIN 键分发（`JoinKeyDistributor`）；
- 通用 OWL 工具（`OntologyModuleUtils`）、SKOS 同义词（`SkosSynonymReader`）、Openllet 调优（`OpenlletTuning`）、迷你推理上下文（`MiniReasoningContextManager` / `MiniContext`）、SWRL 触发监听（`SwrlRuleTriggerListener`）。

**依赖方向**：`OpenlletResolver` **依赖** `OntopOBDAHandler`（`BackendService` 持有 `OBDAHandler`；`OntologyService`、`Insert/Update/DeleteService`、`GenericAxiomBuilder` 均引用其类型）。`Utilities` 又反向依赖本模块。

### 类总览

| 类 | 定位 |
| --- | --- |
| `OntologyService` | 本体加载/合并/工具，`AutoCloseable` |
| `ReasonerService` | 推理器创建、预计算、一致性解释、池化上下文 |
| `BackendService` | 全局单例门面，类/个体查询 + 安全写入 |
| `QueryService` | 通用实例查询（合并 TBox+ABox 推理） |
| `GenericAxiomBuilder` | 三元组 → OWL 公理 |
| `InsertService` / `UpdateService` / `DeleteService` | 实例增/改/删 |
| `JoinKeyDistributor` | 跨表 JOIN 键分发 |
| `OntologyModuleUtils` | 通用 OWL 操作工具集 |
| `SkosSynonymReader` | SKOS 同义词读取 |
| `OpenlletTuning` | Openllet 全局调优 |
| `MiniReasoningContextManager` / `MiniContext` | 迷你本体推理上下文与缓存 |
| `SwrlRuleTriggerListener` | SWRL 推导结果监听 |

---

## 2. `OntologyService`

**定位**：本体加载与合并服务。构造时加载主本体、注入前缀、统计 SWRL 规则。

### 2.1 构造与字段访问

```java
public OntologyService(String mainOntologyPath) throws Exception
```

加载流程（构造内）：创建 `OWLOntologyManager` → 加载主本体（扫描同目录 `.owl/.rdf/.xml/.omn/.ofn` 并注册 `.ttl` 映射）→ 取 `DataFactory` → `getPrefixSpaceAndInjectToOntology(...)`（用 Jena 提取前缀并去重，合并所有已加载本体到一个 total ontology）→ `swrlCheck()`。

| Getter | 说明 |
| --- | --- |
| `getaBoxOntology()` | ABox |
| `gettBoxOntology()` | TBox（合并后的总本体） |
| `getMergedOntology()` | 合并本体 |
| `getDataFactory()` | `OWLDataFactory` |
| `getManager()` | `OWLOntologyManager` |

### 2.2 加载与合并

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `mergeInMemory(OWLOntology tbox, OWLOntology abox)` | `OWLOntology` | 内存合并；会重建 manager 并清除旧 merged 本体 |

### 2.3 工具方法

| 方法 | 说明 |
| --- | --- |
| `buildValuesClause(String variableName, Collection<String> iris)` | 生成安全 `VALUES` 子句；集合为空返回 `VALUES ?var { UNDEF }` |
| `static boolean validateSpecificTypeAxiom(Set<OWLAxiom> tempAxioms, OWLOntology tbox)` | 校验所有 `ClassAssertion`：个体非匿名、类为命名类且在 TBox 中定义；返回是否全部合法 |
| `boolean checkIsObjectProperty(String propertyIri)` | 该 IRI 是否为 ObjectProperty（签名查找） |
| `static String getLocalName(String iriString)` | 取 IRI 的短名 |
| `close()` | 关闭 ABox 服务（`aBoxService.shutdown()`） |

---

## 3. `ReasonerService`

**定位**：围绕 Openllet 推理器的创建、预计算与一致性检查。

### 3.1 构造

```java
public ReasonerService(OntologyService ontologySrv)
```

- 用 `OpenlletReasonerFactory` 对 TBox 建推理器并 `flush()`；
- `precomputeInferences(...)` 预计算类层级、属性层级、断言等；
- 若不一致 → `ExplainInconsistencyWithOWLExplanation(...)`（会抛 `InconsistentOntologyException`）；
- `initReasonerPool(...)`（当前 `POOL_SIZE=0`，池化默认关闭）。

### 3.2 主要方法

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `getReasoner()` / `setReasoner(OWLReasoner)` | `OWLReasoner` | 取/换当前推理器 |
| `getFactory()` | `OWLReasonerFactory` | 推理器工厂 |
| `ExplainInconsistencyWithOWLExplanation(OntologyService)` | `void` | 生成冲突解释；**末尾抛 `InconsistentOntologyException`** |
| `ExplainInconsistencyWithBlackBoxExplanation(OWLOntology)` | `void` | 先用 `findSyntaxLevelViolations` 做语法级诊断，再用 BlackBox 提取不一致公理 |
| `getSuperClassesIncludingSelf(String classIRI)` | `Set<OWLClass>` | 含自身的父类闭包（只读、不可变；异常/无推理器返回空集） |
| `borrowContext(long timeoutMs)` | `PooledReasonerContext` | 从池借上下文；超时抛 `IllegalStateException` |
| `returnContext(PooledReasonerContext)` | `void` | 归还（清理会话公理） |

### 3.3 `PooledReasonerContext`（静态内部类，`AutoCloseable`）

```java
public final OWLOntologyManager manager;
public final OWLOntology ontology;
public final OWLReasoner reasoner;

public void addAxioms(Set<OWLAxiom> axioms)
public void flush()
public OWLDataFactory getDataFactory()
public void close()
```

---

## 4. `BackendService`

**定位**：全局单例门面，聚合 `OntologyService` + `ReasonerService` + `OBDAHandler`，是应用层最常用的入口。实现 `AutoCloseable`。

### 4.1 单例与生命周期

```java
public static BackendService getInstance(String mainOntologyPath, OBDAHandler obdaHandler) throws Exception
public static BackendService getInstance(String mainOntologyPath) throws Exception
public static BackendService getInstance()                                  // 未初始化时抛 IllegalStateException
public static void setInstance(BackendService instance)
public void close()                                                        // dispose 推理器
```

- 两个带参重载：双重检查锁单例；无参重载在未初始化时抛：
  `IllegalStateException("BackendService 尚未初始化，请先调用 getInstance(...) ...")`。
- 构造：加载 TBox（`new OntologyService(...)`）→ 绑定 ABox（传入的 `obdaHandler` 或 `OBDAHandler.getInstance()`）→ `new ReasonerService(ontologyService)`。

**Getter**：`getOntologyService()`、`getReasonerService()`、`getObdaHandler()`、`getDataFactory()`、`getTBoxOntology()`、`getABoxOntology()`、`getManager()`（后四个为便捷代理，等价于 `getOntologyService().getXxx()`）。

**公共嵌套类型**：

```java
public record objectPair(String objectName, String columnName) {}
public static class PatientContext { ... }   // 见 4.7
public record OntologyConstraint(String hostClass, String property, String kind,
                                 double lower, double upper, boolean exclusive, String raw) {
    public boolean accepts(double v) { ... }
}   // 见 4.9
```

### 4.2 类相关查询

| 方法 | 返回 |
| --- | --- |
| `getClass(String classIRIOrQName)` | `OWLClass`（未找到抛 `IllegalArgumentException`） |
| `getSubClassIris(String classIri)` | `Set<String>` |
| `getSuperClasses(String classIRI)` / `getSuperClasses(OWLClass)` | `Set<OWLClass>`（过滤 `owl:Thing`） |
| `getSubClasses(String classIRI)` | `Set<OWLClass>`（过滤 `owl:Nothing`） |
| `getIndividuals(String classIRI)` | `Set<OWLNamedIndividual>`（显式断言 + 推理实例） |
| `readTypeFragments(OWLNamedIndividual ind, boolean direct)` | `Set<String>`（推断类型 fragment 短名；`ind=null` 返回空集） |
| `getAllObjectPropertiesOfClass(OWLClass)` | `Set<OWLObjectPropertyExpression>` |
| `getObjectPropertyOfClass(OWLClass, String propIRI)` | `OWLObjectPropertyExpression`（可空） |
| `getObjectPropertyDomain/ Range(OWLObjectPropertyExpression)` | `Set<OWLClassExpression>` |
| `getObjectPropertyDomains(String)` / `getObjectPropertyRanges(String)` | `Set<OWLClass>` |
| `getObjectPropertyLimitations(OWLClass, OWLObjectPropertyExpression)` | `Set<OWLClassExpression>` |
| `getInverseProperty(String propIRI)` | `Optional<OWLObjectPropertyExpression>` |
| `getAllNamedSubclasses(IRI parentIri)` | `Set<OWLClass>`（递归，BFS + 预构建索引） |
| `getDirectNamedSubclasses(IRI topClassIri)` | `Set<OWLClass>`（仅直接子类） |
| `findMostSpecificClass(Set<OWLClass>, OWLOntology)` | `String`（最具体类 IRI，可空） |

### 4.3 个体相关查询

| 方法 | 返回 |
| --- | --- |
| `getIndividual(String individualIRI)` | `OWLNamedIndividual`（不存在抛 `IllegalArgumentException`） |
| `getIndividualDirectTypes(OWLNamedIndividual)` | `Set<OWLClass>` |
| `getIndividualAllTypes(OWLNamedIndividual)` | `Set<OWLClass>`（直接 + 父类闭包） |
| `isInstanceOf(String individualIRI, String classIRI)` | `boolean` |
| `getObjectPropertiesOfIndividual(OWLNamedIndividual)` | `Set<OWLObjectPropertyExpression>` |
| `getObjectPropertyDirectValueOfIndividual(OWLNamedIndividual, OWLObjectPropertyExpression)` | `Set<OWLNamedIndividual>` |
| `getObjectPropertyAllValueOfIndividual(OWLNamedIndividual, OWLObjectPropertyExpression)` | `Set<OWLNamedIndividual>`（推理） |
| `getDirectDataPropertiesOfIndividual(OWLNamedIndividual)` | `Set<OWLDataProperty>` |
| `getAllAllowedDataPropertiesOfIndividual(OWLNamedIndividual)` | `Set<OWLDataProperty>`（域匹配） |
| `getDataPropertyValueOfIndividual(OWLNamedIndividual, OWLDataProperty)` / `(..., String dataPropIRI)` | `Set<OWLLiteral>` |
| `queryPropertyAxiom(String typeNS, String indNS, String individualName, String propertyIri)` | `Set<OWLAxiom>`（经 CONSTRUCT 查询属性+type 三元组后构建；空返回 `null`） |
| `filterRealIndividuals(Set<OWLNamedIndividual>, OWLOntology)` | `static Set<OWLNamedIndividual>`（过滤 SKOS/元建模/内置命名空间） |

### 4.4 属性/实体辅助

| 方法 | 返回 |
| --- | --- |
| `getObjectProperty(String iri)` / `getDataProperty(String iri)` | `OWLObjectProperty` / `OWLDataProperty`（未找到抛 `IllegalArgumentException`） |
| `getDatatype(String datatypeIRI)` | `Optional<OWLDatatype>` |
| `getEntityType(IRI iri)` | `String`（`Class`/`Individual`/`ObjectProperty`/`DataProperty`/`AnnotationProperty`/`Datatype`/`Unknown`） |
| `getDataPropertyDomains(String)` / `(OWLDataProperty)` | `Set<OWLClass>` |
| `getDataPropertyRanges(String propIRI)` | `Set<OWLDatatype>` |
| `getPropertyFillerFromClass(OWLClass, String objectPropertyIri)` | `Set<String>`（someValuesFrom / exactCardinality / hasValue 的 filler） |
| `getDataPropertyAssertions(OWLNamedIndividual, OWLDataProperty)` | `Set<OWLDataPropertyAssertionAxiom>` |
| `parseNumeric(OWLLiteral)` | `Number`（解析失败 `null`） |

### 4.5 注解与标签

| 方法 | 返回 |
| --- | --- |
| `getAnnotations(OWLObject entity)` | `Map<OWLAnnotationProperty, Set<OWLLiteral>>` |
| `getAnnotationValue(OWLEntity, String annotationPropertyIRI)` | `Set<OWLLiteral>` |
| `getLabel(OWLOntology, IRI, String lang)` | `String`（指定语言优先 → 无语言 → 任意） |
| `resolveLabel(String iri)` | `String`（中文标签，回退短名） |
| `resolveLabel(String iriOrFragment, String baseNamespace)` | `String`（fragment 自动补全） |
| `resolveLabels(List<String>, String baseNamespace)` | `List<String>` |

### 4.6 公理增删与安全写入

| 方法 | 说明 |
| --- | --- |
| `addAxiom(OWLAxiom)` / `removeAxiom(OWLAxiom)` | 单条增删 + `flush()` |
| `addAxioms(OWLOntology, Set<OWLAxiom>)` / `removeAxioms(OWLOntology, Set<OWLAxiom>)` | 批量增删 + `flush()` |
| `removeAxiomSet(Set<? extends OWLAxiom>)` | 对 TBox 批量移除 |
| `addIndividualAxiom(OWLNamedIndividual, OWLDataProperty, OWLLiteral)` / `(..., int)` / `(..., String)` | 便利：加数据属性断言 |
| `addIndividualAxiom(OWLNamedIndividual, OWLObjectProperty, OWLNamedIndividual)` | 便利：加对象属性断言 |
| `validateAxioms(Set<OWLAxiom> tempAxioms)` | **临时注入 → 一致性检查 → 立即移除**，返回是否一致（`writeLock` 保护） |
| `withTempAxioms(Set<OWLAxiom> tempAxioms, Function<OWLReasoner,T> work)` | 临时注入公理 → flush → 执行 `work` → finally 清理临时公理并 flush；全程持写锁；`tempAxioms` 为 null/空时仍执行 `work` |
| `safeVerifyAndDBExecution(Set<OWLAxiom> tempAxioms, Consumer<Connection> dbWriteAction)` | 见下 |
| `safeVerifyAndDBExecution(Set<OWLAxiom> tempAxioms, String typeIRI, Consumer<Connection> dbWriteAction)` | 见下 |

**`safeVerifyAndDBExecution` 执行契约**（核心安全写入引擎）：

1. 先 `validateSpecificTypeAxiom(...)` 校验 `rdf:type`，不通过抛 `IllegalArgumentException`；
2. `[Step1]` 基线一致性检查（不一致抛 `IllegalStateException`）；
3. `[Step2]` 注入临时公理并 `flush()`；
4. `[Step3]` 一致性预校验，矛盾则拦截（抛 `IllegalStateException`，并输出 BlackBox 解释）；
5. `[Step4]` 通过 `OBDAHandler.getInstance().executeInTransaction(dbWriteAction)` 在**单事务**内持久化；
6. `[Step5]` finally 中统一移除临时公理，恢复基线。

> `dbWriteAction` 为 `null` 时仅做校验、不写库。

### 4.7 扩展与诊断

| 方法 | 说明 |
| --- | --- |
| `printOWLClassSet(Set<OWLClass>)` | 打印类集合 |

**`PatientContext`（静态内部类）**：

```java
public final OWLOntologyManager manager;
public final OWLDataFactory df;
public final OWLOntology ontology;
public final OWLReasoner reasoner;

public PatientContext(...)
public void dispose()   // dispose reasoner
```

### 4.9 通用 OWL 本体解析工具（静态方法）

**定位**：与业务无关的本体约束/字面量解析工具集，全部为 `public static`。

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `parseOntologyVersion(OWLOntology ont, OWLDataFactory df)` | `String` | 从 `owl:versionInfo` 注解取版本号；未找到返回 `"unknown"` |
| `parseHasKeys(OWLOntology ont)` | `List<String>` | 解析 `HasKey` 公理，返回 `"类名: [属性列表]"` |
| `parseDataRange(String host, OWLDataAllValuesFrom avf)` | `OntologyConstraint` | 解析 `DataAllValuesFrom` 中的 `DatatypeRestriction` facet（`minInclusive`/`minExclusive`/`maxInclusive`/`maxExclusive`），生成 `OntologyConstraint`；非 `DatatypeRestriction` 返回 `null` |
| `describeCardinality(String host, OWLObjectCardinalityRestriction card)` | `String` | 人类可读的基数约束描述（如 `"Host ⊧ >= 2 hasPart"`） |
| `hostOf(String prop, OWLOntology ont)` | `String` | 在 `SubClassOf` 公理中找 `DataHasValue` 的宿主类 fragment |
| `isNumericLiteral(OWLLiteral lit)` | `boolean` | 是否为数值型字面量（`integer`/`int`/`decimal`/`double`/`float`） |
| `literalToDouble(OWLLiteral lit)` | `double` | 字面量转 `double`（兼容整数与浮点） |
| `localName(IRI iri)` | `String` | IRI 的短名（`getRemainder`，无则返回完整 IRI） |
| `trimDouble(double v)` | `String` | 整数去 `.0`（如 `1400.0` → `"1400"`） |
| `stringAssertion(OWLOntology, OWLNamedIndividual, String ns, String propLocal)` | `String` | 取个体在某数据属性上的字符串值；未找到返回 `null` |
| `numberAssertion(OWLOntology, OWLNamedIndividual, String ns, String propLocal)` | `Double` | 取个体在某数据属性上的数值；未找到返回 `null` |

**`OntologyConstraint` record**：

```java
public record OntologyConstraint(String hostClass, String property, String kind,
                                 double lower, double upper, boolean exclusive, String raw)
```

- `kind`：`"HAS_VALUE"` / `"RANGE"` / `"GT"`
- `accepts(double v)`：判断数值是否满足约束（`HAS_VALUE` 精确匹配；`RANGE`/`GT` 检查上下界）
- `toString()`：形如 `"Host ⊑ ∃prop [raw]"`

### 4.8 用法示例

```java
// 初始化（一次）
BackendService backend = BackendService.getInstance("/path/pizza.owl");

// 查询
Set<OWLNamedIndividual> inds = backend.getIndividuals("http://example.org/pizza/components/PizzaComponent");

// 安全写入：先本体一致性校验，再事务写库
Set<OWLAxiom> tempAxioms = new GenericAxiomBuilder(backend, typeNS, indNS).buildAxioms(triples);
backend.safeVerifyAndDBExecution(tempAxioms, conn -> {
    // 使用 conn 执行 INSERT/UPDATE/DELETE
});
```

---

## 5. `QueryService`

**定位**：通用的「TBox+ABox 合并推理」实例查询服务，不绑定业务领域。

### 5.1 公共数据结构

```java
public record IndividualRecord(
        String individualIri,
        String localName,
        List<String> inferredTypes,
        Map<String, String> dataProperties) {}
```

`QueryConfig`（Builder 模式）：

```java
QueryConfig.builder()                    // 无参，供反射映射使用
QueryConfig.builder(String rootClassIri) // 带参，兼容旧调用
        .rootClassIri(String)
        .dataProperties(List<String>) / .dataProperties(String...)
        .maxResults(int)                 // 默认 -1（不限）
        .includeDirectType(boolean)      // 默认 false
        .build()                         // rootClassIri 为空时抛 NPE
// getter: getRootClassIri() / getDataPropertyIris() / getMaxResults() / isIncludeDirectType()
```

### 5.2 方法

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `QueryService(BackendService backendService)` | — | 构造 |
| `queryInstances(OWLOntology tbox, OWLOntology abox, QueryConfig config)` | `List<IndividualRecord>` | 核心查询：合并本体 → 建 Openllet 推理器 → 取实例/类型/数据属性 |
| `queryInstances(OWLOntology tbox, OWLOntology abox, String rootClassIri, int maxResults)` | `List<IndividualRecord>` | 便捷重载 |
| `queryPropertyValueInDB(String ns, String individualIri, String propertyIri)` | `List<String>` | 经 Ontop Endpoint 查属性值（多值） |
| `queryPropertyValueInOntology(String individualIri, String propertyIri)` | `List<String>` | 走推理器（DataProperty 优先，回退 ObjectProperty） |
| `getBestMatchedType(String sourceClassIri, String objectPropertyIri)` | `Set<String>` | 沿类层级 BFS 向上找对象属性 range；未找到返回 `Set.of()` |

> `queryPropertyValueInDB` / `queryPropertyValueInOntology` 的 `individualIri` / `propertyIri` 为 `null` 时抛 `NullPointerException`。

---

## 6. `GenericAxiomBuilder`

**定位**：三元组 → OWL 公理构建器，完全由 `(subject, predicate, object)` 驱动。

```java
public GenericAxiomBuilder(BackendService backendService, String typeNS, String indNS)
public record Triple(String subject, String predicate, String object, boolean isObjectProperty) {}
```

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `buildAxioms(List<Triple> triples)` | `Set<OWLAxiom>` | `subject/object` 按 `indNS`、`typeNS` resolve；自动识别 `rdf:type` / 对象属性 / 数据属性 |
| `buildAxioms(String subject, Map<String,String> allProperties)` | `Set<OWLAxiom>` | 由属性 Map 构建；键识别 `rdf:type` / `:type` / `type` 为类断言；按 TBox 签名判定对象/数据属性 |

**字面量类型推断**（`inferLiteral`，优先级从高到低）：显式 `^^datatype` > TBox `Range` > 格式推断（int → `Decimal` → `Boolean`）> `xsd:string`。

---

## 7. `InsertService` / `UpdateService` / `DeleteService`

三者对称设计：**先用 Reasoner 校验语义，再委托 OBDAHandler 执行物理写库**，均以 `BackendService` 构造。

```java
public InsertService(BackendService backendService)
public UpdateService(BackendService backendService)
public DeleteService(BackendService backendService)   // 三者均 Objects.requireNonNull(backendService)
```

### 7.1 `InsertService`

| 方法 | 说明 |
| --- | --- |
| `insertComponentAutoSplit(Map<String,String> propertyValues, Set<OWLAxiom> tempAxioms)` | 跨表自动拆分写入（严格事务）。按 `OBDAHandler.Holder.MAPPING_CACHE` 把属性路由到物理表；`JoinKeyDistributor` 填充 JOIN 键；**必须包含合法 `rdf:type`**；经 `safeVerifyAndDBExecution` 单事务写入 |

异常：`propertyValues` 空 → `IllegalArgumentException`；存在无映射属性或写入表为空 → `IllegalStateException`。

### 7.2 `UpdateService`

| 方法 | 说明 |
| --- | --- |
| `updateComponentAutoSplit(Map<String,String> identifierValues, Map<String,String> propertyValues)` | 跨表自动拆分更新（严格事务）。`identifierValues` 为 WHERE 条件，`propertyValues` 为 SET 数据；JOIN 键经 `JoinKeyDistributor` 填充 |

异常：`propertyValues` / `identifierValues` 空 → `IllegalArgumentException`；存在无映射属性/标识符 → `IllegalStateException`。

### 7.3 `DeleteService`

| 方法 | 说明 |
| --- | --- |
| `deleteComponentAutoSplit(Map<String,String> identifierValues)` | 跨表自动拆分删除（严格事务）。所有 IRI 必须有有效映射，否则中止；JOIN 键自动分发；单事务逐表 DELETE |

### 7.4 用法示例

```java
Map<String, String> props = new LinkedHashMap<>();
props.put("http://example.org/pizza/components/name", "Margherita");
props.put("http://example.org/pizza/components/price", "12.5");
new InsertService(backend).insertComponentAutoSplit(props, tempAxioms);
```

---

## 8. `JoinKeyDistributor`

**定位**：封装 Insert/Update/Delete 中相同的 JOIN 键解析、去重、反向查找与填充逻辑（`final`，不可实例化）。

```java
public record DistributionResult(int fillCount, int deduplicatedConfigCount) {}
```

| 方法 | 说明 |
| --- | --- |
| `static DistributionResult distribute(List<JoinKeyInfo> joinKeys, Map<String,ColumnMapping> mappingCache, Map<String,String> identifierValues, Map<String,Map<String,String>> tableDataMap, Map<String,Map<String,String>> tableIdentifierMap, String operationName)` | 执行分发；JOIN 键值缺失时抛 `IllegalStateException`。INSERT 传 `identifierValues=null`；UPDATE/DELETE 传 `tableIdentifierMap` |
| `static void validateCompleteness(Map<String,Map<String,String>> tableDataMap, Map<String,Map<String,String>> tableIdentifierMap, String operationName)` | 前置完整性校验：INSERT 校验 `tableDataMap` 非空；UPDATE/DELETE 校验每表有 WHERE 条件 |

---

## 9. `OntologyModuleUtils`

**定位**：与业务无关的 OWL 操作工具集（`final`，不可实例化），全部为静态方法。

| 方法 | 说明 |
| --- | --- |
| `collectRestrictionFillers(OWLOntology tbox, OWLClass cls, Set<IRI> propIris)` | 收集等价/子类公理中经指定属性 `someValuesFrom` 出现的 filler fragment |
| `buildIntersectionCompositeMap(OWLOntology tbox, IRI topClassIri, Set<OWLClass> allSubs)` | 扫描「复合类 = 原子类交集」，返回 `Map<复合类, 原子类集合>` |
| `findRelatedClasses(OWLOntology tbox, OWLClass cls, Set<OWLClass> targetUniverse)` | 找与类相关（注解/父类/等价类命中）且属于目标全域的类 |
| `collectClassClosure(OWLOntology tbox, Set<OWLClass> initial)` | 沿等价/子类递归收集类闭包（排除 `Thing/Nothing`） |
| `collectIndividualTypes(OWLOntology tbox, Collection<String> individualIris, String baseNs)` | 由个体 IRI 读类断言 |
| `extractTBoxModule(OWLOntology tbox, Set<OWLClass> keepClasses)` | 抽取 TBox 模块，过滤 ABox 与个体相关公理 |
| `addObjectAssertionsAndCopyTypes(OWLOntology tbox, OWLDataFactory df, Set<OWLAxiom> acc, OWLObjectProperty prop, OWLNamedIndividual subj, List<String> objects, String baseNs)` | 加对象断言并复制目标个体的类型 |
| `addSynthesizedComposites(OWLDataFactory df, Set<OWLAxiom> acc, OWLNamedIndividual patient, List<String> providedIris, OWLObjectProperty prop, Map<OWLClass,Set<OWLClass>> compositeMap, String baseNs, String instanceSuffix, Logger log)` | 合成复合个体（患者具备全部原子成分时） |
| `extractFragmentsByMetaClass(Set<OWLClass> allTypes, Set<OWLClass> metaClassSet)` | 过滤属于元类集合的类，返回其 fragment（排序后） |

---

## 10. `SkosSynonymReader`

**定位**：复用 `BackendService` 已加载的 TBox 读取 SKOS 同义词（不自行读文件、不依赖 Jena）。全部静态方法，`throws Exception`。

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `getTBox()` | `OWLOntology` | 取 TBox |
| `getAllLabels(String conceptUri)` | `Map<String,List<String>>` | 按 `prefLabel`/`altLabel`/`hiddenLabel` 分组的中文标签 |
| `getAllSynonyms(String conceptUri)` | `List<String>` | 扁平化的全部中文同义词（去重） |
| `getSynonymsByOwlIndividual(String owlIndividualUri)` | `List<String>` | 经 `skos:exactMatch` 反向找 Concept 的同义词 |
| `buildSynonymDictionary()` | `Map<String,String>` | 「任意中文词 → 规范词」词典（**建议上层缓存**） |
| `findConceptIRIByPrefLabel(String prefLabel)` | `String` | 中文 `prefLabel` 反查 Concept IRI；未找到返回 `null` |

---

## 11. `OpenlletTuning`

**定位**：Openllet 推理器**全局**调优配置点（`final`）。关闭 CD 优化分类器（`USE_CD_CLASSIFICATION`）与高级缓存（`USE_ADVANCED_CACHING`），消除含大量 `equivalentClass` 本体上的非确定性耗时。

**必须在任何推理器创建之前调用一次**。

| 方法 | 说明 |
| --- | --- |
| `static void apply(Properties appOverrides)` | 幂等应用；配置优先级：内置默认 < classpath `openllet-tuning.properties` < 应用参数 < 系统属性 `-Dopenllet.tuning.*` |
| `static void apply()` | 仅用默认 + 配置文件 |

### 用法示例

```java
OpenlletTuning.apply();   // 在创建 OntologyService / ReasonerService 之前
```

---

## 12. `MiniReasoningContextManager` 与 `MiniContext`

**定位**：按 `cacheKey` 缓存「TBox 公理集合」与「迷你推理上下文 `MiniContext`」，避免重复构建/推理。异常时自动 `dispose`。与业务无关。

### 12.1 `MiniReasoningContextManager`

| 方法 | 说明 |
| --- | --- |
| `<T> T withContext(String cacheKey, Supplier<Set<OWLAxiom>> tboxSupplier, Function<OWLDataFactory,Set<OWLAxiom>> aboxBuilder, Function<MiniContext,T> action)` | 取/建上下文并对之执行 action；action 抛 `RuntimeException` 时移除并 dispose 该上下文 |
| `clearByPrefix(String prefix)` | 清空指定前缀缓存并 dispose |

### 12.2 `MiniContext`

封装临时 `OWLOntologyManager` / `OWLOntology` / `OWLReasoner`，并按个体 IRI 缓存类型推断。

| 方法 | 返回 |
| --- | --- |
| `MiniContext(OWLOntologyManager, OWLOntology, OWLDataFactory, OWLReasoner)` | — |
| `getTypes(String individualIri)` | `Set<OWLClass>`（缓存） |
| `isConsistent()` | `boolean` |
| `getManager()` / `getDataFactory()` / `getReasoner()` | 各对象 |
| `dispose()` | 释放推理器并移除本体 |

---

## 13. `SwrlRuleTriggerListener<T>`

**定位**：通用 SWRL 规则推导结果监听器。**必须与 `BackendService` 共享同一个 `OWLOntologyManager`** 才能收到变更事件。

```java
public static class Config<T> {
    public Config(String targetClassIri, Consumer<T> onTriggered, Class<T> callbackParamType)
    // 字段: targetClassIri, onTriggered, watchABoxOnly(默认 true), threadPoolSize(默认 4), callbackParamType
}
public SwrlRuleTriggerListener(Config<T> config, BackendService backendService)
```

| 方法 | 说明 |
| --- | --- |
| `start()` | 非阻塞启动：注册本体变更监听；重复启动被忽略 |
| `shutdown()` | 停止：关闭异步线程池；**不 dispose 全局 Reasoner**（仅移除监听） |

监听逻辑：收到 `AddAxiom`/`RemoveAxiom` 变更 → `flush()` → 查询目标类实例 → 对新触发实例异步回调 `onTriggered`。

---

## 14. 典型协作流程

```text
OpenlletTuning.apply()                       // 1. 全局调优（可选，须最先）
        │
OntologyService(mainPath)                    // 2. 加载 TBox
        │
ReasonerService(ontologyService)             // 3. 建推理器/预计算
        │
BackendService.getInstance(mainPath)         // 4. 全局门面（内部已含 2、3）
        │
        ├── QueryService(backend)            // 5a. 通用实例查询
        ├── Insert/Update/DeleteService      // 5b. 实例增删改（自动拆分 + 安全写库）
        └── GenericAxiomBuilder(backend,..)  // 5c. 三元组 → 公理
                    │
        backend.safeVerifyAndDBExecution(tempAxioms, conn -> {...})   // 6. 校验 + 事务写库
```

## 15. 常见注意事项

1. **单例约束**：`BackendService` 与 `OBDAHandler` 均为进程内单例，初始化须一次且最先完成。
2. **调用顺序**：`OpenlletTuning.apply()` 必须早于任何推理器创建；`BackendService` 会隐式创建 `OntologyService` 与 `ReasonerService`。
3. **异常语义**：`BackendService.getInstance()`（无参）在未初始化时抛 `IllegalStateException`；`getIndividual` / `getClass` 等未找到抛 `IllegalArgumentException`。
4. **一致性校验**：`safeVerifyAndDBExecution` 会临时污染 TBox 再清理；写入前务必确认 `rdf:type` 合法。
5. **写入入口**：优先使用 `Insert/Update/DeleteService` 或 `BackendService.safeVerifyAndDBExecution`。
6. **资源释放**：`BackendService`、`OntologyService`、`MiniContext` 均为 `AutoCloseable`，用完应 `close()`/`dispose()`。
7. **`ExplainInconsistencyWithOWLExplanation` 末尾会抛 `InconsistentOntologyException`**，调用方需注意其「既解释又抛错」的副作用。
