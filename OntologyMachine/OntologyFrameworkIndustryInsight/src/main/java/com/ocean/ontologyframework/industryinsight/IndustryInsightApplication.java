package com.ocean.ontologyframework.industryinsight;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 行业洞察本体应用（IndustryInsight）启动类。
 *
 * <p>启动即由 Spring 注册并运行 {@link IndustryInsightOntologyJobWorker} 中的全部
 * {@code @JobWorker}（激活 Profile = {@code IndustryInsightBPMN}），处理
 * {@code IndustryInsightProcess}（五看三定）流程的各环节作业。
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.ocean"})
public class IndustryInsightApplication {

    public static void main(String[] args) {
        String nativeAccess = System.getProperty("jdk.native.access");
        if (nativeAccess == null || !nativeAccess.equals("ALL-UNNAMED")) {
            System.err.println("⚠️  警告: 未检测到 --enable-native-access=ALL-UNNAMED");
            System.err.println("   请在 IDEA Run Configuration 的 VM options 中添加:");
            System.err.println("   --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow");
        }
        SpringApplication app = new SpringApplication(IndustryInsightApplication.class);
        app.setAdditionalProfiles("IndustryInsightBPMN");
        app.run(args);
    }
}
