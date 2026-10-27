package com.itb.service;

import com.itb.dto.MesaQrResponse;
import com.itb.dto.MesaSummaryResponse;
import com.itb.dto.TableContextResponse;
import com.itb.exception.ResourceNotFoundException;
import com.itb.model.Mesa;
import com.itb.repository.MesaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class TableAccessService {
    private final MesaRepository mesaRepository;
    private final JwtService jwtService;
    private final CurrentUserService currentUser;

    @Transactional(readOnly = true)
    public MesaQrResponse generateQr(Long mesaId) {
        if (!currentUser.hasAnyRole("ADMINISTRADOR", "GARCOM") || !hasActiveStaffIdentity()) {
            throw denied();
        }
        Mesa mesa = activeMesa(mesaId);
        return new MesaQrResponse(MesaSummaryResponse.from(mesa),
                jwtService.generateTableQrToken(mesa.getId(), version(mesa)));
    }

    @Transactional(readOnly = true)
    public TableContextResponse exchangeQr(String qrToken) {
        JwtService.TableTokenClaims claims = jwtService.readTableQrToken(qrToken);
        if (claims == null) throw denied();
        Mesa mesa = validateCurrentMesa(claims);
        String tableToken = jwtService.generateTableAccessToken(mesa.getId(), version(mesa));
        JwtService.TableTokenClaims context = jwtService.readTableAccessToken(tableToken);
        if (context == null) throw new IllegalStateException("Não foi possível emitir o contexto da mesa.");
        return new TableContextResponse(MesaSummaryResponse.from(mesa), tableToken, context.expiresAt());
    }

    /** Emits the same short-lived table capability as QR exchange, after a customer session is validated by the controller. */
    @Transactional(readOnly = true)
    public TableContextResponse exchangeByNumber(Integer number) {
        if (number == null || number <= 0) {
            throw new IllegalArgumentException("Informe um número de mesa válido.");
        }
        Mesa mesa = mesaRepository.findByNumberAndActiveTrue(number)
                .filter(value -> Boolean.TRUE.equals(value.getActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Mesa não encontrada ou inativa."));
        String tableToken = jwtService.generateTableAccessToken(mesa.getId(), version(mesa));
        JwtService.TableTokenClaims context = jwtService.readTableAccessToken(tableToken);
        if (context == null) throw new IllegalStateException("Não foi possível emitir o contexto da mesa.");
        return new TableContextResponse(MesaSummaryResponse.from(mesa), tableToken, context.expiresAt());
    }

    @Transactional(readOnly = true)
    public Mesa requireWriteAccess(Long mesaId, String tableToken) {
        if (currentUser.isStaff()) {
            if (!hasActiveStaffIdentity()) throw denied();
            return activeMesa(mesaId);
        }
        JwtService.TableTokenClaims claims = jwtService.readTableAccessToken(tableToken);
        if (claims == null || !claims.mesaId().equals(mesaId)) throw denied();
        return validateCurrentMesa(claims);
    }

    private Mesa activeMesa(Long id) {
        return mesaRepository.findByIdAndActiveTrue(id).filter(mesa -> Boolean.TRUE.equals(mesa.getActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Mesa não encontrada ou inativa."));
    }

    private boolean hasActiveStaffIdentity() {
        Long staffId = currentUser.idOrNull();
        return staffId != null && staffId > 0;
    }

    private Mesa validateCurrentMesa(JwtService.TableTokenClaims claims) {
        Mesa mesa = mesaRepository.findByIdAndActiveTrue(claims.mesaId())
                .filter(value -> Boolean.TRUE.equals(value.getActive())).orElseThrow(this::denied);
        if (!MessageDigest.isEqual(version(mesa).getBytes(StandardCharsets.US_ASCII),
                claims.qrVersion().getBytes(StandardCharsets.US_ASCII))) throw denied();
        return mesa;
    }

    private String version(Mesa mesa) {
        if (mesa.getQrCode() == null || mesa.getQrCode().isBlank()) throw denied();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(mesa.getQrCode().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("Não foi possível verificar o contexto da mesa.", unavailable);
        }
    }

    private AccessDeniedException denied() {
        return new AccessDeniedException("Contexto da mesa inválido ou expirado. Leia o QR da mesa novamente.");
    }
}
