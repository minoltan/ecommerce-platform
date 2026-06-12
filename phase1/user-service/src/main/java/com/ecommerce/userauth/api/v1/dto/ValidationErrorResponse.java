package com.ecommerce.userauth.api.v1.dto;

import java.util.List;
import java.util.UUID;

public record ValidationErrorResponse(String code, String message, UUID correlationId, List<FieldError> errors) {

    public record FieldError(String field, String reason) {
    }
}
