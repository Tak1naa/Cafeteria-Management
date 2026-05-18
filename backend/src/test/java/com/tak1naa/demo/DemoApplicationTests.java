package com.tak1naa.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@SpringBootTest
class DemoApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    public void test(){
        System.out.println("hello springboot!");
    }

    @RestController
    @RequestMapping("/test")
    public class TestController{

        @GetMapping("/hello")
        public String hello(){
            return "Programme successfully launched @" + LocalDateTime.now();
        }
    }

}
