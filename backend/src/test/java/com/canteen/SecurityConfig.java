package com.canteen;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http){
        http
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()   // 允许所有请求
                )
                .csrf(AbstractHttpConfigurer::disable)  // 禁用 CSRF（开发环境）
                .httpBasic(AbstractHttpConfigurer::disable) // 禁用 HTTP Basic 认证
                .formLogin(AbstractHttpConfigurer::disable);    //禁用表单登录

        return http.build();
    }
}
