package com.itb.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record DashboardResumoResponse(
        long pedidosHoje,
        BigDecimal faturamentoHoje,
        BigDecimal ticketMedio,
        long mesasOcupadas,
        long totalMesas,
        List<StatusResumo> pedidosPorStatus,
        List<PedidoRecente> pedidosRecentes,
        List<MovimentoHora> movimentoPorHora,
        List<MesaResumo> mesas
) {

    public record StatusResumo(
            String status,
            long quantidade
    ) {
    }

    public record PedidoRecente(
            Long id,
            Integer mesa,
            String status,
            BigDecimal total,
            LocalDateTime criadoEm
    ) {
    }

    public record MovimentoHora(
            int hora,
            long quantidade
    ) {
    }

    public record MesaResumo(
            Integer numero,
            String status
    ) {
    }
}
