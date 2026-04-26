## ADDED Requirements

### Requirement: 统一类元信息访问抽象

系统 SHALL 提供 `ClassMetaProvider` 抽象接口，支持按全限定类名获取类元信息（字段、注解、泛型形参、父类、接口、枚举值）。所有上层逻辑（扫描器、类型解析器、合并器）MUST 仅依赖该抽象，不直接依赖 QDox / ASM / 反射任一实现。

#### Scenario: 通过抽象接口获取类元信息
- **WHEN** 上层组件需要某个类的字段列表或注解信息
- **THEN** 该组件 MUST 通过 `ClassMetaProvider.find(fqn)` 获取
- **AND** 返回结果 MUST 是统一的 `ClassMeta` 对象，调用方无法感知数据来源

### Requirement: 三级降级链

系统 SHALL 按 QDox → ASM → 反射的优先级顺序解析类元信息，前一级未命中时降级到下一级。每个类的实际命中来源 MUST 可记录与可观测，便于排查描述缺失等问题。

#### Scenario: 命中源码（QDox）
- **WHEN** 类的 `.java` 源码在扫描范围内
- **THEN** `ClassMetaProvider` MUST 返回基于 QDox 解析的 `ClassMeta`
- **AND** 该 `ClassMeta` 的字段 MUST 包含 JavaDoc 注释内容

#### Scenario: 降级到字节码（ASM）
- **WHEN** 类只有 `.class` 没有 `.java`
- **THEN** `ClassMetaProvider` MUST 返回基于 ASM 解析的 `ClassMeta`
- **AND** 字段名、字段类型、注解 MUST 完整可用
- **AND** JavaDoc 内容 MUST 为 `null`，由上层合并器按优先级链兜底

#### Scenario: 降级到反射
- **WHEN** 类既无源码也无独立字节码资源（例如运行时动态生成）
- **AND** 类已存在于扫描器 classpath
- **THEN** `ClassMetaProvider` MUST 返回基于反射的 `ClassMeta`
- **AND** 反射实现 MUST 使用 `Class.forName(fqn, false, classLoader)` 调用，避免触发目标类的静态初始化

#### Scenario: 三级均未命中
- **WHEN** 三级 Provider 全部找不到目标类
- **THEN** `ClassMetaProvider.find(fqn)` MUST 返回空 Optional
- **AND** 系统 MUST 记录一条警告日志包含未命中的全限定类名，用于后续补齐 classpath

### Requirement: 字节码注解读取保留策略说明

ASM Provider SHALL 同时收集 RUNTIME 与 CLASS 保留策略的注解；SOURCE 级注解（如 `@Override`、Lombok 的 `@Data`）由于不存在于字节码中，MUST 不被期望可读，且业务关心的 Swagger / JSR-303 注解 MUST 全部能被读取。

#### Scenario: 业务注解可读
- **WHEN** 类的字节码中存在 `@ApiModelProperty` / `@Schema` / `@NotNull` 等 RUNTIME 注解
- **THEN** ASM Provider MUST 完整读出注解的成员值（含字符串、布尔、枚举、数组、嵌套注解）

#### Scenario: SOURCE 级注解的合理缺失
- **WHEN** 字段被 Lombok `@Getter` 标注但仅在源码可见
- **THEN** ASM Provider MUST 不返回该注解
- **AND** 上层逻辑 MUST 不依赖该类型注解

### Requirement: 类元信息缓存

`ClassMetaProvider` SHALL 对同一全限定类名的解析结果做进程内缓存，避免重复解析造成扫描耗时膨胀。

#### Scenario: 重复查询命中缓存
- **WHEN** 同一全限定类名在一次扫描过程中被多次请求
- **THEN** 后续请求 MUST 命中缓存，不重复执行 QDox / ASM / 反射解析
