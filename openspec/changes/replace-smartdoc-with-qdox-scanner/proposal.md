## Why

Smart-doc 当前承担源码扫描职责（`SmartDocBootstrap.java:59` 调用 `ApiDataBuilder.getApiData(config)`），但它在以下场景能力不足：泛型解析不彻底、无法稳定剥离自定义响应包装类、对仅有 `.class` 字节码的依赖类直接丢字段、注解优先级合并不可控。这些能力短板已经阻塞业务侧对接口文档准确性的要求，自研一套基于 QDox 的扫描器才能解决根因。

## What Changes

- 新增基于 QDox 的源码扫描器，作为可独立调用的内部组件，输出与 `ApiDocSupport` 兼容的 Controller/Method/Field 数据结构
- 引入统一的 `ClassMetaProvider` 抽象，按 QDox（源码）→ ASM（字节码）→ 反射的优先级降级，解决依赖类无源码时的字段丢失
- 新增 `TypeResolver` 递归引擎，处理泛型上下文传递、循环引用防护、继承字段、响应包装类（Result / ResponseEntity 等）剥离
- 新增 `DocMerger` 合并器，按 `@ApiModelProperty` → `@Schema` → `@JsonPropertyDescription` → JavaDoc → 空 的优先级链合并字段描述与 required 标记
- 改造 `SmartDocBootstrap`：将 `ApiDataBuilder.getApiData(config)` 替换为新扫描器入口，通过开关支持新老切换便于灰度验证
- Smart-doc 依赖暂时保留，待新扫描器在生产环境验证稳定后再做下线

## Capabilities

### New Capabilities
- `api-source-scanner`: 基于 QDox 的 Java 源码扫描能力，提取 Controller 接口元数据并输出与下游同步链路兼容的统一数据契约
- `class-meta-resolution`: 类元信息解析能力，对外提供"按全限定名拿字段/注解/泛型"的统一接口，内部按源码→字节码→反射降级

### Modified Capabilities
<!-- 当前没有已存在的 spec，无需修改 -->

## Impact

- **代码**：替换 `src/main/java/com/poit/doc/sync/config/SmartDocBootstrap.java` 中 `ApiDataBuilder.getApiData` 调用点；新增 `com.poit.doc.scanner` 包承载新扫描器各层组件
- **依赖**：新增 `com.thoughtworks.qdox:qdox` 与 `org.ow2.asm:asm`；Smart-doc 相关依赖保留待下线
- **数据契约**：新增 `ApiInterface` / `ApiMethod` / `ApiParam` / `ApiField` 内部数据模型，由新扫描器输出，由 `ApiDocSupport` 消费，对下游数据库同步无侵入
- **配置**：新增扫描器开关与响应包装类剥离清单等配置项
- **测试**：需补充覆盖泛型、循环引用、继承字段、ASM 字节码兜底、注解优先级合并等场景的单元测试
