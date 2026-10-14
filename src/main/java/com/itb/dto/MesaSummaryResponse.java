package com.itb.dto;

import com.itb.model.Mesa;

public record MesaSummaryResponse(Long id, Integer number) {
    public static MesaSummaryResponse from(Mesa mesa) {
        return new MesaSummaryResponse(mesa.getId(), mesa.getNumber());
    }
}
