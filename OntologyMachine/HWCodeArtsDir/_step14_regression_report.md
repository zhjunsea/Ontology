# ZiWeiDouShu 第 14 步 · 词汇表测试与全流程测试 — 回归报告

- 日期：2026-10-31
- 应用：OntologyFrameworkZiWeiDouShu（紫微斗数）
- 执行：码道　确认：陈希夷（领域专家）

## 1. 环境
- `EnvPrepare start`（后台常驻）：Ontop 8080 / MySQL 3306 / RabbitMQ 5672·15672 / Camunda 9080 / Zeebe 26500 全部 OPEN。
- `ZiWeiDouShuApplication --spring.profiles.active=ZiWeiDouShuBPMN`：Tomcat 9089，21 个 `@JobWorker` 全部注册订阅。

## 2. 词汇表测试
- 命令：`./mvnw -pl OntologyFrameworkZiWeiDouShu -am test -Dtest=AllZiWeiDouShuSuiteTest -Dsurefire.failIfNoSpecifiedTests=false`
- `validation/ZiWeiDouShuSkosTerminologyTest`：6/6 ✅（P-1~P-4 + N-1~N-2）。
- 第 14 步加固（仅测试程序）：P-4 追加逐条 `skos:altLabel` 行「# 源:」来源注释校验。

## 3. 全流程回归
`Tests run: 39, Failures: 0, Errors: 0, Skipped: 0` → **BUILD SUCCESS**（无跳过，全部实跑）

| 测试类 | 用例 | 结果 |
|---|---|---|
| ZiWeiDouShuOntologyTests | 13 | ✅ 13/13 |
| ZiWeiDouShuBpmnInMemoryTest | 9 | ✅ 9/9 |
| ZiWeiDouShuBpmnRealEngineTest | 9 | ✅ 9/9 |
| ZiWeiDouShuSwrlRabbitMqTest | 2 | ✅ 2/2 |
| validation/ZiWeiDouShuSkosTerminologyTest | 6 | ✅ 6/6 |
| **合计** | **39** | **✅ 39/39** |

## 4. 机检
- `validate_structure.py --app-dir OntologyFrameworkZiWeiDouShu --short ZiWeiDouShu` → ERROR=0 WARN=1 INFO=57。
- 禁区零改动：`git status --porcelain OntologyResolver OpenlletResolver OntopOBDAHandler` 为空。

## 5. 结论
第 14 步通过，构建链可复现（★铁律 1/2/19），**14 步流程构建完成**。

## 证据文件
- 回归日志：`HWCodeArtsDir/_step14_regress.log`
- 应用日志：`HWCodeArtsDir/_step14_app.log`
- 环境日志：`HWCodeArtsDir/_step14_bg.log`
- 构建日志：`HWCodeArtsDir/_step14_build.log`
