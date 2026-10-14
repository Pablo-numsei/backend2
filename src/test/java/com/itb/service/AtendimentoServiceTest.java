package com.itb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itb.dto.AtendimentoCreateRequest;
import com.itb.dto.AtendimentoResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AtendimentoServiceTest {
    @Mock private JdbcTemplate jdbc;
    @Mock private PushNotificationService push;
    @InjectMocks private AtendimentoService service;
    private PushNotificationService realPush;

    @AfterEach
    void cleanup() {
        if (realPush != null) realPush.shutdown();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void identicalOpenRequestReusesRecordWithoutInsertOrAnotherNotification() {
        AtendimentoResponse existing = response("ENVIADO");
        stubMesa();
        stubDuplicates(List.of(existing));

        assertSame(existing, service.criar(new AtendimentoCreateRequest(3L, null, "garcom", " água ")));
        verify(jdbc, never()).queryForObject(contains("INSERT INTO"), eq(Long.class), any(Object[].class));
        verifyNoInteractions(push);
    }

    @Test
    void lockAndScopedSearchPrecedeInsertAndNotification() {
        stubMesa();
        stubDuplicates(List.of());
        AtendimentoResponse created = stubInsert();

        assertSame(created, service.criar(new AtendimentoCreateRequest(3L, null, "GARCOM", "água")));

        var order = inOrder(jdbc, push);
        order.verify(jdbc).queryForObject(contains("WITH (UPDLOCK, HOLDLOCK)"), eq(Long.class), eq(3L));
        order.verify(jdbc).query(contains("SELECT TOP (1)"), rowMapper(), any(Object[].class));
        order.verify(jdbc).queryForObject(contains("INSERT INTO"), eq(Long.class), any(Object[].class));
        order.verify(jdbc).queryForObject(contains("WHERE a.id_atendimento = ?"), rowMapper(), eq(8L));
        order.verify(push).notifyStaffAtendimento(created);
    }

    @Test
    void detalheIsTrimmedAndTipoNormalizedBeforeInsert() {
        stubMesa();
        stubDuplicates(List.of());
        stubInsert();

        service.criar(new AtendimentoCreateRequest(3L, null, " garcom ", "  água  "));

        var parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForObject(contains("INSERT INTO"), eq(Long.class), parameters.capture());
        assertArrayEquals(new Object[]{3L, null, "GARCOM", "água"}, parameters.getValue());
    }

    @Test
    void whitespaceOnlyDetalheUsesSameNullKeyAndDoesNotNotifyAgain() {
        stubMesa();
        stubDuplicates(List.of(response("EM_ATENDIMENTO")));

        service.criar(new AtendimentoCreateRequest(3L, null, "GARCOM", "  "));

        var parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(contains("NULLIF(LTRIM(RTRIM(a.detalhe))"), rowMapper(), parameters.capture());
        assertArrayEquals(new Object[]{3L, "GARCOM"}, parameters.getValue());
        verifyNoInteractions(push);
    }

    @Test
    void sameMesaButDifferentPedidoOrDetalheHasSeparateScopedKey() {
        stubMesa();
        when(jdbc.queryForObject(contains("FROM dbo.Pedidos"), eq(Integer.class), eq(42L), eq(3L))).thenReturn(1);
        stubDuplicates(List.of());
        stubInsert();

        service.criar(new AtendimentoCreateRequest(3L, 42L, "CONTA", "detalhe distinto"));

        var sql = ArgumentCaptor.forClass(String.class);
        var parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(), rowMapper(), parameters.capture());
        assertTrue(sql.getValue().contains("a.pedido_id = ?"));
        assertTrue(sql.getValue().contains("COLLATE Latin1_General_100_BIN2 = ?"));
        assertArrayEquals(new Object[]{3L, "CONTA", 42L, "detalhe distinto"}, parameters.getValue());
        verify(push).notifyStaffAtendimento(any());
    }

    @Test
    void concludedRequestsAreExcludedFromDeduplicationSearch() {
        stubMesa();
        stubDuplicates(List.of());
        stubInsert();

        service.criar(new AtendimentoCreateRequest(3L, null, "GARCOM", null));

        verify(jdbc).query(contains("a.status IN ('ENVIADO', 'EM_ATENDIMENTO')"), rowMapper(), any(Object[].class));
        verify(jdbc).queryForObject(contains("INSERT INTO"), eq(Long.class), any(Object[].class));
    }

    @Test
    void invalidMesaCannotInsertOrNotify() {
        when(jdbc.queryForObject(contains("WITH (UPDLOCK, HOLDLOCK)"), eq(Long.class), eq(3L)))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertThrows(IllegalArgumentException.class,
                () -> service.criar(new AtendimentoCreateRequest(3L, null, "GARCOM", null)));
        verify(jdbc, never()).query(anyString(), rowMapper(), any(Object[].class));
        verifyNoInteractions(push);
    }

    @Test
    void pedidoMustBelongToLockedMesaBeforeDeduplication() {
        stubMesa();
        when(jdbc.queryForObject(contains("FROM dbo.Pedidos"), eq(Integer.class), eq(42L), eq(3L))).thenReturn(0);

        assertThrows(IllegalArgumentException.class,
                () -> service.criar(new AtendimentoCreateRequest(3L, 42L, "CONTA", null)));
        verify(jdbc, never()).query(anyString(), rowMapper(), any(Object[].class));
        verifyNoInteractions(push);
    }

    @Test
    void repeatingCurrentStatusDoesNotChangeTimestampOrNotify() {
        AtendimentoResponse current = response("EM_ATENDIMENTO");
        stubRead(current);

        assertSame(current, service.atualizarStatus(8L, "em_atendimento"));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verifyNoInteractions(push);
    }

    @Test
    void repeatingConcludedStatusAlsoDoesNotWriteAgain() {
        AtendimentoResponse current = response("CONCLUIDO");
        stubRead(current);

        assertSame(current, service.atualizarStatus(8L, "CONCLUIDO"));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void concurrentSameTargetIsIdempotentAfterFailedCompareAndSet() {
        AtendimentoResponse target = response("EM_ATENDIMENTO");
        stubRead(response("ENVIADO"), target);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);

        assertSame(target, service.atualizarStatus(8L, "EM_ATENDIMENTO"));
        verify(jdbc).update(contains("AND status = ?"), eq("EM_ATENDIMENTO"), eq(8L), eq("ENVIADO"));
    }

    @Test
    void conflictingConcurrentTargetReturnsConflictInsteadOfRegression() {
        stubRead(response("ENVIADO"), response("CONCLUIDO"));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> service.atualizarStatus(8L, "EM_ATENDIMENTO"));
    }

    @Test
    void normalStatusAdvanceKeepsCompareAndSetPredicate() {
        AtendimentoResponse target = response("EM_ATENDIMENTO");
        stubRead(response("ENVIADO"), target);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        assertSame(target, service.atualizarStatus(8L, "EM_ATENDIMENTO"));
        verify(jdbc).update(contains("WHERE id_atendimento = ? AND status = ?"), eq("EM_ATENDIMENTO"), eq(8L), eq("ENVIADO"));
    }

    @Test
    void skippingStatusIsRejectedBeforeUpdate() {
        stubRead(response("ENVIADO"));

        assertThrows(IllegalArgumentException.class, () -> service.atualizarStatus(8L, "CONCLUIDO"));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void newRequestRegistersPushButRollbackDoesNotSendIt() {
        stubMesa();
        stubDuplicates(List.of());
        stubInsert();
        JdbcTemplate notificationJdbc = mock(JdbcTemplate.class);
        realPush = new PushNotificationService(notificationJdbc, new ObjectMapper(), "configured", "configured",
                "mailto:synthetic@tablehub.test", mock(PushEndpointValidator.class));
        AtendimentoService realDispatch = new AtendimentoService(jdbc, realPush);
        beginTransaction();

        realDispatch.criar(new AtendimentoCreateRequest(3L, null, "GARCOM", null));

        assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
        verifyNoInteractions(notificationJdbc);
        TransactionSynchronizationManager.getSynchronizations().getFirst()
                .afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verifyNoInteractions(notificationJdbc);
    }

    @Test
    void reusedRequestDoesNotEvenRegisterPushCallback() {
        stubMesa();
        stubDuplicates(List.of(response("ENVIADO")));
        JdbcTemplate notificationJdbc = mock(JdbcTemplate.class);
        realPush = new PushNotificationService(notificationJdbc, new ObjectMapper(), "configured", "configured",
                "mailto:synthetic@tablehub.test", mock(PushEndpointValidator.class));
        AtendimentoService realDispatch = new AtendimentoService(jdbc, realPush);
        beginTransaction();

        realDispatch.criar(new AtendimentoCreateRequest(3L, null, "GARCOM", null));

        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
        verifyNoInteractions(notificationJdbc);
    }

    private void stubMesa() {
        when(jdbc.queryForObject(contains("WITH (UPDLOCK, HOLDLOCK)"), eq(Long.class), eq(3L))).thenReturn(3L);
    }
    private void stubDuplicates(List<AtendimentoResponse> responses) {
        when(jdbc.query(contains("SELECT TOP (1)"), rowMapper(), any(Object[].class))).thenReturn(responses);
    }
    private AtendimentoResponse stubInsert() {
        AtendimentoResponse created = response("ENVIADO");
        when(jdbc.queryForObject(contains("INSERT INTO"), eq(Long.class), any(Object[].class))).thenReturn(8L);
        stubRead(created);
        return created;
    }
    private void stubRead(AtendimentoResponse first, AtendimentoResponse... next) {
        when(jdbc.queryForObject(contains("WHERE a.id_atendimento = ?"), rowMapper(), eq(8L))).thenReturn(first, next);
    }
    private RowMapper<AtendimentoResponse> rowMapper() { return any(); }
    private AtendimentoResponse response(String status) {
        LocalDateTime created = LocalDateTime.of(2026, 10, 14, 12, 0);
        return new AtendimentoResponse(8L, 3L, 7, null, "GARCOM", "água", status, created, created);
    }
    private void beginTransaction() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }
}
