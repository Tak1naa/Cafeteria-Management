package com.canteen;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/test")
public class TestController {
    @GetMapping("/hello")
    public String hello() {
        return "Programme successfully launched @" + LocalDateTime.now();
    }
}