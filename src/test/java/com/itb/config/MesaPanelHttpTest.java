package com.itb.config;

import com.itb.controller.MesaPanelController;
import com.itb.dto.MesaPanelResponse;
import com.itb.model.Perfil;
import com.itb.model.Usuario;
import com.itb.service.CustomUserDetailsService;
import com.itb.service.JwtService;
import com.itb.service.MesaPanelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual production security chain, signed synthetic JWTs, and no database access. */
@WebMvcTest(controllers = MesaPanelController.class, properties = {
        "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "jwt.expiration=60000"
})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class, CorsConfiguration.class})
class MesaPanelHttpTest {
    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @MockBean private CustomUserDetailsService users;
    @MockBean private MesaPanelService panels;

    @BeforeEach
    void fixtures() {
        for (String role : List.of("ADMINISTRADOR", "GARCOM", "COZINHA")) {
            when(users.loadUserByUsername(email(role))).thenReturn(
                    User.withUsername(email(role)).password("synthetic-hash").roles(role).build());
        }
    }

    @Test
    void anonymousVisitorCannotReadPanel() throws Exception {
        mvc.perform(get("/api/mesas/painel")).andExpect(status().isUnauthorized());
        verifyNoInteractions(panels);
    }

    @Test
    void kitchenRoleCannotReadPanel() throws Exception {
        mvc.perform(get("/api/mesas/painel").header("Authorization", bearer("COZINHA")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(panels);
    }

    @Test
    void guestOrderCapabilityCannotReadPanel() throws Exception {
        mvc.perform(get("/api/mesas/painel").header("Authorization", "Bearer " + jwt.generateOrderToken(42L)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(panels);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMINISTRADOR", "GARCOM"})
    void allowedStaffReceivesOnlyThePanelContract(String role) throws Exception {
        when(panels.getPanel()).thenReturn(List.of(new MesaPanelResponse(1L, 1, "Ocupada",
                List.of(new MesaPanelResponse.OrderSummary(42L, "Entregue", new BigDecimal("25.00"), false)))));

        mvc.perform(get("/api/mesas/painel").header("Authorization", bearer(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].number").value(1))
                .andExpect(jsonPath("$[0].status").value("Ocupada"))
                .andExpect(jsonPath("$[0].orders[0].id").value(42))
                .andExpect(jsonPath("$[0].orders[0].status").value("Entregue"))
                .andExpect(jsonPath("$[0].orders[0].totalValue").value(25.00))
                .andExpect(jsonPath("$[0].orders[0].paid").value(false))
                .andExpect(jsonPath("$[0].qrCode").doesNotExist())
                .andExpect(jsonPath("$[0].orders[0].updatedBy").doesNotExist());
    }

    private String bearer(String role) {
        Usuario user = new Usuario();
        user.setId(1L);
        user.setEmail(email(role));
        Perfil perfil = new Perfil();
        perfil.setName(role);
        user.setPerfil(perfil);
        return "Bearer " + jwt.generateToken(user);
    }

    private String email(String role) { return role.toLowerCase(java.util.Locale.ROOT) + "@example.invalid"; }
}
