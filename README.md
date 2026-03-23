# poit-api-doc（maven-poit-doc-plugin）

基于 [Smart-doc](https://github.com/smart-doc-group/smart-doc) 的 Maven 插件：在构建时解析 Spring 等框架下的 Controller 源码，将**接口清单**与**模型字段**写入 MySQL 8（JSON 存字段明细，支持 Upsert）。

---

## 使用前准备

1. **JDK 8+**、**Maven 3.6+**（建议与插件编译所用 Maven 版本一致）。
2. **MySQL 8**：在目标库执行建表脚本：

   `maven-poit-doc-plugin/src/main/resources/schema.sql`

3. 确保业务库账号具备对上述表的 `INSERT` / `UPDATE` 权限。

---

## 1. 安装插件到本地仓库

在本仓库根目录执行（或进入 `maven-poit-doc-plugin` 目录）：

```bash
cd maven-poit-doc-plugin
mvn clean install -DskipTests
```

安装后坐标为：

- `groupId`：`com.poit.doc`
- `artifactId`：`maven-poit-doc-plugin`
- `version`：以 `pom.xml` 中为准（当前为 `1.0.0-SNAPSHOT`）

---

## 2. 在业务项目中引入插件

在需要导出文档的 **业务模块** 的 `pom.xml` 里增加：

```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.poit.doc</groupId>
            <artifactId>maven-poit-doc-plugin</artifactId>
            <version>1.0.0-SNAPSHOT</version>
            <configuration>
                <!-- 必填：服务与环境标识 -->
                <serviceName>user-service</serviceName>
                <serviceVersion>v1</serviceVersion>
                <env>dev</env>
                <!-- 必填：数据库 -->
                <dbUrl>jdbc:mysql://127.0.0.1:3306/your_doc_db?useUnicode=true&amp;characterEncoding=utf-8&amp;serverTimezone=Asia/Shanghai</dbUrl>
                <dbUser>root</dbUser>
                <dbPassword>your_password</dbPassword>
                <!-- 可选：默认 spring -->
                <framework>spring</framework>
                <!-- 可选：只扫描指定包，Smart-doc 语法，如 com.foo.api -->
                <!-- <packageFilters>com.yourcompany.api</packageFilters> -->
                <!-- 可选：多源码根（默认仅 ${project.basedir}/src/main/java）；List 参数可写多个同名子元素 -->
                <!--
                <sourcePaths>
                    <sourcePaths>${project.basedir}/src/main/java</sourcePaths>
                    <sourcePaths>${project.basedir}/src/main/kotlin</sourcePaths>
                </sourcePaths>
                -->
            </configuration>
            <executions>
                <execution>
                    <goals>
                        <goal>sync</goal>
                    </goals>
                    <phase>compile</phase>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

说明：

- 插件默认在 **`compile` 阶段**执行 `sync`，与 **`requiresDependencyResolution=compile`** 配合，能解析项目编译类路径上的类型。
- 若只想手动执行、不绑定生命周期，可去掉 `<executions>`，在工程目录下单独运行下方命令。

---

## 3. 如何执行

### 3.1 随 Maven 生命周期执行

```bash
mvn compile
```

（若已绑定 `sync` 到 `compile`，编译时会自动同步文档。）

### 3.2 单独调用插件 Goal

插件前缀为 **`my-doc-plugin`**（在 `pom.xml` 的 `maven-plugin-plugin` 中配置）：

```bash
mvn my-doc-plugin:sync
```

也可写全坐标：

```bash
mvn com.poit.doc:maven-poit-doc-plugin:1.0.0-SNAPSHOT:sync
```

### 3.3 用命令行覆盖部分参数（可选）

部分参数支持通过 **系统属性** 传入（与 `@Parameter(property = "...")` 对应），例如：

```bash
mvn my-doc-plugin:sync \
  -Dpoit.doc.serviceName=demo \
  -Dpoit.doc.env=prod
```

常用属性前缀：`poit.doc.*`（如 `poit.doc.skip=true` 可跳过本次同步）。

---

## 4. 配置项说明

| 配置项 | 是否必填 | 说明 |
|--------|----------|------|
| `serviceName` | 是 | 微服务 / 应用名，写入库表用于区分租户 |
| `serviceVersion` | 是 | 服务版本，如 `v1` |
| `env` | 是 | 环境，如 `dev` / `test` / `prod` |
| `dbUrl` | 是 | JDBC URL（MySQL 8） |
| `dbUser` | 是 | 数据库用户名 |
| `dbPassword` | 是 | 数据库密码 |
| `framework` | 否 | Smart-doc 框架标识，默认 `spring` |
| `packageFilters` | 否 | 包过滤，与 Smart-doc 一致 |
| `packageExcludeFilters` | 否 | 排除包 |
| `sourcePaths` | 否 | 多源码根列表；不配置则使用 `${project.basedir}/src/main/java` |
| `skip` | 否 | `true` 时跳过同步（属性 `poit.doc.skip`） |

---

## 5. 可执行 Fat JAR（不通过 Maven 跑业务项目时）

在插件工程目录打包带依赖的 JAR：

```bash
cd maven-poit-doc-plugin
mvn -Pstandalone clean package -DskipTests
```

产物：`target/maven-poit-doc-plugin-<version>-standalone.jar`

示例：

```bash
java -jar target/maven-poit-doc-plugin-1.0.0-SNAPSHOT-standalone.jar \
  --project=/path/to/your-service-module \
  --dbUrl=jdbc:mysql://127.0.0.1:3306/your_doc_db \
  --dbUser=root \
  --dbPassword=secret \
  --serviceName=user-service \
  --serviceVersion=v1 \
  --env=dev
```

可选：`--framework=spring`、`--packageFilters=...`、`--projectname=显示名`（参数名为小写，与解析逻辑一致）。

---

## 6. 数据写入说明（简要）

- **`api_interface`**：每个接口方法一条记录（路径、HTTP 方法、描述、请求/响应根类型引用等）。
- **`api_model_definition`**：按全类名聚合模型，**fields** 列为 JSON，复杂类型通过 `ref` 指向全限定类名，避免环状结构直接落库。

---

## 7. 常见问题

**Q：执行报错「请先执行到至少 compile 阶段以解析依赖」？**  
A：使用 `mvn compile` 或 `mvn package`，不要单独在尚未解析依赖的生命周期运行；或确保插件绑定在 `compile` 及之后。

**Q：解析不到 Controller？**  
A：检查 `packageFilters` / `framework` 是否与项目一致，源码是否在默认 `src/main/java` 或已配置 `sourcePaths`。

**Q：表不存在或连接失败？**  
A：先执行 `schema.sql`，并核对 `dbUrl`、账号权限与网络。

---

## 相关仓库

- Smart-doc：<https://github.com/smart-doc-group/smart-doc>
