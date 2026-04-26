## Context

当前 `SmartDocBootstrap.java:59` 调用 `ApiDataBuilder.getApiData(config)` 由 Smart-doc 完成 Java 源码扫描，下游 `ApiDocSupport` 再做扁平化与模型抽取。Smart-doc 在泛型解析、响应包装类剥离、注解优先级合并、字节码兜底等方面能力不足，已出现接口字段缺失、描述错位等可观测的质量问题。需要在不破坏下游同步链路的前提下，自研一套可控的扫描器替换该入口。

## Goals / Non-Goals

**Goals:**
- 用自研扫描器完整替代 `ApiDataBuilder.getApiData(config)` 调用点的能力
- 输出与现有 `ApiDocSupport` 消费形态对齐的内部数据契约，让下游同步链路无感知切换
- 当依赖类只有 `.class` 没有 `.java` 时仍能拿到字段名、字段类型、注解（容忍 JavaDoc 缺失），不再整字段丢失
- 注解与 JavaDoc 的优先级合并规则可见、可调，不再隐藏在第三方库内部
- 提供新老切换开关，支持灰度验证

**Non-Goals:**
- 不在本次变更中下线 Smart-doc 依赖（保留作为兜底与对比基准）
- 不改造下游数据库同步逻辑（`DocSyncService` / `ModelDefinitionSync` 不动）
- 不实现完整的 OpenAPI/Swagger UI 输出（只做内部数据契约）
- 不处理非 Spring 框架的 Controller 形态

## Decisions

**1. 分层架构：SourceLoader → ClassFilter → MethodParser → TypeResolver → DocMerger**
- 每层职责单一、可独立替换与测试
- 替代方案：单一大类直接对接 QDox API。被否决原因：耦合度高，泛型/兜底/合并三件事混在一起难以演进

**2. 抽象 `ClassMetaProvider` 统一类元信息访问，三级降级**
- 顺序：QDox（源码）→ ASM（字节码）→ 反射
- ASM 优先于反射：不需要把目标类加载进 JVM，避免类加载冲突与静态初始化副作用
- 反射调用 `Class.forName(fqn, false, loader)`，第二个参数为 `false` 防止触发静态初始化
- 替代方案：只用 QDox。被否决原因：依赖闭包内仅有字节码的 DTO 会整体丢字段

**3. `TypeResolver` 持有 `GenericContext` 栈，递归处理泛型与防循环**
- 解析 `Result<UserVO>` 时把 `T → UserVO` 推入上下文，子调用查得到真实类型
- `visited` Set 防爆栈，命中时返回带类名占位字段（不展开 children）
- 包装类剥离做成可配置清单（默认含 `Result` / `ResponseEntity` / `ApiResponse`）

**4. `DocMerger` 集中所有优先级链**
- 描述：`@ApiModelProperty.value` → `@Schema.description` → `@JsonPropertyDescription` → JavaDoc → 空
- required：`@ApiModelProperty.required` → `@RequestParam.required` → `@NotNull/@NotBlank/@NotEmpty` → false
- 工具方法 `pickFirst` / `boolFirst` 统一入口，后期改优先级只改一处

**5. 新老切换开关**
- 通过 CLI 参数或配置项（如 `--scanner-engine=qdox|smartdoc`）选择实现
- 默认仍走 Smart-doc，灰度环境切换到 qdox 验证

**6. 输出数据契约（内部模型）**
- `ApiInterface(className, classDescription, basePath, methods)`
- `ApiMethod(methodName, httpMethod, path, summary, description, deprecated, parameters, responseBody)`
- `ApiParam(name, type, paramIn, description, required, defaultValue, bodySchema)`
- `ApiField(name, type, description, required, example, children, isCollection, isEnum, enumValues)`
- 由 `ApiDocSupport` 适配层负责把该契约转换为现有同步链路所需的形态

## Risks / Trade-offs

- **风险：QDox 跨模块解析需源码闭包** → 缓解：未命中源码时降级到 ASM；记录每个类命中的 Provider 便于排查
- **风险：ASM 注解保留策略限制（SOURCE 级注解读不到）** → 缓解：业务关心的注解（Swagger / JSR-303）均为 RUNTIME，可接受；记录在文档中
- **风险：泛型 / 包装类剥离逻辑复杂，回归代价大** → 缓解：每类场景配单元测试（`Result<UserVO>` / `Page<List<X>>` / `Map<String, X>` / 自引用 / 多层继承）
- **风险：新老扫描器输出差异引入数据漂移** → 缓解：开关默认 Smart-doc，灰度切换；对比工具记录差异字段
- **风险：classpath 构建复杂（ASM 需要解析依赖 jar）** → 缓解：先要求用户传 `--classpath` 文件，工具不自动下载依赖

## Migration Plan

1. 新扫描器与 Smart-doc 并行存在，由开关控制
2. 在 CI 环境对若干样本项目同时跑两套扫描器，对比 `ApiInterface` / `ApiField` 输出差异
3. 差异收敛后，先在内部环境默认切换到 qdox 引擎
4. 生产稳定一段周期后，再做 Smart-doc 依赖与代码下线（不在本变更范围内）
5. 回滚策略：开关切回 `smartdoc` 即恢复原行为，无需代码回滚

## Open Questions

- classpath 来源：要求用户显式传入 vs 工具内置 `mvn dependency:build-classpath` 调用？倾向前者，本变更先做最小可用
- 是否需要支持 Spring WebFlux 的 `Mono<T>` / `Flux<T>` 包装剥离？建议作为后续增量
- 扫描结果是否做磁盘缓存以加速重复扫描？建议作为后续优化
