package com.ocean.ontologyframework.tmsd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 塔架中段设计应用（TMSD, Tower Mid Section Design）启动入口。
 *
 * <p>同为 {@code @SpringBootApplication} + 全包扫描，通过
 * {@code setAdditionalProfiles("TMSDBPMN")} 激活塔架中段设计 Profile，
 * 从而装载 {@code TMSDOntologyJobWorker}（{@code @Profile("TMSDBPMN")}）。
 *
 * <p>唯一配置文件为 {@code application.yaml}（不再使用 profile 专属 yaml）；
 * 内置 Web 服务器提供配置录入网页 {@code /tmsd-config.html}。
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.ocean"})
public class TMSDApplication {

    public static void main(String[] args) {
        String nativeAccess = System.getProperty("jdk.native.access");
        if (nativeAccess == null || !nativeAccess.equals("ALL-UNNAMED")) {
            System.err.println("⚠️  警告: 未检测到 --enable-native-access=ALL-UNNAMED");
            System.err.println("   请在 IDEA Run Configuration 的 VM options 中添加:");
            System.err.println("   --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow");
        }
        SpringApplication app = new SpringApplication(TMSDApplication.class);
        app.setAdditionalProfiles("TMSDBPMN");  // 激活塔架中段设计 Profile
        app.run(args);
    }
}
