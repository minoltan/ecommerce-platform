package com.ecommerce.userauth.api.v1.dto;

import java.util.UUID;

public record ErrorResponse(String code, String message, UUID correlationId) {
}
