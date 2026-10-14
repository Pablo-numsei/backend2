package com.itb.service;

import com.itb.dto.MesaPanelResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Validates bulk result extraction with synthetic rows; does not execute SQL against a database. */
class MesaPanelServiceTest {
    @Test
    void returnsAllTablesBeyondEightInQueryOrderWithoutAdditionalQueries() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        List<Row> rows = new ArrayList<>();
        for (int number = 1; number <= 12; number++) rows.add(free(number));
        queryRows(jdbc, rows);

        List<MesaPanelResponse> panel = new MesaPanelService(jdbc).getPanel();

        assertEquals(12, panel.size());
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
                panel.stream().map(MesaPanelResponse::number).toList());
        assertTrue(panel.stream().allMatch(table -> table.status().equals("Livre") && table.orders().isEmpty()));
        verify(jdbc, times(1)).query(anyString(), any(ResultSetExtractor.class));
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void groupsOpenOrdersAndKeepsDeliveredUnpaidAndUndeliveredPaidTablesOccupied() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        queryRows(jdbc, List.of(
                new Row(1L, 1, 11L, "Entregue", new BigDecimal("25.00"), false),
                new Row(1L, 1, 12L, "Em preparo", new BigDecimal("13.50"), true),
                // A table whose orders were all delivered and paid has a LEFT JOIN null order.
                free(2)));

        List<MesaPanelResponse> panel = new MesaPanelService(jdbc).getPanel();

        assertEquals(2, panel.size());
        assertEquals("Ocupada", panel.get(0).status());
        assertEquals(List.of(11L, 12L), panel.get(0).orders().stream().map(MesaPanelResponse.OrderSummary::id).toList());
        assertEquals("Entregue", panel.get(0).orders().get(0).status());
        assertFalse(panel.get(0).orders().get(0).paid());
        assertTrue(panel.get(0).orders().get(1).paid());
        assertEquals(new BigDecimal("13.50"), panel.get(0).orders().get(1).totalValue());
        assertEquals("Livre", panel.get(1).status());
        assertTrue(panel.get(1).orders().isEmpty());
    }

    @Test
    void noActiveTablesReturnsEmptyList() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        queryRows(jdbc, List.of());
        assertTrue(new MesaPanelService(jdbc).getPanel().isEmpty());
    }

    private static Row free(int number) { return new Row(number, number, null, null, null, false); }

    @SuppressWarnings("unchecked")
    private static void queryRows(JdbcTemplate jdbc, List<Row> data) throws Exception {
        ResultSet rows = mock(ResultSet.class);
        AtomicInteger index = new AtomicInteger(-1);
        when(rows.next()).thenAnswer(call -> index.incrementAndGet() < data.size());
        when(rows.getLong("table_id")).thenAnswer(call -> data.get(index.get()).tableId());
        when(rows.getInt("table_number")).thenAnswer(call -> data.get(index.get()).number());
        when(rows.getObject("order_id", Long.class)).thenAnswer(call -> data.get(index.get()).orderId());
        when(rows.getString("order_status")).thenAnswer(call -> data.get(index.get()).status());
        when(rows.getBigDecimal("order_total")).thenAnswer(call -> data.get(index.get()).total());
        when(rows.getBoolean("paid")).thenAnswer(call -> data.get(index.get()).paid());
        doAnswer(call -> ((ResultSetExtractor<List<MesaPanelResponse>>) call.getArgument(1)).extractData(rows))
                .when(jdbc).query(anyString(), any(ResultSetExtractor.class));
    }

    private record Row(long tableId, int number, Long orderId, String status, BigDecimal total, boolean paid) { }
}
