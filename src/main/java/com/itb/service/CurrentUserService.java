package com.itb.service;

import com.itb.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class CurrentUserService {
    private final UsuarioRepository usuarioRepository;
    public boolean hasAnyRole(String... roles) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && auth.getAuthorities().stream()
                .anyMatch(authority -> Arrays.stream(roles).anyMatch(role -> authority.getAuthority().equals("ROLE_" + role)));
    }
    public boolean isStaff() { return hasAnyRole("ADMINISTRADOR", "GARCOM", "COZINHA"); }
    public Long idOrNull() {
        if (!isStaff()) return null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return usuarioRepository.findByEmailIgnoreCase(auth.getName())
                .filter(user -> Boolean.TRUE.equals(user.getActive()))
                .map(com.itb.model.Usuario::getId).orElse(null);
    }
}
