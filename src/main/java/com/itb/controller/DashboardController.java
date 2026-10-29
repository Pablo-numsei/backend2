package com.itb.controller;

import com.itb.dto.DashboardResumoResponse;
import com.itb.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/resumo")
    public ResponseEntity<DashboardResumoResponse> getResumo(Authentication authentication) {
        DashboardResumoResponse resumo = dashboardService.getResumo();
        boolean administrator = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMINISTRADOR".equals(authority.getAuthority()));

        if (administrator) {
            return ResponseEntity.ok(resumo);
        }

        List<DashboardResumoResponse.PedidoRecente> pedidosSemValores = resumo.pedidosRecentes().stream()
                .map(pedido -> new DashboardResumoResponse.PedidoRecente(
                        pedido.id(), pedido.mesa(), pedido.status(), null, pedido.criadoEm()))
                .toList();

        return ResponseEntity.ok(new DashboardResumoResponse(
                resumo.pedidosHoje(),
                null,
                null,
                resumo.mesasOcupadas(),
                resumo.totalMesas(),
                resumo.pedidosPorStatus(),
                pedidosSemValores,
                resumo.movimentoPorHora(),
                resumo.mesas()
        ));
    }
}
