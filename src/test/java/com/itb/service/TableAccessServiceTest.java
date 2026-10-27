package com.itb.service;

import com.itb.dto.TableContextResponse;
import com.itb.exception.ResourceNotFoundException;
import com.itb.model.Mesa;
import com.itb.model.Perfil;
import com.itb.model.Usuario;
import com.itb.repository.MesaRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TableAccessServiceTest {
    private static final byte[] TEST_KEY = "tablehub-context-unit-test-key-32!".getBytes(StandardCharsets.UTF_8);
    @Mock private MesaRepository mesaRepository;
    @Mock private CurrentUserService currentUser;
    private JwtService jwt;
    private TableAccessService tableAccess;
    private Mesa mesa;

    @BeforeEach
    void setUp() {
        jwt = new JwtService();
        ReflectionTestUtils.setField(jwt, "secret", Base64.getEncoder().encodeToString(TEST_KEY));
        ReflectionTestUtils.setField(jwt, "expiration", 60000L);
        ReflectionTestUtils.setField(jwt, "orderExpiration", 60000L);
        tableAccess = new TableAccessService(mesaRepository, jwt, currentUser);
        mesa = new Mesa();
        mesa.setId(3L);
        mesa.setNumber(7);
        mesa.setActive(true);
        mesa.setQrCode("synthetic-server-version-one");
    }

    @Test
    void validQrExchangesForTwoHourCapabilityOfActualMesaId() throws Exception {
        stubActiveMesa();

        TableContextResponse context = tableAccess.exchangeQr(qrToken());

        assertEquals(3L, context.mesa().id());
        assertEquals(7, context.mesa().number());
        long seconds = Duration.between(Instant.now(), context.expiresAt()).toSeconds();
        assertTrue(seconds >= 7195 && seconds <= 7200);
        assertNotNull(jwt.readTableAccessToken(context.tableToken()));
        assertFalse(jwt.isTokenValid(context.tableToken()));
        assertFalse(jwt.isOrderTokenValid(context.tableToken(), 3L));
    }

    @Test
    void authenticatedCustomerCanReceiveShortLivedContextByActiveTableNumber() {
        when(mesaRepository.findByNumberAndActiveTrue(7)).thenReturn(Optional.of(mesa));

        TableContextResponse context = tableAccess.exchangeByNumber(7);

        assertEquals(3L, context.mesa().id());
        assertEquals(7, context.mesa().number());
        assertNotNull(jwt.readTableAccessToken(context.tableToken()));
        assertTrue(context.expiresAt().isAfter(Instant.now()));
    }

    @Test
    void unknownManualTableNumberIsRejected() {
        when(mesaRepository.findByNumberAndActiveTrue(99)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> tableAccess.exchangeByNumber(99));
    }

    @Test
    void validTableCapabilityPermitsWriteOnlyForItsMesa() throws Exception {
        stubActiveMesa();

        assertSame(mesa, tableAccess.requireWriteAccess(3L, accessToken()));
        assertThrows(AccessDeniedException.class, () -> tableAccess.requireWriteAccess(4L, accessToken()));
        verify(mesaRepository, never()).findByIdAndActiveTrue(4L);
    }

    @Test
    void permanentQrCannotBeUsedDirectlyAsWriteCapability() throws Exception {
        assertThrows(AccessDeniedException.class, () -> tableAccess.requireWriteAccess(3L, qrToken()));
        verifyNoInteractions(mesaRepository);
    }

    @Test
    void shortCapabilityCannotBeExchangedAsPermanentQr() throws Exception {
        assertThrows(AccessDeniedException.class, () -> tableAccess.exchangeQr(accessToken()));
        verifyNoInteractions(mesaRepository);
    }

    @Test
    void orderCapabilityCannotBeUsedAsTableContext() {
        String orderToken = jwt.generateOrderToken(3L);

        assertThrows(AccessDeniedException.class, () -> tableAccess.exchangeQr(orderToken));
        assertThrows(AccessDeniedException.class, () -> tableAccess.requireWriteAccess(3L, orderToken));
        verifyNoInteractions(mesaRepository);
    }

    @Test
    void staffJwtCannotBePassedAsClientQrOrTableCapability() {
        Perfil perfil = new Perfil();
        perfil.setName("ADMINISTRADOR");
        Usuario user = new Usuario();
        user.setId(1L);
        user.setEmail("synthetic-staff@tablehub.test");
        user.setPerfil(perfil);
        String staffToken = jwt.generateToken(user);

        assertThrows(AccessDeniedException.class, () -> tableAccess.exchangeQr(staffToken));
        assertThrows(AccessDeniedException.class, () -> tableAccess.requireWriteAccess(3L, staffToken));
        verifyNoInteractions(mesaRepository);
    }

    @Test
    void absentTableCapabilityDoesNotLookUpMesa() {
        assertThrows(AccessDeniedException.class, () -> tableAccess.requireWriteAccess(3L, null));
        verifyNoInteractions(mesaRepository);
    }

    @Test
    void tamperedQrDoesNotLookUpMesa() throws Exception {
        String[] parts = qrToken().split("\\.");
        parts[2] = (parts[2].charAt(0) == 'A' ? 'B' : 'A') + parts[2].substring(1);

        assertThrows(AccessDeniedException.class, () -> tableAccess.exchangeQr(String.join(".", parts)));
        verifyNoInteractions(mesaRepository);
    }

    @Test
    void expiredTableCapabilityDoesNotReachMesaLookup() throws Exception {
        String expired = Jwts.builder().subject("table:3").claim("purpose", "table-access")
                .claim("mesaId", 3L).claim("qrVersion", version())
                .expiration(new Date(System.currentTimeMillis() - 60000))
                .signWith(Keys.hmacShaKeyFor(TEST_KEY)).compact();

        assertThrows(AccessDeniedException.class, () -> tableAccess.requireWriteAccess(3L, expired));
        verifyNoInteractions(mesaRepository);
    }

    @Test
    void tableCapabilityWithoutExpirationIsRejected() throws Exception {
        String noExpiry = Jwts.builder().subject("table:3").claim("purpose", "table-access")
                .claim("mesaId", 3L).claim("qrVersion", version())
                .signWith(Keys.hmacShaKeyFor(TEST_KEY)).compact();

        assertThrows(AccessDeniedException.class, () -> tableAccess.requireWriteAccess(3L, noExpiry));
        verifyNoInteractions(mesaRepository);
    }

    @Test
    void deactivatedMesaRevokesQrAndExistingTableCapability() throws Exception {
        String qr = qrToken();
        String context = accessToken();
        mesa.setActive(false);
        stubActiveMesa();

        assertThrows(AccessDeniedException.class, () -> tableAccess.exchangeQr(qr));
        assertThrows(AccessDeniedException.class, () -> tableAccess.requireWriteAccess(3L, context));
    }

    @Test
    void changedVersionRevokesQrAndExistingTableCapability() throws Exception {
        String qr = qrToken();
        String context = accessToken();
        mesa.setQrCode("synthetic-server-version-two");
        stubActiveMesa();

        assertThrows(AccessDeniedException.class, () -> tableAccess.exchangeQr(qr));
        assertThrows(AccessDeniedException.class, () -> tableAccess.requireWriteAccess(3L, context));
    }

    @Test
    void activeStaffCanWriteWithoutClientCapabilityButStillNeedsActiveMesa() {
        when(currentUser.isStaff()).thenReturn(true);
        when(currentUser.idOrNull()).thenReturn(9L);
        stubActiveMesa();

        assertSame(mesa, tableAccess.requireWriteAccess(3L, null));
    }

    @Test
    void inactiveOrUnknownStaffCannotUseRoleOnlyBypass() {
        when(currentUser.isStaff()).thenReturn(true);
        when(currentUser.idOrNull()).thenReturn(null);

        assertThrows(AccessDeniedException.class, () -> tableAccess.requireWriteAccess(3L, null));
        verifyNoInteractions(mesaRepository);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void nonPositiveStaffIdentityCannotWriteOrIssueQr(Long staffId) {
        when(currentUser.isStaff()).thenReturn(true);
        when(currentUser.hasAnyRole("ADMINISTRADOR", "GARCOM")).thenReturn(true);
        when(currentUser.idOrNull()).thenReturn(staffId);

        assertThrows(AccessDeniedException.class, () -> tableAccess.requireWriteAccess(3L, null));
        assertThrows(AccessDeniedException.class, () -> tableAccess.generateQr(3L));
        verifyNoInteractions(mesaRepository);
    }

    @Test
    void kitchenOrAnonymousCannotIssuePrintableQr() {
        assertThrows(AccessDeniedException.class, () -> tableAccess.generateQr(3L));
        verifyNoInteractions(mesaRepository);
    }

    @Test
    void authorizedQrIsStableAndCannotAuthenticateAsStaff() {
        when(currentUser.hasAnyRole("ADMINISTRADOR", "GARCOM")).thenReturn(true);
        when(currentUser.idOrNull()).thenReturn(9L);
        stubActiveMesa();

        String first = tableAccess.generateQr(3L).qrToken();
        String second = tableAccess.generateQr(3L).qrToken();

        assertEquals(first, second);
        assertNotNull(jwt.readTableQrToken(first));
        assertNull(jwt.readTableAccessToken(first));
        assertFalse(jwt.isTokenValid(first));
        assertFalse(jwt.isOrderTokenValid(first, 3L));
    }

    @Test
    void staffCannotIssueQrForMissingOrInactiveMesa() {
        when(currentUser.hasAnyRole("ADMINISTRADOR", "GARCOM")).thenReturn(true);
        when(currentUser.idOrNull()).thenReturn(9L);
        when(mesaRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> tableAccess.generateQr(3L));
    }

    private void stubActiveMesa() {
        when(mesaRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(mesa));
    }

    private String version() throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(mesa.getQrCode().getBytes(StandardCharsets.UTF_8)));
    }

    private String qrToken() throws Exception { return jwt.generateTableQrToken(3L, version()); }
    private String accessToken() throws Exception { return jwt.generateTableAccessToken(3L, version()); }
}
