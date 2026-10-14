package com.itb.service;

import com.itb.model.Usuario;
import com.itb.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {
    @Mock private UsuarioRepository usuarioRepository;
    @InjectMocks private CurrentUserService currentUser;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anonymousRequestHasNoStaffRoleOrAuthorId() {
        assertFalse(currentUser.isStaff());
        assertNull(currentUser.idOrNull());
        verifyNoInteractions(usuarioRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMINISTRADOR", "GARCOM", "COZINHA"})
    void supportedStaffRolesAreRecognized(String role) {
        authenticate(role);

        assertTrue(currentUser.isStaff());
        assertTrue(currentUser.hasAnyRole(role));
    }

    @Test
    void unrelatedRoleCannotSupplyStaffAuthorId() {
        authenticate("CLIENTE");

        assertFalse(currentUser.isStaff());
        assertNull(currentUser.idOrNull());
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    void unauthenticatedPrincipalCannotUseStaffAuthorities() {
        var authentication = authenticate("ADMINISTRADOR");
        authentication.setAuthenticated(false);

        assertFalse(currentUser.isStaff());
        assertNull(currentUser.idOrNull());
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    void authorIdComesFromActiveDatabaseUserMatchingPrincipal() {
        authenticate("GARCOM");
        Usuario user = new Usuario();
        user.setId(73L);
        user.setActive(true);
        when(usuarioRepository.findByEmailIgnoreCase("staff@tablehub.test")).thenReturn(Optional.of(user));

        assertEquals(73L, currentUser.idOrNull());
        verify(usuarioRepository).findByEmailIgnoreCase("staff@tablehub.test");
    }

    @Test
    void inactiveUserCannotSupplyAuthorId() {
        authenticate("GARCOM");
        Usuario user = new Usuario();
        user.setId(73L);
        user.setActive(false);
        when(usuarioRepository.findByEmailIgnoreCase("staff@tablehub.test")).thenReturn(Optional.of(user));

        assertNull(currentUser.idOrNull());
    }

    @Test
    void missingDatabaseUserCannotSupplyAuthorId() {
        authenticate("ADMINISTRADOR");
        when(usuarioRepository.findByEmailIgnoreCase("staff@tablehub.test")).thenReturn(Optional.empty());

        assertNull(currentUser.idOrNull());
    }

    private UsernamePasswordAuthenticationToken authenticate(String role) {
        var authentication = new UsernamePasswordAuthenticationToken("staff@tablehub.test", null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return authentication;
    }
}
