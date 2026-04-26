## 1. 依赖与骨架

- [x] 1.1 在 `pom.xml` 添加 QDox 与 ASM 依赖
- [x] 1.2 新建 `com.poit.doc.scanner` 包及子包（`provider` / `model` / `resolver` / `merger`）
- [x] 1.3 定义内部数据契约：`ApiInterface` / `ApiMethod` / `ApiParam` / `ApiField`
- [x] 1.4 定义 `ClassMeta` / `FieldMeta` / `AnnotationMeta` / `TypeMeta` 抽象接口

## 2. ClassMetaProvider 三级降级

- [x] 2.1 实现 `QDoxClassMetaProvider`，包装 `JavaProjectBuilder`，对外暴露 `ClassMeta`
- [x] 2.2 实现 `AsmClassMetaProvider`，使用 `ClassReader` + 自定义 `ClassVisitor` 收集字段、注解、签名
- [x] 2.3 在 ASM Provider 中实现注解值收集（基础值、数组、嵌套注解、枚举）
- [x] 2.4 在 ASM Provider 中通过 `SignatureReader` 解析泛型签名为 `TypeMeta`
- [x] 2.5 实现 `ReflectionClassMetaProvider`，用 `Class.forName(fqn, false, loader)` 调用
- [x] 2.6 实现 `CompositeClassMetaProvider`，按 QDox → ASM → 反射顺序降级并加进程内缓存
- [x] 2.7 在每次降级命中时记录命中来源日志，便于后续排查

## 3. 扫描主链路

- [x] 3.1 实现 `SourceLoader`：调用现有 `SourceRootDiscovery`，把所有 `src/main/java` 目录加入 `JavaProjectBuilder`
- [x] 3.2 实现 `ClassFilter`：识别 `@RestController` / `@Controller`，结合 package-filters 做包级过滤
- [x] 3.3 实现 `MethodParser`：识别 `@RequestMapping` 系列注解、提取 HTTP 方法、拼接 path、读取 deprecated/summary
- [x] 3.4 在 `MethodParser` 中预解析方法 JavaDoc 的 `@param` 标签为 `Map<String, String>`

## 4. TypeResolver 递归引擎

- [x] 4.1 实现基础类型 / 字符串 / 日期类型的直接返回逻辑
- [x] 4.2 实现集合类型解析（`List` / `Set` / 数组），输出 `isCollection=true`
- [x] 4.3 实现 Map 类型解析（默认只展开 value 类型）
- [x] 4.4 实现 `GenericContext` 栈，支持泛型形参 → 实参的传递
- [x] 4.5 实现 `visited` Set 防循环引用，命中时返回占位字段
- [x] 4.6 实现继承字段递归收集（沿 `getSuperclass()` 向上爬，排除 `Object`）
- [x] 4.7 实现枚举识别与 `enumValues` 收集
- [x] 4.8 实现响应包装类剥离逻辑，剥离清单可配置

## 5. DocMerger 合并器

- [x] 5.1 实现 `pickFirst(String...)` / `boolFirst(Boolean...)` 工具方法
- [x] 5.2 实现字段描述优先级链：`@ApiModelProperty.value` → `@Schema.description` → `@JsonPropertyDescription` → JavaDoc → 空
- [x] 5.3 实现 required 优先级链：`@ApiModelProperty.required` → `@RequestParam.required` → JSR-303 校验注解 → false
- [x] 5.4 实现 `@ApiModelProperty(hidden=true)` 字段过滤
- [x] 5.5 实现 `@JsonProperty` 字段名重写

## 6. 入参位置识别

- [x] 6.1 按 `@PathVariable` / `@RequestParam` / `@RequestBody` / `@RequestHeader` 注解识别位置
- [x] 6.2 缺失注解时按 HTTP 方法兜底推断（GET → query / POST/PUT → body）
- [x] 6.3 `@RequestBody` 参数填充递归 `bodySchema`

## 7. 入口替换与切换开关

- [x] 7.1 在 `PoitApiScannerCli` 增加 `--scanner-engine` 参数（取值 `qdox` / `smartdoc`，默认 `smartdoc`）
- [x] 7.2 在 `SmartDocBootstrap` 中根据开关分发：原路径走 `ApiDataBuilder.getApiData(config)`；新路径走自研扫描器
- [x] 7.3 实现适配层，把自研扫描器的 `List<ApiInterface>` 转换为 `ApiDocSupport` 期望的输入形态
- [x] 7.4 验证下游 `DocSyncService` / `ModelDefinitionSync` 在新路径下行为一致

## 8. 测试

- [x] 8.1 单元测试：QDox Provider 解析 Controller 与 POJO
- [x] 8.2 单元测试：ASM Provider 解析仅有字节码的依赖类（包含泛型字段、Swagger 注解、JSR-303 注解）
- [x] 8.3 单元测试：泛型场景 `Result<UserVO>` / `Page<List<UserVO>>` / `Map<String, UserVO>`
- [x] 8.4 单元测试：循环引用、深层继承、枚举字段
- [x] 8.5 单元测试：注解优先级合并各分支
- [ ] 8.6 集成测试：在样本项目上对比 Smart-doc 与新扫描器的输出差异
- [x] 8.7 集成测试：CLI 端到端，开关切换 qdox 引擎跑通完整数据库同步链路

## 9. 文档与收尾

- [x] 9.1 在 `CLAUDE.md` 与 README 中补充 `--scanner-engine` 开关与 classpath 传入方式说明
- [x] 9.2 记录已知差异与下线 Smart-doc 的后续计划
