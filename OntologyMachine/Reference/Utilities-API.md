# Utilities 模块 API 使用文档

> 模块坐标：`com.ocean:Utilities`
> 包名：`com.ocean.utilities`
> 用途：沉淀 **与业务无关的通用支撑能力**，供 `OntologyFramework` / `OntologyFrameworkExample` 等应用复用。

---

## 1. 模块定位与依赖

`Utilities` 是公共能力模块，不包含任何业务语义。它对外提供 9 个类：

| 类 | 类型 | 职责 |
| --- | --- | --- |
| `OntologyWorkerSupport` | 抽象类 | JobWorker 的「本体推理链路」初始化骨架（模板方法） |
| `ConfigFileLocator` | 工具类（不可实例化） | 定位 `application.yaml` |
| `YamlConfigUpdater` | 工具类（不可实例化） | 保留注释/排版地对 YAML 做外科式赋值 |
| `OntologyLabelMatcher` | 工具类（不可实例化） | 基于类型闭包匹配 `rdfs:label` 候选词 |
| `RabbitMqHandler` | 实例类 | RabbitMQ 发送封装（自动 JSON 序列化） |
| `StopOnTimeoutExtension` | JUnit 5 扩展 | 任一测试超时即中止整个测试套件 |
| `ArchiveSupport` | 工具类（不可实例化） | 目录归档：把一组文件按相对路径打包为 zip |
| `FileTransferSupport` | 工具类（不可实例化） | 文件上传/传输：`MultipartFile` 存盘、文件名安全清洗、Content-Disposition 头 |
| `ProcessOrchestrator` | 工具类（不可实例化） | Camunda 8 流程编排：启流程实例、读状态/变量、幂等部署 |

**依赖方向（重要）**：`Utilities` **反向依赖** `OpenlletResolver` 与 `OntopOBDAHandler`：

- `OntologyWorkerSupport` 依赖 `com.ocean.openlletresolver.BackendService`、`com.ocean.openlletresolver.QueryService`
- `OntologyLabelMatcher` 依赖 `com.ocean.openlletresolver.BackendService`

因此 `Utilities` 的 pom 需声明对 `OpenlletResolver` 的依赖，且 `OpenlletResolver`、`OntopOBDAHandler` 必须能先构建通过。

---

## 2. `OntologyWorkerSupport`

**定位**：把各 JobWorker 中重复的「本体推理链路初始化顺序」沉淀为模板方法，子类只需实现「如何创建 `BackendService`」。

### 2.1 受保护字段

```java
protected BackendService backendService;   // 初始化后可用
protected QueryService   queryService;     // 由 backendService 派生
```

### 2.2 方法

| 方法 | 可见性 | 说明 |
| --- | --- | --- |
| `initOntologyPipeline()` | `protected final` | 模板方法，子类应在 `@PostConstruct` 中调用 |
| `applyOpenlletTuning()` | `protected` | 应用 Openllet 库级调优，默认空实现，按需覆盖 |
| `createBackendService()` | `protected abstract` | **必须实现**：用自身配置创建 `BackendService` |
| `afterBackendServiceReady()` | `protected` | `BackendService` / `QueryService` 就绪后的扩展点，默认空实现 |

`initOntologyPipeline()` 的执行顺序：

1. `applyOpenlletTuning()`
2. `backendService = createBackendService();`
   若返回 `null` → 抛 `IllegalStateException("BackendService 初始化失败，请检查本体路径和 OBDA 连接")`
3. `queryService = new QueryService(backendService);`
4. `afterBackendServiceReady();`

### 2.3 用法示例

```java
@Component
public class MyJobWorker extends OntologyWorkerSupport {

    @Value("${ontology.main-path}") String ontologyPath;

    @PostConstruct
    void init() throws Exception {
        initOntologyPipeline();   // 完成 backendService / queryService 初始化
    }

    @Override
    protected void applyOpenlletTuning() {
        com.ocean.openlletresolver.OpenlletTuning.apply();  // 可选
    }

    @Override
    protected BackendService createBackendService() throws Exception {
        return BackendService.getInstance(ontologyPath);
    }

    @JobWorker(type = "my-task")
    public void run() { /* 直接使用 backendService / queryService */ }
}
```

> 注意：`initOntologyPipeline()` 抛出的异常类型为 `Exception`，调用处需处理；`createBackendService()` 若返回 `null` 会主动抛出运行时异常。

---

## 3. `ConfigFileLocator`

**定位**：通用 `application.yaml` 定位器（`final` 工具类，私有构造，不可实例化）。

### 3.1 方法

```java
public static Path resolve(String override)
```

**定位优先级**：

1. 显式覆盖路径（如配置项 `tmsd.config-file`）：非空白时按 `Paths.get(override.strip())` 解析，**必须存在**，否则抛 `IllegalStateException("tmsd.config-file 指向的文件不存在: ...")`；
2. 当前工作目录源码文件：`<user.dir>/src/main/resources/application.yaml`；
3. classpath 下的 `application.yaml`（**仅当 URL 协议为 `file`** 时）。

三处均无法定位时抛 `IllegalStateException("无法定位 application.yaml，请设置 tmsd.config-file")`。

### 3.2 用法示例

```java
Path yaml = ConfigFileLocator.resolve(System.getProperty("tmsd.config-file"));
```

---

## 4. `YamlConfigUpdater`

**定位**：对 `application.yaml` 做「保留注释与排版」的外科式赋值（`final` 工具类）。它会定位**指定顶层段内**的子键所在行，只替换该行的值；若子键不存在，则插入到段首之后（缩进 2 空格）。不会重排版整份 YAML，因此注释全部保留。

### 4.1 方法

```java
public static synchronized void update(Path file, String topKey, String subKey, String value) throws IOException
```

| 参数 | 说明 |
| --- | --- |
| `file` | 目标 `application.yaml` |
| `topKey` | 顶层段名（如 `ontology` / `tmsd`） |
| `subKey` | 段内子键名（如 `main-path`） |
| `value` | 新值（空串或 `null` 会写成 `""`） |

**行为细节**：

- 顶层键不存在 → 抛 `IllegalStateException("application.yaml 未找到顶层键: " + topKey)`；
- 只匹配「非注释、非空行」且以 `subKey:` 开头的行；
- `render(value)` 的引号策略：值为空 → `""`；包含 `#`、`: `、以 `"` `'` `[` `{` `*` `&` `!` `|` `>` `%` `@` `` ` `` 开头时加双引号并转义 `\` 与 `"`。

### 4.2 用法示例

```java
Path yaml = ConfigFileLocator.resolve(null);
YamlConfigUpdater.update(yaml, "ontology", "main-path", "D:/ontologies/pizza.owl");
```

---

## 5. `OntologyLabelMatcher`

**定位**：基于「个体类型闭包」或「类父类闭包」匹配 `rdfs:label` 候选词（`final` 工具类）。常用于把 IRI 翻译成可读标签。

### 5.1 方法

```java
public static String resolveMatchedWord(String entityIri,
                                        List<String> candidateLabels,
                                        boolean byIndividual,
                                        BackendService backendService)
```

| 参数 | 说明 |
| --- | --- |
| `entityIri` | 目标个体或类的完整 IRI |
| `candidateLabels` | 候选标签**有序**列表，**最后一个元素为缺省值** |
| `byIndividual` | `true`=按个体类型闭包匹配；`false`=按类父类闭包匹配 |
| `backendService` | 后端服务（访问推理器与本体） |

**返回值**：匹配到的标签；未命中或异常时返回 `candidateLabels` 的**末位缺省值**。

**匹配逻辑**：

1. 参数防御：`candidateLabels` 为空 → 返回 `""`；`entityIri` 空白 → 返回缺省值；
2. 获取类型闭包：
   - `byIndividual=true` → `backendService.getIndividual(entityIri)` + `getIndividualAllTypes(...)`
   - `byIndividual=false` → `backendService.getReasonerService().getSuperClassesIncludingSelf(entityIri)`
3. 遍历闭包中每个 `OWLClass`，取其在 TBox 上的 `rdfs:label` 注解值，**优先中文（`lang=zh`）**，取第一个落在候选集合中的值；
4. 全部未命中 → 返回缺省值。

异常处理：`IllegalArgumentException`（实体不存在）与其它异常均被捕获并返回缺省值（记录日志）。

### 5.2 用法示例

```java
String word = OntologyLabelMatcher.resolveMatchedWord(
        "http://example.org/pizza/components/NeapolitanCrust",
        List.of("那不勒斯饼底", "意式饼底", "未知饼底"),  // 末位为缺省值
        false,                                          // 按类父类闭包匹配
        backendService);
```

---

## 6. `RabbitMqHandler`

**定位**：RabbitMQ 发送封装。内部使用 `CachingConnectionFactory` + `RabbitTemplate`，**不设置 MessageConverter**，在 `send()` 中手动完成 JSON 序列化并设置 `content_type=application/json`。

### 6.1 构造方法

```java
public RabbitMqHandler()                                  // 默认 localhost:5672, guest/guest
public RabbitMqHandler(String host, int port, String username, String password)
```

底层 `com.rabbitmq.client.ConnectionFactory` 已开启 `setAutomaticRecoveryEnabled(true)`。

### 6.2 方法

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `getRabbitTemplate()` | `RabbitTemplate` | 获取底层模板（高级场景） |
| `send(String exchange, String routingKey, Object payload)` | `void` | 将 `payload` 序列化为 JSON 后发送；失败抛 `RuntimeException("RabbitMQ 消息序列化或发送失败", e)` |
| `getObjectMapper()` | `ObjectMapper` | 获取共享 `ObjectMapper`（供测试断言复用，保证序列化行为一致） |
| `destroy()` | `void` | 销毁底层 `connectionFactory`，释放资源 |

### 6.3 用法示例

```java
RabbitMqHandler mq = new RabbitMqHandler("localhost", 5672, "guest", "guest");
try {
    mq.send("camunda-exchange", "pizza.created", Map.of("id", 42, "name", "Margherita"));
} finally {
    mq.destroy();
}
```

---

## 7. `StopOnTimeoutExtension`

**定位**：JUnit 5 扩展。**一旦某个测试超时，就中止整个测试套件**（后续测试被跳过），用于防止「首个超时后继续跑大量无意义用例」。

实现接口：`TestExecutionExceptionHandler`、`BeforeEachCallback`。

### 7.1 机制

- 内部静态标志 `STOP_REQUESTED`（`AtomicBoolean`）。
- `handleTestExecutionException(...)`：若异常链中任一异常类名包含 `"Timeout"`，则置位 `STOP_REQUESTED=true` 并打印告警；随后**原样重抛**异常。
- `beforeEach(...)`：若 `STOP_REQUESTED` 已置位，则调用 `Assumptions.assumeFalse(...)` 跳过当前测试。

### 7.2 用法

```java
@ExtendWith(StopOnTimeoutExtension.class)
class MySuperHeavyTest {

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void case1() { ... }
}
```

> 注意：本扩展的停止标志为**进程内静态状态**，同一 JVM 内一经触发即对后续所有使用该扩展的测试类生效。

---

## 8. 常见注意事项

1. **构建顺序**：`Utilities` 依赖 `OpenlletResolver` / `OntopOBDAHandler`，请先确保后两者可编译。
2. **空值语义**：`OntologyLabelMatcher` 的候选列表末位即缺省值，务必保证列表最后一位是期望的兜底词。
3. **`YamlConfigUpdater` 是同步静态方法**，多线程更新同一文件时已加类锁。
4. **`RabbitMqHandler` 用完应 `destroy()`**，否则连接工厂不会释放。

---

## 8. `ArchiveSupport`

**定位**：目录归档通用工具（`final`，不可实例化）。把一组文件按相对路径打包为 zip 写入输出流。

### 8.1 方法

```java
public static void zip(Path root, List<Path> files, OutputStream out) throws IOException
```

| 参数 | 说明 |
| --- | --- |
| `root` | 基准目录（用于计算 zip 内相对路径） |
| `files` | 要打包的文件列表 |
| `out` | 输出流（调用方负责关闭） |

行为：对每个文件用 `root.relativize(f)` 计算相对路径（`\` → `/`），写入 `ZipEntry` + 文件内容，最后 `finish()`。

### 8.2 用法示例

```java
Path root = Path.of("output");
List<Path> files = Files.walk(root).filter(Files::isRegularFile).toList();
try (OutputStream os = Files.newOutputStream(root.resolve("bundle.zip"))) {
    ArchiveSupport.zip(root, files, os);
}
```

---

## 9. `FileTransferSupport`

**定位**：文件上传/传输通用工具（`final`，不可实例化）。提供 `MultipartFile` 存盘、文件名安全清洗、Content-Disposition 头生成等静态方法。

### 9.1 方法

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `save(MultipartFile file, Path target)` | `Path` | 将上传文件保存到目标路径（覆盖已有文件），返回 `target` |
| `safeName(String s)` | `String` | 去除文件系统非法字符（`\ / : * ? " < > |` → `_`）；`null` 返回 `"case"` |
| `contentDisposition(String filename)` | `String` | 兼容中文文件名的 Content-Disposition（RFC 5987），固定 `filename="download.zip"` |

### 9.2 用法示例

```java
String name = FileTransferSupport.safeName(file.getOriginalFilename());
Path target = dir.resolve(name);
FileTransferSupport.save(file, target);

String header = FileTransferSupport.contentDisposition("结果报告.zip");
// → attachment; filename="download.zip"; filename*=UTF-8''%E7%BB%93%E6%9E%9C%E6%8A%A5%E5%91%8A.zip
```

---

## 10. `ProcessOrchestrator`

**定位**：Camunda 8 流程编排通用机制（`final`，不可实例化）。提供启流程实例、读流程状态/变量（含 404 最终一致性降级）、状态推导、幂等部署等静态工具。业务层注入 `CamundaClient` 后调用本类方法，自身只保留业务变量组装与结论解读。

### 10.1 公共数据结构

```java
public record InstanceInfo(String state, boolean hasIncident) {}
```

`state` 为 `null` 表示实例尚未导出到查询存储（按运行中处理）。

### 10.2 方法

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `start(CamundaClient client, String bpmnProcessId, Map<String,Object> vars)` | `long` | 启动流程实例，返回 `processInstanceKey`；`vars` 为 `null` 时用空 Map |
| `fetchInstance(CamundaClient client, long processInstanceKey)` | `InstanceInfo` | 读取实例状态；404（尚未导出）返回 `null`（按运行中处理），仅记 DEBUG |
| `readVariables(CamundaClient client, long processInstanceKey)` | `Map<String,Object>` | 读取流程变量并解码 JSON 值为对象；失败返回空 Map |
| `statusOf(String state, boolean hasIncident)` | `String` | 由状态与 incident 标志推导统一运行状态：`COMPLETED` / `TERMINATED` / `INCIDENT` / `RUNNING` |
| `deployIfAbsent(CamundaClient client, String processId, String bpmnPath)` | `boolean` | 幂等部署：已存在则跳过，否则部署；`true`=已部署或已存在，`false`=部署失败 |

### 10.3 用法示例

```java
long key = ProcessOrchestrator.start(client, "TowerMidDesign", Map.of("machineType", "V12"));

ProcessOrchestrator.InstanceInfo info = ProcessOrchestrator.fetchInstance(client, key);
String status = ProcessOrchestrator.statusOf(info.state(), info.hasIncident());

Map<String, Object> vars = ProcessOrchestrator.readVariables(client, key);

ProcessOrchestrator.deployIfAbsent(client, "TowerMidDesign", "ontology/bpmn/TowerMidDesign.bpmn");
```
