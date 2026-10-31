package com.ocean.ontologyframework.ziweidoushu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 紫微斗数（陈希夷紫微斗数推命）本体应用启动类。
 *
 * <p>启动本应用即由 Spring 注册并运行 {@code ZiWeiDouShuOntologyJobWorker} 的全部 {@code @JobWorker}
 * （即以 {@code ZiWeiDouShuBPMN} Profile 激活 Worker），承接
 * {@code ontology/bpmn/ziweidoushuProcess.bpmn}（{@code ZiWeiDouShuChartProcess}）各 serviceTask 的作业。
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.ocean"})
public class ZiWeiDouShuApplication {

    public static void main(String[] args) {
        String nativeAccess = System.getProperty("jdk.native.access");
        if (nativeAccess == null || !nativeAccess.equals("ALL-UNNAMED")) {
            System.err.println("⚠️  警告: 未检测到 --enable-native-access=ALL-UNNAMED");
            System.err.println("   请在 IDEA Run Configuration 的 VM options 中添加:");
            System.err.println("   --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow");
        }
        SpringApplication app = new SpringApplication(ZiWeiDouShuApplication.class);
        app.setAdditionalProfiles("ZiWeiDouShuBPMN");
        app.run(args);
    }
}
