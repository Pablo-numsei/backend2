package com.itb.dto;

import com.itb.model.Mesa;

public record MesaResponse(Long id, Integer number, Boolean active) {
    public static MesaResponse from(Mesa mesa) {
        return new MesaResponse(mesa.getId(), mesa.getNumber(), mesa.getActive());
    }
}
