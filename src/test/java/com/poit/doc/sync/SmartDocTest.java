package com.poit.doc.sync;

import com.ly.doc.builder.HtmlApiDocBuilder;
import com.ly.doc.model.ApiConfig;
import com.ly.doc.model.SourceCodePath;
import org.junit.jupiter.api.Test;

public class SmartDocTest {

    @Test
    public void testGenerateDoc() {
        ApiConfig config = new ApiConfig();

        // 1. 基础配置
        config.setServerUrl("http://localhost:8080");
        config.setStrict(false); // 关闭严格模式，防止因为缺少注释报错中断
        config.setAllInOne(true);

        // 将生成的文档放到 app 模块的 target 目录下
        config.setOutPath("/Users/zhangheng/poi_tech/poit-wine-mes/poit-wine-mes-app/target/doc");

        // 项目根目录
        String basePath = "/Users/zhangheng/poi_tech/poit-wine-mes/";


        // 2. 核心：将所有包含 Java 代码的模块 src/main/java 都加进来！
        config.setSourceCodePaths(
                // Controller 所在模块 (必须)
                SourceCodePath.builder().setPath(basePath + "poit-wine-mes-app/src/main/java"),

                // API 模块：通常存放对外暴露的接口、DTO、VO (极其重要，否则文档没参数)
                SourceCodePath.builder().setPath(basePath + "poit-wine-mes-api/src/main/java"),

                // BIZ 模块：业务逻辑和内部实体
                SourceCodePath.builder().setPath(basePath + "poit-wine-mes-biz/src/main/java"),

                // DAO 模块：数据库实体 (如果有直接返回数据库实体的情况也需要加)
                SourceCodePath.builder().setPath(basePath + "poit-wine-mes-dao/src/main/java")
        );

        // 3. 执行生成
        System.out.println("开始扫描多模块代码并生成文档...");
        long start = System.currentTimeMillis();

        // 生成 HTML 格式
        HtmlApiDocBuilder.buildApiDoc(config);

        System.out.println("生成完毕！耗时: " + (System.currentTimeMillis() - start) + "ms");
        System.out.println("请在浏览器打开: " + config.getOutPath() + "/index.html");
    }
}