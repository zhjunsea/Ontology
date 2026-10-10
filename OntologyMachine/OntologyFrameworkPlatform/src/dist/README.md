# OntologyFrameworkPlatform 发行包

本目录为 `OntologyFrameworkPlatform` 模块构建产出的**本体应用开发平台发行包**，
用于开发与运行类似 `OntologyFrameworkTCM` 的新本体应用。

## 内容

- `OntopOBDAHandler-*.jar`、`OpenlletResolver-*.jar`、`Utilities-*.jar` — 基础库
- `EnvPrepare-*.jar` — 可执行环境启动工具
- `OntologyFrameworkExample-*-sources.jar` — 示例工程源码
- `lib/` — 第三方依赖集合
- `DEPENDENCIES.txt` / `DEPENDENCY-TREE.txt` — 依赖清单
- `新应用开发与部署指南.md` — **请从此文档开始**

## 快速开始

```bash
java -jar EnvPrepare-0.0.1-SNAPSHOT.jar start -c <新应用>/src/main/resources/application.yaml
```

详见《新应用开发与部署指南.md》。
