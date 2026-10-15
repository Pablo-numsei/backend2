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
        return new DesperdicioCreateRequest(7L, quantity, expected, reason, null, new BigDecimal("3.50"));
    }

    @Test
    void rejectsInvalidReasonBeforeTouchingDatabase() {
        assertThrows(IllegalArgumentException.class,
                () -> service.registrar(request(1, 10, "MOTIVO_DESCONHECIDO"), 1L));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsOldMotivesThatTheDatabaseCheckDoesNotAccept() {
        for (String invalid : List.of("DANO", "OUTROS")) {
            assertThrows(IllegalArgumentException.class,
                    () -> service.registrar(request(1, 10, invalid), 1L));
        }
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsNonPositiveQuantityBeforeTouchingDatabase() {
        assertThrows(IllegalArgumentException.class,
                () -> service.registrar(request(0, 10, "PREPARO"), 1L));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsObservationOverLimitBeforeTouchingDatabase() {
        var input = new DesperdicioCreateRequest(7L, 1, 10, "DANIFICADO", "x".repeat(256), new BigDecimal("3.50"));
        assertThrows(IllegalArgumentException.class, () -> service.registrar(input, 1L));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsNonMonetaryCostBeforeTouchingDatabase() {
        var input = new DesperdicioCreateRequest(7L, 1, 10, "DANIFICADO", null, new BigDecimal("0.001"));
        assertThrows(IllegalArgumentException.class, () -> service.registrar(input, 1L));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsMissingOperatorBeforeTouchingDatabase() {
        assertThrows(IllegalArgumentException.class,
                () -> service.registrar(request(1, 10, "DANIFICADO"), null));
        verifyNoInteractions(jdbc);
    }

    @Test
    void inactiveOrMissingProductCannotBeWasted() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(7L))).thenReturn(List.of());
        assertThrows(ResourceNotFoundException.class,
                () -> service.registrar(request(1, 10, "VENCIMENTO"), 1L));
    }

    @Test
    void staleStockIsRejected() {
        mockProductStock(10);
        assertThrows(IllegalStateException.class,
                () -> service.registrar(request(2, 13, "DANIFICADO"), 1L));
    }

    @Test
    void wasteCannotExceedRealStock() {
        mockProductStock(3);
        assertThrows(IllegalArgumentException.class,
                () -> service.registrar(request(4, 3, "PREPARO"), 1L));
    }

    @Test
    void failedStockWriteDoesNotCreateDesperdicio() {
        mockProductStock(5);
        when(jdbc.update(anyString(), eq(3), eq(7L), eq(5))).thenReturn(0);
        assertThrows(IllegalStateException.class,
                () -> service.registrar(request(2, 5, "DANIFICADO"), 1L));
        verify(jdbc, never()).queryForObject(contains("INSERT INTO dbo.Desperdicios_Produtos"), eq(Long.class), any());
    }

    private void mockProductStock(int stock) {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(7L))).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getLong("id_produto")).thenReturn(7L);
            when(rs.getInt("estoque")).thenReturn(stock);
            return List.of(mapper.mapRow(rs, 0));
        });
    }
}
