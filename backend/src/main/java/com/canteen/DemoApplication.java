package com.canteen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

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
        SpringApplication.run(DemoApplication.class, args);
        System.out.println("========================================");
        System.out.println("食堂仿真系统启动成功！");
        System.out.println("接口地址: http://localhost:8081");
        System.out.println("健康检查: http://localhost:8081/health");
        System.out.println("========================================");
    }
}