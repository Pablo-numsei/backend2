package com.itb.service;

import com.itb.model.Usuario;
import com.itb.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PasswordResetService {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final UsuarioRepository usuarios;
    private final JwtService jwt;
    private final PasswordEncoder passwords;
    private final JdbcTemplate jdbc;
    private final ObjectProvider<JavaMailSender> mailSenders;
    private final BrevoEmailService brevoEmailService;

    @Value("${app.password-reset.provider:smtp}")
    private String mailProvider;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${app.password-reset.from:}")
    private String from;

    @Value("${app.password-reset.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    public void request(String rawEmail) {
        boolean brevo = "brevo".equalsIgnoreCase(mailProvider);
        boolean smtp = mailProvider == null || mailProvider.isBlank()
                || "smtp".equalsIgnoreCase(mailProvider);

        // Validar a configuracao antes de consultar o e-mail, sem revelar existencia de contas.
        if (from == null || from.isBlank()) {
            throw unavailable();
        }
        JavaMailSender sender = null;
        if (brevo) {
            if (!brevoEmailService.isConfigured()) throw unavailable();
        } else if (smtp) {
            if (mailHost == null || mailHost.isBlank()) throw unavailable();
            sender = mailSenders.getIfAvailable();
            if (sender == null) throw unavailable();
        } else {
            throw unavailable();
        }

        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        Usuario user = usuarios.findByEmailIgnoreCase(email).orElse(null);

        // Resultado identico para e-mail inexistente ou usuario desativado.
        if (user == null || !Boolean.TRUE.equals(user.getActive())) return;

        String token = jwt.generatePasswordResetToken(user);
        String resetUrl = frontendUrl.replaceAll("/+$", "") + "/redefinir-senha#token=" + token;
        String subject = "TableHub - recuperação de senha";
        String body = "Uma recuperação de senha foi solicitada para sua conta TableHub.\n\n"
                + "Para criar sua nova senha, abra o link (válido por 15 minutos):\n"
                + resetUrl + "\n\nSe não foi você, ignore esta mensagem.\n"
                + "Nunca informe sua senha por e-mail.";

        if (brevo) {
            // Nao revelar a existencia do usuario nem o token em mensagens de erro/logs.
            if (!brevoEmailService.send(from, user.getEmail(), subject, body)) {
                log.warn("Falha na entrega do e-mail de recuperação (API HTTPS).");
            }
        } else {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(user.getEmail());
            message.setSubject(subject);
            message.setText(body);
            try {
                sender.send(message);
            } catch (MailException ex) {
                log.warn("Falha na entrega do e-mail de recuperação (SMTP).");
            }
        }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "A recuperação por e-mail não está configurada. Contate o administrador.");
    }

    @Transactional
    public void confirm(String token, String novaSenha) {
        if (novaSenha.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A senha deve ter no máximo 72 bytes UTF-8.");
        }

        JwtService.PasswordResetClaims claims = jwt.readPasswordResetToken(token);
        if (claims == null) throw invalid();

        Usuario user = usuarios.findById(claims.usuarioId()).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getActive())
                || !user.getEmail().equalsIgnoreCase(claims.email())
                || !JwtService.passwordVersion(user.getPasswordHash()).equals(claims.passwordVersion())) {
            throw invalid();
        }

        if (passwords.matches(novaSenha, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A nova senha deve ser diferente da anterior.");
        }

        String nextHash = passwords.encode(novaSenha);
        // Atomic compare-and-swap: one-time use, including simultaneous submit from multiple tabs.
        int affected = jdbc.update("""
                UPDATE dbo.Usuarios
                   SET senha_hash = ?, alterado_em = SYSDATETIME()
                 WHERE id_usuario = ? AND senha_hash = ? AND ativo = 1
                """, nextHash, user.getId(), user.getPasswordHash());
        if (affected != 1) throw invalid();
    }

    private ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Link inválido, expirado ou já utilizado. Solicite outro.");
    }
}
