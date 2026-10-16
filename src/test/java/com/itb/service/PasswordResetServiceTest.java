package com.itb.service;

import com.itb.model.Usuario;
import com.itb.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {
    @Mock private UsuarioRepository usuarios;
    @Mock private JwtService jwt;
    @Mock private PasswordEncoder passwords;
    @Mock private JdbcTemplate jdbc;
    @Mock private ObjectProvider<JavaMailSender> mailSenders;
    @Mock private JavaMailSender sender;
    @InjectMocks private PasswordResetService service;

    private Usuario active;

    @BeforeEach
    void init() {
        ReflectionTestUtils.setField(service, "mailHost", "smtp.example.test");
        ReflectionTestUtils.setField(service, "from", "noreply@example.test");
        ReflectionTestUtils.setField(service, "frontendUrl", "https://tablehub.example.test");
        active = new Usuario();
        active.setId(9L);
        active.setEmail("staff@example.test");
        active.setPasswordHash("synthetic-existing-hash");
        active.setActive(true);
    }

    @Test
    void unknownEmailDoesNotSendAndDoesNotRevealAccountStatus() {
        when(mailSenders.getIfAvailable()).thenReturn(sender);
        when(usuarios.findByEmailIgnoreCase("missing@example.test")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.request("MISSING@example.test"));
        verifyNoInteractions(jwt, sender);
    }

    @Test
    void activeAccountReceivesLinkWithTemporaryTokenInFragment() {
        when(mailSenders.getIfAvailable()).thenReturn(sender);
        when(usuarios.findByEmailIgnoreCase("staff@example.test")).thenReturn(Optional.of(active));
        when(jwt.generatePasswordResetToken(active)).thenReturn("safe.synthetic.token");

        service.request("STAFF@example.test");

        var captor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        assertTrue(captor.getValue().getText().contains(
                "https://tablehub.example.test/redefinir-senha#token=safe.synthetic.token"));
    }

    @Test
    void noSmtpIsReportedBeforeUserLookup() {
        when(mailSenders.getIfAvailable()).thenReturn(null);
        var e = assertThrows(ResponseStatusException.class, () -> service.request("staff@example.test"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
        verifyNoInteractions(usuarios);
    }

    @Test
    void expiredTokenCannotChangePassword() {
        when(jwt.readPasswordResetToken("expired")).thenReturn(null);
        var e = assertThrows(ResponseStatusException.class,
                () -> service.confirm("expired", "strong-new-passphrase"));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        verifyNoInteractions(jdbc);
    }

    @Test
    void validTokenUpdatesPasswordUsingCompareAndSwap() {
        var claims = new JwtService.PasswordResetClaims(9L, active.getEmail(),
                JwtService.passwordVersion(active.getPasswordHash()));
        when(jwt.readPasswordResetToken("valid")).thenReturn(claims);
        when(usuarios.findById(9L)).thenReturn(Optional.of(active));
        when(passwords.encode("strong-new-passphrase")).thenReturn("synthetic-new-hash");
        when(jdbc.update(anyString(), eq("synthetic-new-hash"), eq(9L),
                eq("synthetic-existing-hash"))).thenReturn(1);

        service.confirm("valid", "strong-new-passphrase");

        verify(jdbc).update(anyString(), eq("synthetic-new-hash"), eq(9L),
                eq("synthetic-existing-hash"));
    }

    @Test
    void tokenAlreadyUsedFailsAtomicUpdate() {
        var claims = new JwtService.PasswordResetClaims(9L, active.getEmail(),
                JwtService.passwordVersion(active.getPasswordHash()));
        when(jwt.readPasswordResetToken("valid")).thenReturn(claims);
        when(usuarios.findById(9L)).thenReturn(Optional.of(active));
        when(passwords.encode(anyString())).thenReturn("synthetic-new-hash");
        var e = assertThrows(ResponseStatusException.class,
                () -> service.confirm("valid", "strong-new-passphrase"));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }
}
