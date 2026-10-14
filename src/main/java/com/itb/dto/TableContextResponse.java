package com.itb.dto;

import java.time.Instant;

public record TableContextResponse(MesaSummaryResponse mesa, String tableToken, Instant expiresAt) {}
