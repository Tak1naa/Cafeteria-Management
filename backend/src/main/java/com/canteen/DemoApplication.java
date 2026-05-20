package com.canteen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Arrays;

/**
 * Spring Boot 启动类
 *
 * @SpringBootApplication 包含三个注解：
 *   - @Configuration：标记为配置类
 *   - @EnableAutoConfiguration：启用自动配置
 *   - @ComponentScan：扫描当前包及子包的组件
 *
 * @EnableAsync：启用异步方法支持（@Async 生效）
 * @EnableScheduling：启用定时任务支持（@Scheduled 生效）
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class DemoApplication {

    public static void main(String[] args) {
        var ctx = SpringApplication.run(DemoApplication.class, args);
        Environment env = ctx.getEnvironment();
        String port = env.getProperty("server.port", "8081");
        String profiles = String.join(",", Arrays.asList(env.getActiveProfiles()));
        if (profiles.isEmpty()) {
            profiles = "default";
        }

        System.out.println("========================================");
        System.out.println("食堂仿真系统启动成功！");
        System.out.println("Profile: " + profiles);
        System.out.println("用户端:   http://localhost:" + port + "/app.html");
        System.out.println("控制台:   http://localhost:" + port + "/");
        System.out.println("健康检查: http://localhost:" + port + "/health");
        if (Arrays.asList(env.getActiveProfiles()).contains("dev")) {
            System.out.println("H2 控制台: http://localhost:" + port + "/h2-console");
        }
        System.out.println("========================================");
    }
}