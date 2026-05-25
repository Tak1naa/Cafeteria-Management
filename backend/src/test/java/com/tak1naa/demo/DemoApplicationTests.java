package com.tak1naa.demo;

import com.canteen.DemoApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = DemoApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "security.api-key=test-key")
class DemoApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void hello() {
        System.out.println("hello springboot!");
    }
}
