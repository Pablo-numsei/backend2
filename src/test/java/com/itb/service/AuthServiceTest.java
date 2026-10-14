package com.itb.service;

import com.itb.dto.LoginRequest;
import com.itb.dto.LoginResponse;
import com.itb.model.Perfil;
import com.itb.model.Usuario;
import com.itb.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @InjectMocks private AuthService authService;

    private Usuario user;

    @BeforeEach
    void setUp() {
        Perfil profile = new Perfil();
        profile.setId(2L);
        profile.setName("GARCOM");
        user = new Usuario();
        user.setId(8L);
        user.setName("Equipe de teste");
        user.setEmail("staff@tablehub.test");
        user.setPerfil(profile);
        user.setActive(true);
        user.setPasswordHash("synthetic-password-hash");
    }

    @Test
    void missingUserStillChecksDummyHashAndReturnsGenericFailure() {
        when(usuarioRepository.findByEmailIgnoreCase("unknown@tablehub.test")).thenReturn(Optional.empty());

        BadCredentialsException error = assertThrows(BadCredentialsException.class,
                () -> authService.login(new LoginRequest("unknown@tablehub.test", "test-password")));

        assertEquals("E-mail ou senha inválidos", error.getMessage());
        var hash = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).matches(eq("test-password"), hash.capture());
        assertTrue(hash.getValue().startsWith("$2"));
        verifyNoInteractions(jwtService);
    }

    @Test
    void missingUserCannotAuthenticateEvenIfEncoderReportsMatch() {
        when(usuarioRepository.findByEmailIgnoreCase("unknown@tablehub.test")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(eq("test-password"), anyString())).thenReturn(true);

        assertThrows(BadCredentialsException.class,
                () -> authService.login(new LoginRequest("unknown@tablehub.test", "test-password")));
        verifyNoInteractions(jwtService);
    }

    @Test
    void incorrectPasswordReturnsSameGenericFailure() {
        when(usuarioRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", user.getPasswordHash())).thenReturn(false);

        BadCredentialsException error = assertThrows(BadCredentialsException.class,
                () -> authService.login(new LoginRequest(user.getEmail(), "wrong-password")));

        assertEquals("E-mail ou senha inválidos", error.getMessage());
        verifyNoInteractions(jwtService);
    }

    @Test
    void inactiveUserIsRejectedAfterPasswordCheckWithSameFailure() {
        user.setActive(false);
        when(usuarioRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("test-password", user.getPasswordHash())).thenReturn(true);

        BadCredentialsException error = assertThrows(BadCredentialsException.class,
                () -> authService.login(new LoginRequest(user.getEmail(), "test-password")));

        assertEquals("E-mail ou senha inválidos", error.getMessage());
        verify(passwordEncoder).matches("test-password", user.getPasswordHash());
        verifyNoInteractions(jwtService);
    }

    @Test
    void activeUserWithCorrectPasswordReceivesOnlyItsIdentityAndToken() {
        when(usuarioRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("test-password", user.getPasswordHash())).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("synthetic-staff-token");

        LoginResponse response = authService.login(new LoginRequest(user.getEmail(), "test-password"));

        assertEquals(user.getId(), response.id());
        assertEquals(user.getName(), response.nome());
        assertEquals(user.getEmail(), response.email());
        assertEquals(2L, response.perfilId());
        assertEquals("GARCOM", response.perfil());
        assertEquals("synthetic-staff-token", response.token());
        verify(jwtService).generateToken(user);
    }

    @Test
    void serviceTrimsEmailBeforeRepositoryLookup() {
        when(usuarioRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("test-password", user.getPasswordHash())).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("synthetic-staff-token");

        authService.login(new LoginRequest(" staff@tablehub.test ", "test-password"));

        verify(usuarioRepository).findByEmailIgnoreCase("staff@tablehub.test");
    }

    @Test
    void asciiPasswordBeyondBcryptLimitIsRejectedBeforeRepositoryOrEncoder() {
        assertThrows(BadCredentialsException.class,
                () -> authService.login(new LoginRequest(user.getEmail(), "a".repeat(73))));

        verifyNoInteractions(usuarioRepository, passwordEncoder, jwtService);
    }

    @Test
    void utf8PasswordBeyond72BytesIsRejectedEvenBelow72Characters() {
        assertThrows(BadCredentialsException.class,
                () -> authService.login(new LoginRequest(user.getEmail(), "é".repeat(37))));

        verifyNoInteractions(usuarioRepository, passwordEncoder, jwtService);
    }

    @Test
    void passwordWithExactly72Utf8BytesReachesCredentialCheck() {
        when(usuarioRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class,
                () -> authService.login(new LoginRequest(user.getEmail(), "é".repeat(36))));

        verify(usuarioRepository).findByEmailIgnoreCase(user.getEmail());
        verify(passwordEncoder).matches(eq("é".repeat(36)), anyString());
    }
}
