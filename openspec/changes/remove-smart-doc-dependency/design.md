## Context

当前项目通过 `SmartDocBootstrap.loadApiDocs()` 调用 Smart-doc 引擎获取 `List<ApiDoc>`，再由 `ApiDocSupport.flattenControllerDocs()` 打平、`DocSyncService.sync()` 同步到 MySQL。在之前的 `replace-smartdoc-with-qdox-scanner` 变更中，已实现基于 QDox/ASM 的自研扫描器 `ApiScannerEngine`，并通过 `QdoxApiDocAdapter` 将其输出转换为 Smart-doc `ApiDoc` 格式以实现兼容。现在 Smart-doc 仅作为中间格式传递层存在，不再承担核心解析能力。

当前 Smart-doc 依赖链涉及的类：
- `SmartDocBootstrap.java` — 构建 `ApiConfig` 并调用 `ApiDataBuilder.getApiData(config)`
- `SmartDocRunConfig.java` — Smart-doc 运行配置 POJO
- `QdoxApiDocAdapter.java` — `ApiInterface` → `ApiDoc` 适配器
- `ApiDocSupport.java` — 依赖 Smart-doc `ApiParam`/`ApiMethodDoc` 类型做树遍历
- `DocSyncService.java` — `sync()` 方法接收 `List<ApiDoc>`

## Goals / Non-Goals

**Goals:**
- 完全移除 Smart-doc 依赖及其传递依赖
- 将 `ApiScannerEngine` 的输出直接对接 `DocSyncService`，不再经过 `ApiDoc` 中间格式
- 保持数据库同步行为一致（相同的表结构、相同的数据内容）

**Non-Goals:**
- 不修改数据库 schema 或同步逻辑的核心算法（upsert/delete/MD5 检测）
- 不修改 `ModelDefinitionSync` 的模型处理逻辑
- 不引入新的外部依赖

## Decisions

**1. 新增 `SyncInput` 统一数据输入接口，替代 `List<ApiDoc>`**
- 定义 `SyncInput` 包含 `List<SyncController>` 结构，直接映射 `ApiInterface`
- 下游 `DocSyncService` 改为接收 `SyncInput` 而非 `List<ApiDoc>`
- 替代方案：直接传入 `List<ApiInterface>`。被否决：新增中间层可隔离 CLI 参数变化对同步链路的影响

**2. 重构 `ApiDocSupport` 为纯工具类，不再依赖 Smart-doc 类型**
- 将 `extractModelsFromRequestAndResponse` 等方法改为从 `ApiMethod`（自研模型）抽取
- 移除所有 `com.ly.doc.model.*` import
- 替代方案：完全删除 `ApiDocSupport` 并将其逻辑内联到 `DocSyncService`。被否决：模型抽取逻辑独立且可复用

**3. 移除 `SmartDocBootstrap` 和 `SmartDocRunConfig`，将配置内联到 CLI**
- `PoitApiScannerCli` 直接构建 `SourceRootDiscovery` + `ApiScannerEngine`
- `SourceLoader` 已封装源码加载逻辑，可直接调用
- 替代方案：保留 `SmartDocBootstrap` 但替换内部实现。被否决：类名与实际能力不符，增加理解成本

**4. 移除 `--scanner-engine` 和 `--framework` CLI 参数**
- 仅剩 QDox 一条路径，开关不再需要
- Smart-doc 配置（`--framework`）随依赖一起移除

## Risks / Trade-offs

- **风险：`ApiDocSupport` 重构后模型抽取逻辑与 Smart-doc 版本存在差异** → 缓解：对相同样本项目跑两套扫描器，对比 `api_interface` 和 `api_model_definition` 表内容
- **风险：Smart-doc `ApiParam` 直接序列化到 JSON（`paramsToJson`），移除后需手动构建 JSON** → 缓解：使用 `fastjson2` 构建等价 JSON 结构
- **风险：测试覆盖率下降** → 缓解：原有 Smart-doc 测试替换为基于 QDox 的等价测试
