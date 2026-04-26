## 1. 移除 Smart-doc 依赖与配置类

- [x] 1.1 在 `pom.xml` 中移除 `com.ly.smart-doc:smart-doc` 和 `com.ly.smart-doc:qdox` 依赖
- [x] 1.2 删除 `SmartDocBootstrap.java`
- [x] 1.3 删除 `SmartDocRunConfig.java`
- [x] 1.4 删除 `QdoxApiDocAdapter.java`

## 2. 重构 ApiDocSupport 为纯工具类

- [x] 2.1 移除 `ApiDocSupport` 中所有 `com.ly.doc.model.*` import
- [x] 2.2 新增 `extractModelsFromMethod(ApiMethod)` 方法，从自研 `ApiMethod` 抽取模型
- [x] 2.3 新增 `extractModelsFromRequestAndResponse(ApiMethod)` 方法
- [x] 2.4 新增 `resolveReqModelRef(ApiMethod)` / `resolveResModelRef(ApiMethod)` 方法
- [x] 2.5 保留 JSON 序列化逻辑（GSON），使用自研 `ApiField` 替代 `ApiParam`

## 3. 新增 SyncInput 数据接口

- [x] 3.1 新建 `SyncInput.java`，包含 `List<SyncController>` 结构
- [x] 3.2 新建 `SyncController.java`，映射 `ApiInterface` 的方法列表与类信息
- [x] 3.3 修改 `ApiScannerEngine.scan()` 返回 `SyncInput` 而非 `List<ApiInterface>`

## 4. 重构 DocSyncService

- [x] 4.1 修改 `sync()` 方法签名：`sync(SyncInput)` 替代 `sync(List<ApiDoc>)`
- [x] 4.2 内部循环改为遍历 `SyncController` → `SyncMethod`
- [x] 4.3 调用重构后的 `ApiDocSupport` 方法抽取模型
- [x] 4.4 使用 `fastjson2` 构建 pathParams / queryParams JSON（替代直接序列化 Smart-doc `ApiParam`）
- [x] 4.5 移除所有 `com.ly.doc.model.*` import

## 5. 重构 PoitApiScannerCli

- [x] 5.1 移除 Smart-doc 路径代码，直接调用 `ApiScannerEngine`
- [x] 5.2 移除 `--scanner-engine` 参数
- [x] 5.3 移除 `--framework` 参数
- [x] 5.4 移除 `runQdoxScanner()` 方法（逻辑内联到 `call()`）
- [x] 5.5 清理不再需要的 import（`ApiDoc`, `SmartDocBootstrap`, `SmartDocRunConfig`, `JavaProjectBuilder` 等）

## 6. 测试修复

- [x] 6.1 删除 `SmartDocTest.java`
- [x] 6.2 重写 `ApiDocSupportTest.java` 使用自研数据契约
- [x] 6.3 重写 `ApiSignatureUtilsTest.java`（如依赖 Smart-doc 类型）
- [x] 6.4 验证 `mvn clean test` 全部通过

## 7. 文档更新

- [x] 7.1 更新 `CLAUDE.md`：移除 Smart-doc 相关描述，更新架构图
- [x] 7.2 更新 CLI `--help` 输出（参数列表变更）
