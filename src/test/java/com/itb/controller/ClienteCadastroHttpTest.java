package com.itb.controller;

import com.itb.config.CorsConfiguration;
import com.itb.config.JwtAuthenticationFilter;
import com.itb.config.SecurityConfig;
import com.itb.dto.ClienteCadastroRequest;
import com.itb.dto.ClienteCadastroResponse;
import com.itb.exception.GlobalExceptionHandler;
import com.itb.service.ClienteCadastroService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ClienteCadastroController.class, properties = {
        "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "jwt.expiration=60000"
})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class,
        CorsConfiguration.class, GlobalExceptionHandler.class})
class ClienteCadastroHttpTest {
    @Autowired private MockMvc mvc;
    @MockBean private CustomUserDetailsService users;
    @MockBean private ClienteCadastroService cadastro;

    private static final String VALID = """
        {"nomeUsuario":"cliente.tcc","email":"cliente@example.test","senha":"minha-senha-forte-123"}
        """;

    @Test
    void anonymousCanRegisterWithoutAStaffRoleOrToken() throws Exception {
        when(cadastro.cadastrar(any(ClienteCadastroRequest.class)))
                .thenReturn(new ClienteCadastroResponse(7L, "cliente.tcc", "Conta criada."));
        mvc.perform(post("/api/clientes/cadastro")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nomeUsuario").value("cliente.tcc"))
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.perfil").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist());
        verify(cadastro).cadastrar(any(ClienteCadastroRequest.class));
    }

    @Test
    void invalidAndWeakCredentialsCannotBeRegistered() throws Exception {
        mvc.perform(post("/api/clientes/cadastro").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nomeUsuario\":\"a\",\"email\":\"errado\",\"senha\":\"123\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(cadastro);
    }

    @Test
    void publicSignupNeverOpensTheStaffAccountEndpoint() throws Exception {
        mvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(cadastro);
    }
}
