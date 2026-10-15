package com.itb.service;

import com.itb.dto.DesperdicioCreateRequest;
import com.itb.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DesperdicioServiceTest {
    @Mock JdbcTemplate jdbc;
    private DesperdicioService service;

    @BeforeEach
    void prepare() {
        service = new DesperdicioService(jdbc);
    }

    private DesperdicioCreateRequest request(int quantity, int expected, String reason) {
        return new DesperdicioCreateRequest(7L, quantity, expected, reason, null);
    }

    @Test
    void rejectsInvalidReasonBeforeTouchingDatabase() {
        assertThrows(IllegalArgumentException.class,
                () -> service.registrar(request(1, 10, "MOTIVO_DESCONHECIDO"), "admin@test.local"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsNonPositiveQuantityBeforeTouchingDatabase() {
        assertThrows(IllegalArgumentException.class,
                () -> service.registrar(request(0, 10, "PREPARO"), "admin@test.local"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsObservationOverLimitBeforeTouchingDatabase() {
        var input = new DesperdicioCreateRequest(7L, 1, 10, "DANO", "x".repeat(256));
        assertThrows(IllegalArgumentException.class,
                () -> service.registrar(input, "admin@test.local"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void inactiveOrMissingProductCannotBeWasted() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(7L))).thenReturn(List.of());
        assertThrows(ResourceNotFoundException.class,
                () -> service.registrar(request(1, 10, "VENCIMENTO"), "admin@test.local"));
    }

    @Test
    void staleStockIsRejected() {
        mockProductStock(10);
        assertThrows(IllegalStateException.class,
                () -> service.registrar(request(2, 13, "DANO"), "admin@test.local"));
    }

    @Test
    void wasteCannotExceedRealStock() {
        mockProductStock(3);
        assertThrows(IllegalArgumentException.class,
                () -> service.registrar(request(4, 3, "PREPARO"), "admin@test.local"));
    }

    private void mockProductStock(int stock) {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(7L))).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getLong("id_produto")).thenReturn(7L);
            when(rs.getString("nome")).thenReturn("Produto de teste");
            when(rs.getBigDecimal("preco")).thenReturn(new BigDecimal("11.00"));
            when(rs.getInt("estoque")).thenReturn(stock);
            return List.of(mapper.mapRow(rs, 0));
        });
    }
}
