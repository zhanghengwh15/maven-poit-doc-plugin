package com.poit.doc.sync;

import com.ly.doc.builder.ApiDataBuilder;
import com.ly.doc.model.ApiAllData;
import com.ly.doc.model.ApiConfig;
import com.ly.doc.model.ApiDoc;
import com.ly.doc.model.SourceCodePath;
import org.junit.jupiter.api.Test;

import java.util.List;


public class SmartDocTest {

    @Test
    public void testGenerateDoc() {
        ApiConfig config = new ApiConfig();


        // todo  对应的
        // 1. 基础配置
        config.setServerUrl("http://localhost:8080");
        config.setStrict(false); // 关闭严格模式，防止因为缺少注释报错中断
        config.setProjectName("WineMES");
        // 项目根目录
        config.setFramework("spring");
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
        config.setBaseDir(basePath);
        config.setCodePath(basePath);
        // 生成 HTML 格式
        ApiAllData data = ApiDataBuilder.getApiData(config);

        List<ApiDoc> controllers = ApiDocSupport.flattenControllerDocs(data.getApiDocList());

        // 对应的转换的实体类。


        System.out.println("生成完毕！耗时: " + (System.currentTimeMillis() - start) + "ms");
    }
}