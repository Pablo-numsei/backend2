package com.itb.dto;

import java.math.BigDecimal;

public record DesperdicioResumoResponse(
        Long registros,
        Long unidades,
        BigDecimal prejuizo
) {}
