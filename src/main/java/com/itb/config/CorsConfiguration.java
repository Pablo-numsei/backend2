package com.itb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfiguration {

    private final String[] allowedOrigins;

    public CorsConfiguration(
            @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:8686}")
            String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {

        return new WebMvcConfigurer() {

            @Override
            public void addCorsMappings(CorsRegistry registry) {

                registry.addMapping("/**")
                        .allowedOrigins(allowedOrigins)
                        .allowedMethods(
                                "GET",
                                "POST",
                                "PUT",
                                "PATCH",
                                "DELETE",
                                "OPTIONS"
                        )
                        .allowedHeaders("*");
            }
        };
    }
}
