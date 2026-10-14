package com.itb.service;

import com.itb.dto.MesaPanelResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DashboardServiceTest {
    @Test
    void dashboardUsesTheSamePanelForOccupancyAndAllActiveTables() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MesaPanelService tableService = mock(MesaPanelService.class);
        List<MesaPanelResponse> tables = new ArrayList<>();
        for (int number = 1; number <= 12; number++) {
            List<MesaPanelResponse.OrderSummary> orders = number == 10
                    ? List.of(new MesaPanelResponse.OrderSummary(100L, "Entregue", BigDecimal.TEN, false))
                    : List.of();
            tables.add(new MesaPanelResponse((long) number, number, orders.isEmpty() ? "Livre" : "Ocupada", orders));
        }
        when(tableService.getPanel()).thenReturn(tables);

        var dashboard = new DashboardService(jdbc, tableService).getResumo();

        assertEquals(12, dashboard.totalMesas());
        assertEquals(1, dashboard.mesasOcupadas());
        assertEquals(12, dashboard.mesas().size());
        assertEquals("Ocupada", dashboard.mesas().get(9).status());
        assertEquals(12, dashboard.mesas().get(11).numero());
        verify(tableService, times(1)).getPanel();
    }
}
