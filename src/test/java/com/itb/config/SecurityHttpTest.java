package com.itb.config;

import com.itb.controller.OrderController;
import com.itb.controller.PaymentController;
import com.itb.controller.UsuarioController;
import com.itb.dto.AvancarStatusRequest;
import com.itb.dto.PedidoCreateRequest;
import com.itb.exception.GlobalExceptionHandler;
import com.itb.model.*;
import com.itb.repository.OrderRepository;
import com.itb.repository.MesaRepository;
import com.itb.repository.PerfilRepository;
import com.itb.repository.UsuarioRepository;
import com.itb.service.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises the production filter chain and signed JWTs with synthetic repository fixtures. */
@WebMvcTest(controllers = {OrderController.class, UsuarioController.class, PaymentController.class}, properties = {
        // Deliberately public, synthetic key used only inside this test context.
        "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "jwt.expiration=60000", "jwt.order-expiration=60000"
})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class,
        CurrentUserService.class, OrderAccessService.class, TableAccessService.class,
        CorsConfiguration.class, GlobalExceptionHandler.class})
class SecurityHttpTest {
    private static final String TEST_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockBean private CustomUserDetailsService users;
    @MockBean private UsuarioRepository usuarioRepository;
    @MockBean private PerfilRepository perfilRepository;
    @MockBean private OrderRepository orderRepository;
    @MockBean private MesaRepository mesaRepository;
    @MockBean private PedidoService pedidos;
    @MockBean private WhatsAppNotificationService whatsApp;
    @MockBean private PaymentService payments;
    @MockBean private EntityManager entityManager;
    private Order order;

    @BeforeEach
    void fixtures() {
        for (String role : List.of("ADMINISTRADOR", "GARCOM", "COZINHA")) {
            Usuario user = staff(role);
            when(users.loadUserByUsername(user.getEmail()))
                    .thenReturn(User.withUsername(user.getEmail()).password("synthetic-hash").roles(role).build());
            when(usuarioRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        }
        order = new Order();
        order.setId(42L);
        order.setTotalValue(new BigDecimal("25.00"));
        order.setConfirmed(true);
        Mesa mesa = new Mesa();
        mesa.setId(3L);
        mesa.setNumber(3);
        mesa.setQrCode("synthetic-private-qr");
        mesa.setActive(true);
        when(mesaRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(mesa));
        order.setMesa(mesa);
        StatusPedido status = new StatusPedido();
        status.setName("Recebido");
        status.setFlowOrder(1);
        order.setStatus(status);
        order.setUpdatedBy(staff("GARCOM"));
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));
        when(orderRepository.findByDeletedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(order));
        when(pedidos.avancarStatus(eq(42L), any(AvancarStatusRequest.class))).thenReturn(order);
        when(pedidos.criarPedido(any(PedidoCreateRequest.class))).thenReturn(order);
        Payment payment = payment();
        when(payments.confirmPayment(eq(42L), anyString())).thenReturn(payment);
        when(payments.cancelPayment(eq(42L), anyString())).thenReturn(payment);
    }

    @Test
    void anonymousSignupIsDeniedBeforeRepositoryAccess() throws Exception {
        mvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON).content(signup()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(usuarioRepository, perfilRepository, entityManager);
    }

    @Test
    void kitchenCannotCreatePrivilegedAccounts() throws Exception {
        mvc.perform(post("/api/usuarios").header("Authorization", bearer("COZINHA"))
                        .contentType(MediaType.APPLICATION_JSON).content(signup()))
                .andExpect(status().isForbidden());
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void administratorCanCreateAccountWithHashExcludedFromResponse() throws Exception {
        Perfil perfil = new Perfil();
        perfil.setId(1L);
        perfil.setName("Administrador");
        when(perfilRepository.findById(1L)).thenReturn(Optional.of(perfil));
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenAnswer(call -> {
            Usuario saved = call.getArgument(0);
            saved.setId(77L);
            saved.setActive(true);
            return saved;
        });
        mvc.perform(post("/api/usuarios").header("Authorization", bearer("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(signup()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(77))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        ArgumentCaptor<Usuario> saved = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).saveAndFlush(saved.capture());
        assertNotEquals("synthetic-password", saved.getValue().getPasswordHash());
        assertTrue(passwordEncoder.matches("synthetic-password", saved.getValue().getPasswordHash()));
    }

    @Test
    void managementEndpointsRejectAnonymousVisitors() throws Exception {
        for (String route : List.of("/api/pedidos", "/api/pedidos/status/Recebido",
                "/api/usuarios", "/api/perfis", "/api/dashboard/resumo", "/api/atendimentos")) {
            mvc.perform(get(route)).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(orderRepository);
    }

    @Test
    void signedStaffTokenAllowsOperationalOrderList() throws Exception {
        mvc.perform(get("/api/pedidos").header("Authorization", bearer("COZINHA")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(42));
    }

    @Test
    void invalidAndExpiredStaffTokensCannotAuthenticate() throws Exception {
        mvc.perform(get("/api/pedidos").header("Authorization", "Bearer malformed.token"))
                .andExpect(status().isUnauthorized());
        String expired = Jwts.builder().subject(staff("ADMINISTRADOR").getEmail())
                .claim("purpose", "staff").expiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET))).compact();
        mvc.perform(get("/api/pedidos").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(orderRepository);
    }

    @Test
    void guestCapabilityCannotBeReusedAsStaffAuthentication() throws Exception {
        mvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + jwt.generateOrderToken(42L)))
                .andExpect(status().isUnauthorized());
        verify(users, never()).loadUserByUsername(anyString());
    }

    @Test
    void guestCanReadOnlyTheOrderCoveredByItsCapability() throws Exception {
        mvc.perform(get("/api/pedidos/42").header("X-Order-Token", jwt.generateOrderToken(42L)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.mesa.number").value(3))
                .andExpect(jsonPath("$.mesa.qrCode").doesNotExist())
                .andExpect(jsonPath("$.updatedBy").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void missingAndMismatchedCapabilitiesDenyBeforeLookingUpOrderId() throws Exception {
        mvc.perform(get("/api/pedidos/42")).andExpect(status().isForbidden());
        mvc.perform(get("/api/pedidos/42").header("X-Order-Token", jwt.generateOrderToken(43L)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(orderRepository);
    }

    @Test
    void softDeletedOrderIsNotReturnedEvenWithItsCapability() throws Exception {
        order.setDeletedAt(LocalDateTime.now());
        mvc.perform(get("/api/pedidos/42").header("X-Order-Token", jwt.generateOrderToken(42L)))
                .andExpect(status().isNotFound());
    }

    @Test
    void guestCannotApproveOrCancelPayment() throws Exception {
        for (String action : List.of("confirm", "cancel")) {
            mvc.perform(post("/api/payments/42/" + action).header("X-Order-Token", jwt.generateOrderToken(42L)))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(payments);
    }

    @Test
    void kitchenCannotApproveOrCancelPayment() throws Exception {
        for (String action : List.of("confirm", "cancel")) {
            mvc.perform(post("/api/payments/42/" + action).header("Authorization", bearer("COZINHA")))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(payments);
    }

    @Test
    void waiterAndAdministratorCanConfirmPaymentsUsingAuthenticatedIdentity() throws Exception {
        for (String role : List.of("GARCOM", "ADMINISTRADOR")) {
            mvc.perform(post("/api/payments/42/confirm").header("Authorization", bearer(role)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAGO"));
            verify(payments).confirmPayment(42L, staff(role).getEmail());
        }
    }

    @Test
    void guestCannotAdvanceKitchenStatus() throws Exception {
        mvc.perform(patch("/api/pedidos/42/status").header("X-Order-Token", jwt.generateOrderToken(42L))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"novoStatus\":\"Em preparo\"}"))
                .andExpect(status().isUnauthorized());
        verify(pedidos, never()).avancarStatus(anyLong(), any());
    }

    @Test
    void kitchenAndWaiterCannotPerformEachOthersTransitions() throws Exception {
        mvc.perform(patch("/api/pedidos/42/status").header("Authorization", bearer("GARCOM"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"novoStatus\":\"Em preparo\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/pedidos/42/status").header("Authorization", bearer("COZINHA"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"novoStatus\":\"Entregue\"}"))
                .andExpect(status().isForbidden());
        verify(pedidos, never()).avancarStatus(anyLong(), any());
    }

    @Test
    void allowedTransitionsUseServerIdentityInsteadOfForgedBodyAuthor() throws Exception {
        for (String role : List.of("COZINHA", "GARCOM", "ADMINISTRADOR")) {
            String next = role.equals("GARCOM") ? "Entregue" : "Em preparo";
            mvc.perform(patch("/api/pedidos/42/status").header("Authorization", bearer(role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"novoStatus\":\"" + next + "\",\"alteradoPor\":999}"))
                    .andExpect(status().isOk());
        }
        ArgumentCaptor<AvancarStatusRequest> requests = ArgumentCaptor.forClass(AvancarStatusRequest.class);
        verify(pedidos, times(3)).avancarStatus(eq(42L), requests.capture());
        assertEquals(List.of(3L, 2L, 1L), requests.getAllValues().stream().map(AvancarStatusRequest::alteradoPor).toList());
    }

    @Test
    void malformedJsonAndNullListItemsReturnBadRequestWithoutCreatingOrder() throws Exception {
        mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mesaId\":3,\"itens\":[null]}"))
                .andExpect(status().isBadRequest());
        verify(pedidos, never()).criarPedido(any());
    }

    @Test
    void oversizedRequestsAreRejectedBeforeOrderCreation() throws Exception {
        mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON)
                        .content("{}" + " ".repeat(262_144)))
                .andExpect(status().isPayloadTooLarge());
        verify(pedidos, never()).criarPedido(any());
    }

    @Test
    void newOrderReceivesCapabilityAndIgnoresForgedStaffId() throws Exception {
        mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Table-Token", tableContextToken())
                        .content("{\"mesaId\":3,\"criadoPor\":999,\"nomeCliente\":\"Lucas\",\"itens\":[{\"produtoId\":8,\"quantidade\":1}]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.accessToken").isString());
        ArgumentCaptor<PedidoCreateRequest> request = ArgumentCaptor.forClass(PedidoCreateRequest.class);
        verify(pedidos).criarPedido(request.capture());
        assertNull(request.getValue().criadoPor());
    }

    @Test
    void guestOrderRequiresANameAfterChoosingItems() throws Exception {
        mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Table-Token", tableContextToken())
                        .content("{\"mesaId\":3,\"itens\":[{\"produtoId\":8,\"quantidade\":1}]}"))
                .andExpect(status().isBadRequest());
        verify(pedidos, never()).criarPedido(any());
    }

    @Test
    void guestCanCancelWhatsappWithOrderScopedTokenOnly() throws Exception {
        mvc.perform(post("/api/pedidos/42/whatsapp/cancelar"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/pedidos/42/whatsapp/cancelar")
                        .header("X-Order-Token", jwt.generateOrderToken(42L)))
                .andExpect(status().isNoContent());
        verify(whatsApp).cancelConsent(42L);
    }

    @Test
    void unknownEndpointsAreDeniedByDefault() throws Exception {
        mvc.perform(get("/api/not-declared")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/not-declared").header("Authorization", bearer("ADMINISTRADOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowedOriginCanPreflightGuestHeaderAndForeignOriginIsRejected() throws Exception {
        mvc.perform(options("/api/pedidos/42").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization,X-Order-Token"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options("/api/pedidos/42").header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    private String bearer(String role) { return "Bearer " + jwt.generateToken(staff(role)); }

    private String tableContextToken() throws Exception {
        String version = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest("synthetic-private-qr".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return jwt.generateTableAccessToken(3L, version);
    }

    private Usuario staff(String role) {
        Usuario user = new Usuario();
        user.setId(role.equals("ADMINISTRADOR") ? 1L : role.equals("GARCOM") ? 2L : 3L);
        user.setEmail(role.toLowerCase(java.util.Locale.ROOT) + "@example.test");
        user.setName("Synthetic " + role);
        user.setActive(true);
        Perfil perfil = new Perfil();
        perfil.setName(role);
        user.setPerfil(perfil);
        return user;
    }

    private Payment payment() {
        Payment payment = new Payment();
        payment.setId(99L);
        payment.setOrder(order);
        payment.setAmount(order.getTotalValue());
        payment.setMethod("DINHEIRO");
        payment.setStatus("PAGO");
        return payment;
    }

    private String signup() {
        return "{\"perfilId\":1,\"nome\":\"Synthetic user\",\"email\":\"new@example.test\",\"senha\":\"synthetic-password\"}";
    }
}
