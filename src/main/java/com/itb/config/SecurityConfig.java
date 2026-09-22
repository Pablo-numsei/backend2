package com.itb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.http.HttpMethod;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) {
        this.jwtAuthenticationFilter =
                jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {

        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http

                .csrf(csrf ->
                        csrf.disable()
                )

                .cors(cors -> {
                })

                .formLogin(form ->
                        form.disable()
                )

                .httpBasic(basic ->
                        basic.disable()
                )

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )
.authorizeHttpRequests(auth -> auth

        .requestMatchers(
                HttpMethod.POST,
                "/api/auth/login"
        ).permitAll()

        // TEMPORÁRIO: permite criar o primeiro usuário
        .requestMatchers(
                HttpMethod.POST,
                "/api/usuarios"
        ).permitAll()

        .requestMatchers(
                "/ws/**"
        ).permitAll()
                  .requestMatchers(
                "/api/usuarios/**"
      ).hasRole("ADMINISTRADOR")


       // Produtos podem ser visualizados pelo cliente
        .requestMatchers(
                HttpMethod.GET,
                "/api/v1/produtos/**"
        ).permitAll()

        // Criar produto: administrador
        .requestMatchers(
                HttpMethod.POST,
                "/api/v1/produtos/**"
        ).hasRole("ADMINISTRADOR")

        // Editar produto: administrador
        .requestMatchers(
                HttpMethod.PUT,
                "/api/v1/produtos/**"
        ).hasRole("ADMINISTRADOR")

        // Excluir produto: administrador
        .requestMatchers(
                HttpMethod.DELETE,
                "/api/v1/produtos/**"
        ).hasRole("ADMINISTRADOR")

        // Mesas - leitura pública temporariamente
        .requestMatchers(
                HttpMethod.GET,
                "/api/mesas/**"
        ).permitAll()

        // Criar mesa - somente administrador
        .requestMatchers(
                HttpMethod.POST,
                "/api/mesas/**"
        ).hasRole("ADMINISTRADOR")

        // Editar mesa - somente administrador
        .requestMatchers(
                HttpMethod.PUT,
                "/api/mesas/**"
        ).hasRole("ADMINISTRADOR")

        // Desativar mesa - somente administrador
        .requestMatchers(
                HttpMethod.DELETE,
                "/api/mesas/**"
        ).hasRole("ADMINISTRADOR")

        .anyRequest().permitAll()
)
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}