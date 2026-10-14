package com.itb.service;

import com.itb.dto.MesaCreateRequest;
import com.itb.dto.MesaResponse;
import com.itb.dto.MesaUpdateRequest;
import com.itb.exception.ResourceNotFoundException;
import com.itb.model.Mesa;
import com.itb.repository.MesaRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MesaService {
    private final MesaRepository mesaRepository;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<MesaResponse> listActive() {
        return mesaRepository.findByActiveTrueOrderByNumberAsc().stream().map(MesaResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public MesaResponse getActive(Long id) {
        return MesaResponse.from(mesaRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mesa não encontrada ou inativa.")));
    }

    @Transactional
    public MesaResponse create(MesaCreateRequest request) {
        if (mesaRepository.existsByNumber(request.getNumber())) {
            throw new IllegalStateException("Já existe uma mesa com esse número.");
        }
        Mesa mesa = new Mesa();
        mesa.setNumber(request.getNumber());
        mesa.setQrCode(UUID.randomUUID().toString());
        Mesa saved = mesaRepository.saveAndFlush(mesa);
        entityManager.refresh(saved);
        return MesaResponse.from(saved);
    }

    @Transactional
    public MesaResponse update(Long id, MesaUpdateRequest request) {
        Mesa mesa = lockMesa(id);
        if (mesaRepository.existsByNumberAndIdNot(request.getNumber(), id)) {
            throw new IllegalStateException("Já existe outra mesa com esse número.");
        }
        if (request.getQrCode() != null && !request.getQrCode().equals(mesa.getQrCode())) {
            throw new IllegalArgumentException("O QR da mesa é gerenciado pelo servidor e não pode ser alterado neste formulário.");
        }
        mesa.setNumber(request.getNumber());
        Mesa saved = mesaRepository.saveAndFlush(mesa);
        entityManager.refresh(saved);
        return MesaResponse.from(saved);
    }

    @Transactional
    public void deactivate(Long id) {
        Mesa mesa = lockMesa(id);
        if (Boolean.TRUE.equals(mesa.getActive())) {
            mesa.setActive(false);
            mesaRepository.saveAndFlush(mesa);
        }
    }

    private Mesa lockMesa(Long id) {
        return mesaRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mesa não encontrada."));
    }
}
