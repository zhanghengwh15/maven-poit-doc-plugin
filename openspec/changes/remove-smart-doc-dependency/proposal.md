## Why

Smart-doc 目前仅作为"下游数据消费者"存在（`SmartDocBootstrap.loadApiDocs()` 的旧路径 + `ApiDocSupport` 将 Smart-doc 的 `ApiDoc`/`ApiMethodDoc`/`ApiParam` 树扁平化为同步链路所需的形态）。QDox 扫描器已实现完整能力并通过 `QdoxApiDocAdapter` 将输出转换为 Smart-doc 兼容格式。移除 Smart-doc 依赖可消除约 5MB 的传递依赖体积、消除潜在的类冲突、降低构建时间，并将代码控制权完全收回到本项目内。

## What Changes

- 移除 `com.ly.smart-doc:smart-doc` 及其传递依赖（含 `com.ly.smart-doc:qdox`）
- 替换 `SmartDocBootstrap.loadApiDocs()` 调用点，改为直接调用 `ApiScannerEngine`
- 移除 `QdoxApiDocAdapter`，将 QDox 扫描器的 `List<ApiInterface>` 直接对接下游同步链路
- 保留 `ApiDocSupport` 的模型抽取能力，但其输入来源改为 QDox 内部数据契约
- **BREAKING**: 移除 `--framework` CLI 参数（Smart-doc 专属配置不再需要）

## Capabilities

### New Capabilities
- `native-sync-output`: 扫描器直接输出下游 DocSyncService 所需的数据形态，不再经过 Smart-doc ApiDoc 中间格式
- `direct-controller-scan`: 基于 QDox/ASM 的 Controller 扫描能力直接驱动同步链路

### Modified Capabilities
<!-- 无现有 spec 需要修改 -->

## Impact

- **依赖**: 移除 `smart-doc` 及其全部传递依赖（`qdox-fork`, `datafaker` 等）；保留 `com.thoughtworks.qdox:qdox` + `org.ow2.asm`
- **代码**: 删除 `SmartDocBootstrap.java`、`SmartDocRunConfig.java`、`QdoxApiDocAdapter.java` 中 Smart-doc 适配代码
- **测试**: 需移除/替换依赖 Smart-doc API 的单元测试（`SmartDocTest`, `ApiDocSupportTest` 等）
- **CLI**: 移除 `--framework` 参数；`--scanner-engine` 参数不再需要（仅剩 qdox 一条路径）
- **数据库**: 同步链路行为保持一致，不受影响
