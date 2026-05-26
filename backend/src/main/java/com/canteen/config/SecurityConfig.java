package com.canteen.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Value("${security.api-key:changeme-dev-key}")
    private String apiKey;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/health", "/h2-console/**", "/ws/**").permitAll()
                        .requestMatchers("/", "/index.html", "/app.html", "/dashboard/**", "/app/**",
                                "/css/**", "/js/**", "/img/**", "/favicon.ico",
                                "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/frontend/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )
                .addFilterBefore(apiKeyFilter(), UsernamePasswordAuthenticationFilter.class)
                .headers(headers -> headers.frameOptions(fo -> fo.sameOrigin()));

        return http.build();
    }

    private OncePerRequestFilter apiKeyFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {
                String path = request.getRequestURI();
                if (!path.startsWith("/api/") || path.startsWith("/api/frontend/")) {
                    filterChain.doFilter(request, response);
                    return;
                }

                String requestKey = request.getHeader("X-API-Key");
                if (apiKey.equals(requestKey)) {
                    filterChain.doFilter(request, response);
                    return;
                }

                log.warn("未授权的 API 访问: {} from {}", path, request.getRemoteAddr());
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"unauthorized\",\"message\":\"Missing or invalid X-API-Key header\"}");
            }
        };
    }
}
