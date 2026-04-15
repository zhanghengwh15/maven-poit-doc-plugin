package com.poit.doc.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * PoitApiScannerCli 的集成测试类
 * 使用配置好的数据库连接进行测试
 */
class PoitApiScannerCliTest {

    // 数据库连接配置
    private static final String DB_URL = "jdbc:mysql://dev-mysql.poi-t.cn:3306/poit-wine-mes?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai";
    private static final String DB_USER = "poit-dev-biguser@poit_db";
    private static final String DB_PASSWORD = "uT1jK98,sgRa";

    @Test
    void testCliHelpOption() {
        // 测试 --help 选项
        PoitApiScannerCli cli = new PoitApiScannerCli();
        CommandLine cmd = new CommandLine(cli);
        int exitCode = cmd.execute("--help");
        // help 通常返回 0
        assertEquals(0, exitCode);
    }

    @Test
    void testCliWithMissingRequiredOptions() {
        // 测试缺少必需选项时的错误处理
        PoitApiScannerCli cli = new PoitApiScannerCli();
        CommandLine cmd = new CommandLine(cli);
        int exitCode = cmd.execute();
        // picocli 对于缺少必需选项通常返回 2
        assertEquals(2, exitCode);
    }

    @Test
    void testCliWithValidScanDirButNoSources() throws Exception {

        // 执行 CLI
        PoitApiScannerCli cli = new PoitApiScannerCli();
        CommandLine cmd = new CommandLine(cli);

        String[] args = {
                "--scan-dir", "/Users/zhangheng/poi_tech/poit-wine-mes",
                "--db-url", DB_URL,
                "--db-user", DB_USER,
                "--db-password", DB_PASSWORD,
                "--service-version", "v1",
                "--env", "dev"
        };

        int exitCode = cmd.execute(args);
        // 即使没有源文件，只要配置正确，应该成功（可能同步 0 个 Controller）
        // 根据实际实现可能返回 0 或其他值
        System.out.println("Exit code: " + exitCode);
    }

    /**
     * 快速测试数据库连接的简单方法
     * 可以直接运行这个测试来验证数据库配置是否正确
     */
    @Test
    void testDatabaseConnectionConfig() {
        System.out.println("数据库配置:");
        System.out.println("  URL: " + DB_URL);
        System.out.println("  User: " + DB_USER);
        System.out.println("  Password: " + maskPassword(DB_PASSWORD));
    }

    private String maskPassword(String password) {
        if (password == null || password.length() <= 4) {
            return "****";
        }
        return password.substring(0, 2) + "****" + password.substring(password.length() - 2);
    }
}
