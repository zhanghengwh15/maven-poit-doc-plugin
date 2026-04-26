## ADDED Requirements

### Requirement: 扫描器直接输出同步链路所需数据形态

系统 SHALL 提供 `SyncInput` 数据结构，由 `ApiScannerEngine.scan()` 直接返回，供 `DocSyncService.sync()` 消费。该结构 MUST 包含完整的 Controller 元数据、方法元数据、参数信息和响应体结构，不再经过 Smart-doc `ApiDoc` 中间格式转换。

#### Scenario: 扫描器输出直接用于同步
- **WHEN** `ApiScannerEngine.scan()` 执行完毕
- **THEN** 返回对象 MUST 能被 `DocSyncService.sync()` 直接消费
- **AND** 无需任何中间格式转换步骤

#### Scenario: 模型抽取不再依赖 Smart-doc 类型
- **WHEN** `ApiDocSupport` 抽取模型定义时
- **THEN** 所有输入 MUST 来自自研数据契约（`ApiMethod` / `ApiField`）
- **AND** 不得 import 任何 `com.ly.doc.model.*` 类型

### Requirement: 同步服务接收类型变更

`DocSyncService.sync()` SHALL 接收 `SyncInput` 类型参数而非 `List<ApiDoc>`。同步逻辑内部 MUST 从 `SyncInput` 提取模型合并与接口记录，行为与接收 `ApiDoc` 时一致。

#### Scenario: 同步行为一致性
- **WHEN** 使用 `SyncInput` 执行同步
- **THEN** 数据库中的 `api_interface` 和 `api_model_definition` 记录 MUST 与使用 Smart-doc 路径时一致
- **AND** MD5 签名计算结果 MUST 相同

#### Scenario: 参数 JSON 序列化
- **WHEN** 构建 pathParams / queryParams JSON 时
- **THEN** 序列化结果 MUST 与 Smart-doc `ApiParam` 直接序列化等价
- **AND** 使用 `fastjson2` 完成序列化

## REMOVED Requirements

### Requirement: Smart-doc 中间格式适配
**Reason**: Smart-doc 依赖将被完全移除，不再需要 `ApiDoc` 中间格式
**Migration**: `QdoxApiDocAdapter` 逻辑内联到 `ApiDocSupport` 重构中，不再单独存在
