package com.itb.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Map;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public RequestRateLimitFilter requestRateLimitFilter() { return new RequestRateLimitFilter(); }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RequestRateLimitFilter> rateFilterRegistration(RequestRateLimitFilter filter) {
        FilterRegistrationBean<RequestRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
                                                   RequestRateLimitFilter rateFilter, ObjectMapper mapper) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType("application/json;charset=UTF-8");
                            mapper.writeValue(response.getWriter(), Map.of("status", 401, "message", "Autenticação necessária."));
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setContentType("application/json;charset=UTF-8");
                            mapper.writeValue(response.getWriter(), Map.of("status", 403, "message", "Acesso não permitido."));
                        }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/password-reset/request", "/api/auth/password-reset/confirm", "/api/clientes/cadastro", "/api/clientes/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/clientes/me").permitAll()
                        .requestMatchers("/api/usuarios/**", "/api/perfis/**").hasRole("ADMINISTRADOR")
                        .requestMatchers("/api/v1/categorias", "/api/v1/categorias/**").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.GET, "/api/mesas/*/qr").hasAnyRole("ADMINISTRADOR", "GARCOM")
                        .requestMatchers(HttpMethod.GET, "/api/mesas/painel").hasAnyRole("ADMINISTRADOR", "GARCOM")
                        .requestMatchers(HttpMethod.POST, "/api/mesas").hasAnyRole("ADMINISTRADOR", "GARCOM")
                        .requestMatchers(HttpMethod.POST, "/api/mesas/contexto", "/api/mesas/contexto-manual").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/produtos/**", "/api/mesas/**", "/api/status-pedidos", "/api/push/public-key").permitAll()
                        .requestMatchers("/api/v1/produtos/**", "/api/mesas/**").hasRole("ADMINISTRADOR")
                        .requestMatchers("/api/desperdicios", "/api/desperdicios/**").hasAnyRole("ADMINISTRADOR", "GARCOM", "COZINHA")
                        .requestMatchers(HttpMethod.GET, "/api/pedidos", "/api/pedidos/status/**").hasAnyRole("ADMINISTRADOR", "GARCOM", "COZINHA")
                        .requestMatchers(HttpMethod.PATCH, "/api/pedidos/*/status").hasAnyRole("ADMINISTRADOR", "GARCOM", "COZINHA")
                        .requestMatchers(HttpMethod.GET, "/api/payments").hasRole("ADMINISTRADOR")
                        // Individual reads require an order-scoped token inside their controllers.
                        .requestMatchers(HttpMethod.GET, "/api/pedidos/*", "/api/itens-pedido/pedido/*", "/api/payments/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/pedidos", "/api/payments/*/process", "/api/atendimentos", "/api/push/subscriptions").permitAll()
                        .requestMatchers("/api/payments/*/confirm", "/api/payments/*/cancel", "/api/dashboard/**", "/api/atendimentos/**", "/api/push/staff-subscriptions").hasAnyRole("ADMINISTRADOR", "GARCOM")
                        // STOMP CONNECT and inbound messages are checked by the channel interceptor.
                        .requestMatchers("/ws/**").permitAll()
                        .anyRequest().denyAll())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateFilter, JwtAuthenticationFilter.class);
        return http.build();
    }
}
