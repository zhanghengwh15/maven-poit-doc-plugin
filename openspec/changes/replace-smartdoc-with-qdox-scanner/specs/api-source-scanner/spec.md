## ADDED Requirements

### Requirement: 扫描器替换 Smart-doc 调用入口

系统 SHALL 提供一个可在 `SmartDocBootstrap` 中替代 `ApiDataBuilder.getApiData(config)` 调用的 Java 源码扫描器，扫描器 MUST 输出与下游 `ApiDocSupport` 兼容的接口元数据集合。

#### Scenario: 通过开关切换到自研扫描器
- **WHEN** 用户在 CLI 启动时指定使用自研扫描器引擎
- **THEN** `SmartDocBootstrap` MUST 调用自研扫描器入口，而不是 `ApiDataBuilder.getApiData(config)`
- **AND** 输出对象 MUST 能被 `ApiDocSupport` 直接消费而无需修改下游同步逻辑

#### Scenario: 默认保持 Smart-doc 行为
- **WHEN** 用户未指定扫描器引擎或显式选择 Smart-doc
- **THEN** `SmartDocBootstrap` MUST 保持原有 `ApiDataBuilder.getApiData(config)` 调用路径
- **AND** 下游同步行为 MUST 与变更前一致

### Requirement: Controller 接口识别与提取

扫描器 SHALL 识别 Spring 框架下的 Controller 类与接口方法，并提取类级元数据与方法级元数据。

#### Scenario: 识别 Controller 类
- **WHEN** 扫描器加载到一个标注了 `@RestController` 或 `@Controller` 的类
- **THEN** 该类 MUST 被纳入接口提取范围
- **AND** 类级 `@RequestMapping` 的 path MUST 作为该类下所有方法路径的前缀

#### Scenario: 识别接口方法及 HTTP 方法
- **WHEN** 类中存在标注了 `@RequestMapping` / `@GetMapping` / `@PostMapping` / `@PutMapping` / `@DeleteMapping` 的方法
- **THEN** 扫描器 MUST 提取该方法为接口方法
- **AND** HTTP 方法 MUST 与注解类型一致；`@RequestMapping` 时取注解上的 `method` 属性
- **AND** 完整 path MUST 由类级前缀拼接方法级 path 得到

### Requirement: 入参位置识别与解析

扫描器 SHALL 根据参数注解或 HTTP 方法推断每个入参的位置，并提取参数名、类型、描述、required、默认值等信息。

#### Scenario: 按注解识别参数位置
- **WHEN** 接口方法的参数标注 `@PathVariable` / `@RequestParam` / `@RequestBody` / `@RequestHeader`
- **THEN** 参数位置 MUST 分别被识别为 `path` / `query` / `body` / `header`

#### Scenario: 注解缺失时按 HTTP 方法兜底
- **WHEN** 复杂对象类型的参数未携带任何位置注解
- **AND** HTTP 方法为 GET
- **THEN** 该参数 MUST 被推断为 `query` 位置
- **WHEN** HTTP 方法为 POST / PUT 且参数为复杂对象
- **THEN** 该参数 MUST 被推断为 `body` 位置

#### Scenario: @RequestBody 参数填充结构化 schema
- **WHEN** 参数标注 `@RequestBody` 且类型为复杂对象
- **THEN** 该参数 MUST 包含递归解析后的 `bodySchema` 字段树

### Requirement: 注解与 JavaDoc 优先级合并

扫描器 SHALL 按固定优先级链合并多来源的描述与必填标记，优先级链 MUST 集中在统一合并器中实现，便于后续调整。

#### Scenario: 字段描述优先级
- **WHEN** 同一字段同时存在 `@ApiModelProperty(value=...)` 与 JavaDoc
- **THEN** 输出描述 MUST 取 `@ApiModelProperty.value`
- **WHEN** 仅存在 `@Schema(description=...)`
- **THEN** 输出描述 MUST 取 `@Schema.description`
- **WHEN** 仅存在 JavaDoc
- **THEN** 输出描述 MUST 取 JavaDoc 首段

#### Scenario: 必填标记优先级
- **WHEN** 字段标注 `@ApiModelProperty(required=true)`
- **THEN** 输出 required MUST 为 true
- **WHEN** 字段未声明 `@ApiModelProperty.required` 但标注 `@NotNull` / `@NotBlank` / `@NotEmpty` 之一
- **THEN** 输出 required MUST 为 true
- **WHEN** 上述均未声明
- **THEN** 输出 required MUST 为 false

#### Scenario: hidden 字段被排除
- **WHEN** 字段标注 `@ApiModelProperty(hidden=true)`
- **THEN** 该字段 MUST 不出现在输出结果中

### Requirement: 复杂类型递归解析

扫描器 SHALL 递归解析复杂入参与返回类型，正确处理泛型、集合、Map、循环引用、继承字段，并支持响应包装类剥离。

#### Scenario: 泛型上下文传递
- **WHEN** 方法返回类型为 `Result<UserVO>`
- **AND** `Result<T>` 内部存在字段 `T data`
- **THEN** 该 `data` 字段的类型 MUST 被解析为 `UserVO` 而非 `Object`
- **AND** `data` 字段 MUST 包含 `UserVO` 的完整字段树

#### Scenario: 集合元素类型解析
- **WHEN** 字段类型为 `List<UserVO>` 或 `UserVO[]`
- **THEN** 输出字段 MUST 标记为 `isCollection=true`
- **AND** 字段树 MUST 反映 `UserVO` 的字段结构

#### Scenario: 循环引用防护
- **WHEN** 类 A 字段引用类 B，类 B 字段引用类 A
- **THEN** 扫描器 MUST 在第二次遇到 A 时返回带类名的占位字段
- **AND** 解析过程 MUST 不进入无限递归

#### Scenario: 继承字段合并
- **WHEN** 类 `Child` 继承类 `Parent`
- **THEN** 解析 `Child` 时 MUST 同时包含 `Child` 自身字段与 `Parent` 的字段（排除 `Object`）

#### Scenario: 响应包装类剥离
- **WHEN** 方法返回类型为可配置剥离清单中的包装类（如 `Result<T>` / `ResponseEntity<T>`）
- **THEN** 输出 `responseBody` MUST 直接展开为 `T` 的字段结构，而非保留外层包装

### Requirement: 输出数据契约

扫描器 SHALL 输出结构化的内部数据契约，所有字段语义稳定且能被下游适配层一次性映射到现有同步链路所需形态。

#### Scenario: 接口元数据契约
- **WHEN** 扫描完成
- **THEN** 输出 MUST 是 `List<ApiInterface>`
- **AND** 每个 `ApiInterface` MUST 包含 `className` / `classDescription` / `basePath` / `methods`
- **AND** 每个 `ApiMethod` MUST 包含 `methodName` / `httpMethod` / `path` / `summary` / `description` / `deprecated` / `parameters` / `responseBody`
- **AND** 每个 `ApiField` MUST 包含 `name` / `type` / `description` / `required` / `children` / `isCollection` / `isEnum`
