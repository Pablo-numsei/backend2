package com.itb.controller;

import com.itb.config.CorsConfiguration;
import com.itb.config.JwtAuthenticationFilter;
import com.itb.config.SecurityConfig;
import com.itb.dto.ClienteAuthResponse;
import com.itb.dto.ClienteLoginRequest;
import com.itb.dto.ClienteProfileResponse;
import com.itb.exception.GlobalExceptionHandler;
import com.itb.service.ClienteAuthService;
import com.itb.service.CustomUserDetailsService;
import com.itb.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ClienteAuthController.class, properties = {
        "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "jwt.expiration=60000"
})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class,
        CorsConfiguration.class, GlobalExceptionHandler.class})
class ClienteAuthHttpTest {
    @Autowired private MockMvc mvc;
    @MockBean private CustomUserDetailsService users;
    @MockBean private ClienteAuthService customerAuth;

    @Test
    void anonymousLoginIsPublicButStaffRoutesRemainPrivate() throws Exception {
        when(customerAuth.login(any(ClienteLoginRequest.class)))
                .thenReturn(new ClienteAuthResponse(27L, "cliente.tcc", "token-de-cliente"));
        mvc.perform(post("/api/clientes/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nomeUsuario\":\"cliente.tcc\",\"pin\":\"0472\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-de-cliente"))
                .andExpect(jsonPath("$.perfil").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.pin").doesNotExist());
        mvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
    }

    @Test
    void customerProfileReadsCustomerTokenOnly() throws Exception {
        when(customerAuth.profile("Bearer customer.jwt.token"))
                .thenReturn(new ClienteProfileResponse(27L, "cliente.tcc"));
        mvc.perform(get("/api/clientes/me").header("Authorization", "Bearer customer.jwt.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeUsuario").value("cliente.tcc"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void invalidLoginBodyReturns400() throws Exception {
        mvc.perform(post("/api/clientes/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nomeUsuario\":\"invalido\",\"pin\":\"123\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(customerAuth);
    }
}
