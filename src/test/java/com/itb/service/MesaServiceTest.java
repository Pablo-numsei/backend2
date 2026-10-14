package com.itb.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itb.dto.MesaCreateRequest;
import com.itb.dto.MesaUpdateRequest;
import com.itb.exception.ResourceNotFoundException;
import com.itb.model.Mesa;
import com.itb.repository.MesaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MesaServiceTest {
    @Mock private MesaRepository mesaRepository;
    @Mock private EntityManager entityManager;
    @InjectMocks private MesaService mesaService;
    private Mesa mesa;

    @BeforeEach
    void setUp() {
        mesa = new Mesa();
        mesa.setId(3L);
        mesa.setNumber(7);
        mesa.setActive(true);
        mesa.setQrCode("synthetic-existing-version");
    }

    @Test
    void publicListUsesActiveTablesAndOnlySummaryFields() {
        when(mesaRepository.findByActiveTrueOrderByNumberAsc()).thenReturn(List.of(mesa));

        var response = mesaService.listActive();
        var json = new ObjectMapper().valueToTree(response.getFirst());

        assertEquals(3L, response.getFirst().id());
        assertEquals(7, response.getFirst().number());
        assertFalse(json.has("qrCode"));
        assertFalse(json.has("createdAt"));
        verify(mesaRepository, never()).findAll();
    }

    @Test
    void inactiveOrMissingTableIsNotAvailableOnPublicLookup() {
        when(mesaRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> mesaService.getActive(3L));
    }

    @Test
    void serverGeneratesUuidForNewMesaWithoutClientIdentityOrState() {
        when(mesaRepository.saveAndFlush(any(Mesa.class))).thenAnswer(invocation -> {
            Mesa created = invocation.getArgument(0);
            assertNull(created.getId());
            assertNull(created.getActive());
            assertDoesNotThrow(() -> UUID.fromString(created.getQrCode()));
            created.setId(9L);
            created.setActive(true);
            return created;
        });

        var created = mesaService.create(new MesaCreateRequest(9));

        assertEquals(9L, created.id());
        assertEquals(9, created.number());
        assertTrue(created.active());
        verify(entityManager).refresh(any(Mesa.class));
    }

    @Test
    void duplicateNumberIsRejectedBeforeInsert() {
        when(mesaRepository.existsByNumber(7)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> mesaService.create(new MesaCreateRequest(7)));
        verify(mesaRepository, never()).saveAndFlush(any());
    }

    @Test
    void creationRejectsIdentityAndStateEvenWhenMapperIgnoresUnknownProperties() {
        ObjectMapper mapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        for (String field : List.of("id", "qrCode", "active", "createdAt", "updatedAt")) {
            String body = "{\"number\":7,\"" + field + "\":null}";
            assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,
                    () -> mapper.readValue(body, MesaCreateRequest.class));
        }
    }

    @Test
    void renameLocksMesaAndKeepsQrVersion() {
        when(mesaRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(mesa));
        when(mesaRepository.saveAndFlush(mesa)).thenReturn(mesa);

        assertEquals(8, mesaService.update(3L, new MesaUpdateRequest(8, null)).number());
        assertEquals("synthetic-existing-version", mesa.getQrCode());
        verify(entityManager).refresh(mesa);
    }

    @Test
    void legacySameQrFieldDoesNotRotateVersion() {
        when(mesaRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(mesa));
        when(mesaRepository.saveAndFlush(mesa)).thenReturn(mesa);

        mesaService.update(3L, new MesaUpdateRequest(8, mesa.getQrCode()));
        assertEquals("synthetic-existing-version", mesa.getQrCode());
    }

    @Test
    void arbitraryQrCannotBeWrittenThroughUpdate() {
        when(mesaRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(mesa));

        assertThrows(IllegalArgumentException.class,
                () -> mesaService.update(3L, new MesaUpdateRequest(8, "client-selected-version")));
        assertEquals(7, mesa.getNumber());
        verify(mesaRepository, never()).saveAndFlush(any());
    }

    @Test
    void deactivationLocksMesaAndIsIdempotent() {
        when(mesaRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(mesa));

        mesaService.deactivate(3L);
        mesaService.deactivate(3L);

        assertFalse(mesa.getActive());
        verify(mesaRepository, times(1)).saveAndFlush(mesa);
    }

    @Test
    void rawEntityAlsoHidesQrVersionFromSerialization() {
        var json = new ObjectMapper().valueToTree(mesa);
        assertFalse(json.has("qrCode"));
    }
}
