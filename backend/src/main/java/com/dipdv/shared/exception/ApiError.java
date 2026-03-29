package com.dipdv.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Envelope padrão de erro para TODOS os erros da API.
 * Garante que o frontend sempre receba JSON estruturado — nunca HTML.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
    int status,
    String error,
    String message,
    OffsetDateTime timestamp,
    List<FieldError> fields       // preenchido apenas em erros de validação (@Valid)
) {
    public record FieldError(String field, String message) {}

    /** Factory para erros simples */
    public static ApiError of(int status, String error, String message) {
        return new ApiError(status, error, message, OffsetDateTime.now(), null);
    }

    /** Factory para erros de validação com lista de campos */
    public static ApiError ofValidation(List<FieldError> fields) {
        return new ApiError(400, "VALIDATION_ERROR",
            "Campos inválidos na requisição", OffsetDateTime.now(), fields);
    }
}
