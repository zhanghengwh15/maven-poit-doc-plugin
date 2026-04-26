## ADDED Requirements

### Requirement: QDox 扫描器作为唯一扫描引擎

系统 SHALL 仅使用基于 QDox + ASM 的自研扫描器进行源码解析，不再提供 Smart-doc 引擎切换能力。`ApiScannerEngine` 为唯一入口，内部按 QDox（源码）→ ASM（字节码）→ 反射顺序降级获取类元信息。

#### Scenario: 扫描器无引擎切换开关
- **WHEN** 启动 CLI 工具
- **THEN** 不再存在 `--scanner-engine` 参数
- **AND** 扫描始终通过 QDox/ASM 路径执行

#### Scenario: Controller 识别
- **WHEN** 扫描器发现标注 `@RestController` 或 `@Controller` 的类
- **THEN** 该类 MUST 被纳入接口提取范围
- **AND** 仅标注 `@RequestMapping` 系列注解的方法被识别为接口方法

### Requirement: CLI 移除 Smart-doc 专属配置

CLI SHALL 不再接受 Smart-doc 专属参数（`--framework`）。所有扫描配置 MUST 通过 QDox/ASM 自研扫描器原生支持的参数完成。

#### Scenario: 移除 --framework 参数
- **WHEN** 用户尝试传入 `--framework` 参数
- **THEN** CLI MUST 报错并提示该参数已移除

#### Scenario: 必需参数不变
- **WHEN** 用户正常启动扫描
- **THEN** `--scan-dir`、`--db-url`、`--db-user`、`--db-password`、`--service-version`、`--env` 等必需参数 MUST 保持不变
