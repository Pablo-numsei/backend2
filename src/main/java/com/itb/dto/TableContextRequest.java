package com.itb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TableContextRequest(@NotBlank @Size(max = 4096) String qrToken) {}
