package com.itb.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class MesaCreateRequest {
    @NotNull(message = "O número da mesa é obrigatório")
    @Min(value = 1, message = "O número da mesa deve ser positivo")
    @Max(value = 9999, message = "O número da mesa deve ser no máximo 9999")
    private Integer number;

    public MesaCreateRequest() {}
    public MesaCreateRequest(Integer number) { this.number = number; }
    public Integer getNumber() { return number; }
    public void setNumber(Integer number) { this.number = number; }

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("A requisição da mesa contém campos não permitidos.");
    }
}
