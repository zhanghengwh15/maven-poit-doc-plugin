# poit-api-scanner-cli

基于 [Smart-doc](https://github.com/smart-doc-group/smart-doc) 的**独立命令行扫描器**：指定源码目录（自动递归发现各模块 `src/main/java`），解析 Spring 等框架下的 Controller 与模型，将**接口清单**与**模型字段** **Upsert** 到 MySQL 8（JSON 存字段明细）。无需在业务工程中引入 Maven 插件。

---

## 使用前准备

1. **JDK 8+**、**Maven 3.6+**（仅用于打包本工具）。
2. **MySQL 8**：在目标库执行建表脚本：

   `src/main/resources/schema.sql`

3. 确保数据库账号具备对目标表的 `INSERT` / `UPDATE` 权限。

---

## 构建可执行 Fat JAR

在本仓库目录执行：

```bash
cd maven-poit-doc-plugin   # 或你克隆后的项目根目录
mvn clean package -DskipTests
```

产物（已 Shade、可直接运行）：

`target/poit-api-scanner-cli-<version>.jar`

安装到本地仓库时坐标为：

- `groupId`：`com.poit.doc`
- `artifactId`：`poit-api-scanner-cli`
- `version`：以 `pom.xml` 为准

---

## 运行示例

```bash
java -jar target/poit-api-scanner-cli-1.0.0-SNAPSHOT.jar \
  --scan-dir=/path/to/your/repo-or-module-root \
  --db-url='jdbc:mysql://127.0.0.1:3306/your_doc_db?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai' \
  --db-user=root \
  --db-password=secret \
  --service-name=user-service \
  --service-version=v1 \
  --env=dev
```

查看全部参数：

```bash
java -jar target/poit-api-scanner-cli-1.0.0-SNAPSHOT.jar --help
```

常用可选参数：

| 选项 | 说明 |
|------|------|
| `--framework` | Smart-doc 框架，默认 `spring` |
| `--package-filters` | 只扫描指定包（Smart-doc 语法） |
| `--package-exclude-filters` | 排除包 |
| `--project-name` | 展示名，默认取 `--scan-dir` 目录名 |
| `--artifact-id` | 写入库时 artifactId，默认与项目名相同 |
| `--source-path` | 可重复指定，显式源码根；一旦指定则**不再**自动发现 |

---

## 配置项说明（CLI）

| 选项 | 是否必填 | 说明 |
|------|----------|------|
| `--scan-dir` | 是 | 扫描根目录；未指定 `--source-path` 时递归发现 `**/src/main/java` |
| `--service-name` | 是 | 微服务 / 应用名 |
| `--service-version` | 是 | 如 `v1` |
| `--env` | 是 | 如 `dev` / `test` / `prod` |
| `--db-url` | 是 | MySQL JDBC URL |
| `--db-user` / `--db-password` | 是 | 数据库账号 |
| `--framework` | 否 | 默认 `spring` |
| `--package-filters` / `--package-exclude-filters` | 否 | 与 Smart-doc 一致 |
| `--source-path` | 否 | 多源码根；不指定则按目录自动发现 |
| `--project-name` / `--artifact-id` | 否 | 有默认值，见上表 |

---

## 数据写入说明（简要）

- **`api_interface`**：每个接口方法一条记录。
- **`api_model_definition`**：按全类名聚合模型，**fields** 列为 JSON。

---

## 常见问题

**Q：解析不到 Controller？**  
A：检查 `--package-filters` / `--framework`，并确认源码在 `src/main/java` 或通过 `--source-path` 指到正确根目录。

**Q：表不存在或连接失败？**  
A：先执行 `schema.sql`，核对 JDBC URL 与账号权限。

---

## 相关仓库

- Smart-doc：<https://github.com/smart-doc-group/smart-doc>
