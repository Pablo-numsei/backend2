package com.itb.dto;

import jakarta.validation.constraints.Size;

public class MesaUpdateRequest extends MesaCreateRequest {
    // Compatibilidade: um valor legado só é aceito se coincidir com a versão atual.
    @Size(max = 255)
    private String qrCode;

    public MesaUpdateRequest() {}
    public MesaUpdateRequest(Integer number, String qrCode) {
        super(number);
        this.qrCode = qrCode;
    }
    public String getQrCode() { return qrCode; }
    public void setQrCode(String qrCode) { this.qrCode = qrCode; }
}
