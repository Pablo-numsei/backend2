package com.itb.controller;

import com.itb.config.CorsConfiguration;
import com.itb.config.JwtAuthenticationFilter;
import com.itb.config.SecurityConfig;
import com.itb.dto.AtendimentoCreateRequest;
import com.itb.dto.AtendimentoResponse;
import com.itb.dto.MesaCreateRequest;
import com.itb.dto.MesaResponse;
import com.itb.dto.PedidoCreateRequest;
import com.itb.exception.GlobalExceptionHandler;
import com.itb.model.Mesa;
import com.itb.model.Cliente;
import com.itb.model.Order;
import com.itb.model.Perfil;
import com.itb.model.StatusPedido;
import com.itb.model.Usuario;
import com.itb.repository.MesaRepository;
import com.itb.repository.OrderRepository;
import com.itb.repository.UsuarioRepository;
import com.itb.service.AtendimentoService;
import com.itb.service.CurrentUserService;
import com.itb.service.CustomUserDetailsService;
import com.itb.service.ClienteAuthService;
import com.itb.service.JwtService;
import com.itb.service.MesaService;
import com.itb.service.OrderAccessService;
import com.itb.service.PedidoService;
import com.itb.service.TableAccessService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {MesaController.class, OrderController.class, AtendimentoController.class}, properties = {
        "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "jwt.expiration=60000", "jwt.order-expiration=60000"
})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class, CurrentUserService.class,
        TableAccessService.class, OrderAccessService.class, CorsConfiguration.class, GlobalExceptionHandler.class})
class MesaContextHttpTest {
    private static final String TEST_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @MockBean private MesaService mesaService;
    @MockBean private MesaRepository mesaRepository;
    @MockBean private OrderRepository orderRepository;
    @MockBean private UsuarioRepository usuarioRepository;
    @MockBean private CustomUserDetailsService users;
    @MockBean private PedidoService pedidos;
    @MockBean private ClienteAuthService clienteAuth;
    @MockBean private AtendimentoService atendimentos;
    private Mesa mesa;

    @BeforeEach
    void fixtures() {
        for (String role : List.of("ADMINISTRADOR", "GARCOM", "COZINHA")) {
            Usuario user = staff(role);
            when(users.loadUserByUsername(user.getEmail())).thenReturn(
                    User.withUsername(user.getEmail()).password("synthetic-hash").roles(role).build());
            when(usuarioRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        }
        mesa = new Mesa();
        mesa.setId(3L);
        mesa.setNumber(7);
        mesa.setActive(true);
        mesa.setQrCode("synthetic-http-qr-version");
        when(mesaRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(mesa));
        when(mesaService.listActive()).thenReturn(List.of(MesaResponse.from(mesa)));
        when(mesaService.getActive(3L)).thenReturn(MesaResponse.from(mesa));
        Order order = new Order();
        order.setId(42L);
        order.setMesa(mesa);
        order.setTotalValue(new BigDecimal("25.00"));
        order.setConfirmed(true);
        StatusPedido status = new StatusPedido();
        status.setName("Recebido");
        status.setFlowOrder(1);
        order.setStatus(status);
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));
        when(pedidos.criarPedido(any(PedidoCreateRequest.class))).thenReturn(order);
        Cliente cliente = new Cliente();
        cliente.setId(27L);
        cliente.setUsername("cliente.tcc");
        when(clienteAuth.requireCustomer("synthetic-customer-session")).thenReturn(cliente);
        when(atendimentos.criar(any(AtendimentoCreateRequest.class))).thenAnswer(invocation -> {
            AtendimentoCreateRequest request = invocation.getArgument(0);
            return new AtendimentoResponse(8L, request.mesaId(), 7, request.pedidoId(), request.tipo(),
                    request.detalhe(), "ENVIADO", LocalDateTime.now(), LocalDateTime.now());
        });
    }

    @Test
    void publicMesaListAndLookupExposeOnlyActiveSummary() throws Exception {
        mvc.perform(get("/api/mesas")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3)).andExpect(jsonPath("$[0].number").value(7))
                .andExpect(jsonPath("$[0].active").value(true)).andExpect(jsonPath("$[0].qrCode").doesNotExist())
                .andExpect(jsonPath("$[0].createdAt").doesNotExist());
        mvc.perform(get("/api/mesas/3")).andExpect(status().isOk())
                .andExpect(jsonPath("$.qrCode").doesNotExist()).andExpect(jsonPath("$.number").value(7));
    }

    @Test
    void anonymousAndKitchenCannotIssuePrintableQr() throws Exception {
        mvc.perform(get("/api/mesas/3/qr")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/mesas/3/qr").header("Authorization", bearer("COZINHA")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(mesaRepository);
    }

    @Test
    void administratorAndWaiterCanIssueQrWithoutLeakingRawVersion() throws Exception {
        for (String role : List.of("ADMINISTRADOR", "GARCOM")) {
            mvc.perform(get("/api/mesas/3/qr").header("Authorization", bearer(role)))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.mesa.id").value(3)).andExpect(jsonPath("$.mesa.number").value(7))
                    .andExpect(jsonPath("$.qrToken").isString()).andExpect(jsonPath("$.mesa.qrCode").doesNotExist());
        }
    }

    @Test
    void validPrintableQrProducesShortContextWithActualIdAndDisplayNumber() throws Exception {
        mvc.perform(post("/api/mesas/contexto").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qrToken\":\"" + qrToken() + "\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.mesa.id").value(3)).andExpect(jsonPath("$.mesa.number").value(7))
                .andExpect(jsonPath("$.tableToken").isString()).andExpect(jsonPath("$.expiresAt").isString());
    }

    @Test
    void staffOrderAndShortTokensCannotBeExchangedAsQr() throws Exception {
        for (String token : List.of(jwt.generateToken(staff("ADMINISTRADOR")), jwt.generateOrderToken(3L), tableToken())) {
            mvc.perform(post("/api/mesas/contexto").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"qrToken\":\"" + token + "\"}"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(mesaRepository);
    }

    @Test
    void blankQrIsBadRequestAndTamperedQrIsDenied() throws Exception {
        mvc.perform(post("/api/mesas/contexto").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qrToken\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/mesas/contexto").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qrToken\":\"" + tampered(qrToken()) + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void newOrderWithoutTableContextDoesNotCallCreationService() throws Exception {
        mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON).content(orderBody(3)))
                .andExpect(status().isForbidden());
        verify(pedidos, never()).criarPedido(any());
    }

    @Test
    void tableContextCannotCreateOrderOnAnotherMesa() throws Exception {
        mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON).header("X-Table-Token", tableToken())
                        .content(orderBody(4)))
                .andExpect(status().isForbidden());
        verify(pedidos, never()).criarPedido(any());
    }

    @Test
    void qrStaffAndOrderTokensCannotSubstituteForWriteContext() throws Exception {
        for (String token : List.of(qrToken(), jwt.generateToken(staff("ADMINISTRADOR")), jwt.generateOrderToken(3L))) {
            mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON).header("X-Table-Token", token)
                            .content(orderBody(3)))
                    .andExpect(status().isForbidden());
        }
        verify(pedidos, never()).criarPedido(any());
    }

    @Test
    void validTableContextCreatesOrderAndReceivesSeparateOrderCapability() throws Exception {
        mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Customer-Token", "synthetic-customer-session")
                        .header("X-Table-Token", tableToken())
                        .content(orderBody(3)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.mesa.id").value(3)).andExpect(jsonPath("$.accessToken").isString());
        verify(pedidos).criarPedido(any());
    }

    @Test
    void activeStaffRolesCanCreateOrderWithoutGuestContext() throws Exception {
        for (String role : List.of("ADMINISTRADOR", "GARCOM", "COZINHA")) {
            mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", bearer(role)).content(orderBody(3)))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void expiredContextCannotCreateOrder() throws Exception {
        String expired = Jwts.builder().subject("table:3").claim("purpose", "table-access")
                .claim("mesaId", 3L).claim("qrVersion", version())
                .expiration(new Date(System.currentTimeMillis() - 60000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET))).compact();
        mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Table-Token", expired).content(orderBody(3)))
                .andExpect(status().isForbidden());
        verify(pedidos, never()).criarPedido(any());
    }

    @Test
    void deactivationRevokesPreviouslyIssuedWriteContext() throws Exception {
        String token = tableToken();
        mesa.setActive(false);
        mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Table-Token", token).content(orderBody(3)))
                .andExpect(status().isForbidden());
        verify(pedidos, never()).criarPedido(any());
    }

    @Test
    void callWithoutOrderRequiresTableContext() throws Exception {
        mvc.perform(post("/api/atendimentos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mesaId\":3,\"tipo\":\"GARCOM\"}"))
                .andExpect(status().isForbidden());
        verify(atendimentos, never()).criar(any());
        mvc.perform(post("/api/atendimentos").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Table-Token", tableToken()).content("{\"mesaId\":3,\"tipo\":\"GARCOM\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void callWithExistingOrderKeepsOrderCapabilityInsteadOfTableContext() throws Exception {
        mvc.perform(post("/api/atendimentos").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Order-Token", jwt.generateOrderToken(42L))
                        .content("{\"mesaId\":3,\"pedidoId\":42,\"tipo\":\"CONTA\"}"))
                .andExpect(status().isCreated());
        verify(atendimentos).criar(any());
    }

    @Test
    void existingOrderCannotAuthorizeCallOnDifferentMesa() throws Exception {
        mvc.perform(post("/api/atendimentos").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Order-Token", jwt.generateOrderToken(42L))
                        .content("{\"mesaId\":4,\"pedidoId\":42,\"tipo\":\"CONTA\"}"))
                .andExpect(status().isBadRequest());
        verify(atendimentos, never()).criar(any());
    }

    @Test
    void createMesaRejectsClientIdentityQrAndActiveFlag() throws Exception {
        for (String field : List.of("id", "qrCode", "active")) {
            mvc.perform(post("/api/mesas").header("Authorization", bearer("ADMINISTRADOR"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"number\":8,\"" + field + "\":null}"))
                    .andExpect(status().isBadRequest());
        }
        verify(mesaService, never()).create(any());
    }

    @Test
    void createMesaAcceptsOnlyValidNumberForAdministrator() throws Exception {
        when(mesaService.create(any(MesaCreateRequest.class))).thenReturn(new MesaResponse(8L, 8, true));
        mvc.perform(post("/api/mesas").header("Authorization", bearer("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"number\":8}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.qrCode").doesNotExist());
        mvc.perform(post("/api/mesas").header("Authorization", bearer("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"number\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/mesas").header("Authorization", bearer("GARCOM"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"number\":8}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void browserCanPreflightTableHeaderFromAllowedOrigin() throws Exception {
        mvc.perform(options("/api/pedidos").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type,X-Table-Token"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    private String qrToken() throws Exception { return jwt.generateTableQrToken(3L, version()); }
    private String tableToken() throws Exception { return jwt.generateTableAccessToken(3L, version()); }
    private String version() throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(mesa.getQrCode().getBytes(StandardCharsets.UTF_8)));
    }
    private String orderBody(int id) {
        return "{\"mesaId\":" + id + ",\"itens\":[{\"produtoId\":8,\"quantidade\":1}]}";
    }
    private String bearer(String role) { return "Bearer " + jwt.generateToken(staff(role)); }
    private String tampered(String token) {
        String[] parts = token.split("\\.");
        parts[2] = (parts[2].charAt(0) == 'A' ? 'B' : 'A') + parts[2].substring(1);
        return String.join(".", parts);
    }
    private Usuario staff(String role) {
        Usuario user = new Usuario();
        user.setId(role.equals("ADMINISTRADOR") ? 1L : role.equals("GARCOM") ? 2L : 3L);
        user.setEmail(role.toLowerCase(java.util.Locale.ROOT) + "@tablehub.test");
        user.setActive(true);
        Perfil profile = new Perfil();
        profile.setName(role);
        user.setPerfil(profile);
        return user;
    }
}
