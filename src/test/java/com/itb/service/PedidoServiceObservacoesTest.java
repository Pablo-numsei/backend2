package com.itb.service;

import com.itb.dto.ItemPedidoRequest;
import com.itb.dto.PedidoCreateRequest;
import com.itb.model.Order;
import com.itb.repository.OrderRepository;
import com.itb.repository.StatusPedidoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoServiceObservacoesTest {
    @Mock private JdbcTemplate jdbc;
    @Mock private OrderRepository orders;
    @Mock private StatusPedidoRepository statuses;
    @Mock private SocketService socket;
    @Mock private PushNotificationService push;
    @InjectMocks private PedidoService service;

    @Test
    void sendsCustomerAndPreparationNoteAsSqlParametersWithoutChangingStockTransaction() {
        Order created = new Order();
        created.setId(7L);
        when(orders.findById(7L)).thenReturn(Optional.of(created));
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    // JdbcTemplate uses Object...; getArguments() expands varargs into
                    // individual values. getRawArguments() retains the original array.
                    Object[] params = (Object[]) invocation.getRawArguments()[2];
                    assertTrue(sql.contains("EXEC dbo.sp_CriarPedido"));
                    assertTrue(sql.contains("@cliente_id = ?"));
                    assertTrue(sql.contains("@observacoes = ?"));
                    assertEquals(7, params.length);
                    assertEquals("carne bem passada",
                            ((SqlParameterValue) params[6]).getValue());
                    return 7L;
                });

        PedidoCreateRequest request = new PedidoCreateRequest(
                1L, null, List.of(new ItemPedidoRequest(8L, 1)),
                27L, "pabloteste01", " carne bem passada ");

        assertEquals(7L, service.criarPedido(request).getId());
        verify(socket).notifyOrderUpdate(created);
    }

    @Test
    void refusesOversizedNoteBeforeWritingAnyOrder() {
        PedidoCreateRequest request = new PedidoCreateRequest(
                1L, null, List.of(new ItemPedidoRequest(8L, 1)),
                27L, "pabloteste01", "x".repeat(256));
        assertThrows(IllegalArgumentException.class, () -> service.criarPedido(request));
        verifyNoInteractions(jdbc, orders);
    }
}
