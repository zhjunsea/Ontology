package com.ocean.ontologyframework;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.ocean"})
public class PizzaApplication {

    public static void main(String[] args) {
        String nativeAccess = System.getProperty("jdk.native.access");
        if (nativeAccess == null || !nativeAccess.equals("ALL-UNNAMED")) {
            System.err.println("⚠️  警告: 未检测到 --enable-native-access=ALL-UNNAMED");
            System.err.println("   请在 IDEA Run Configuration 的 VM options 中添加:");
            System.err.println("   --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow");
        }
        SpringApplication app = new SpringApplication(PizzaApplication.class);
        app.setAdditionalProfiles("PizzaBPMNTest");  // 激活 Profile
        app.run(args);
    }
}