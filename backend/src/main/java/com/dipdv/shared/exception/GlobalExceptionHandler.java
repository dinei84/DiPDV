package com.dipdv.shared.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Handler global de exceções — garante respostas JSON padronizadas.
 *
 * HIERARQUIA DE HANDLERS (ordem de precedência):
 * 1. MethodArgumentNotValidException → 400 com lista de campos inválidos
 * 2. BusinessException               → status definido pelo Service (401, 404, 409...)
 * 3. Exception (fallback)            → 500 sem expor detalhes internos
 *
 * IMPORTANTE: Nunca expor stack trace ou mensagens internas em produção.
 * O log registra o detalhe técnico; o response retorna apenas o necessário.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Erros de validação do @Valid — campos obrigatórios, formatos inválidos, etc.
     * Retorna lista de todos os campos com problema de uma vez.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiError.FieldError> fields = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(e -> new ApiError.FieldError(e.getField(), e.getDefaultMessage()))
            .toList();

        return ResponseEntity
            .badRequest()
            .body(ApiError.ofValidation(fields));
    }

    /**
     * Erros de negócio lançados pelos Services.
     * O status HTTP é definido pelo próprio Service via BusinessException.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex) {
        log.warn("BusinessException: {} — status {}", ex.getMessage(), ex.getStatus());

        return ResponseEntity
            .status(ex.getStatus())
            .body(ApiError.of(
                ex.getStatus().value(),
                ex.getStatus().name(),
                ex.getMessage()
            ));
    }

    /**
     * Erros de leitura HTTP — JSON malformado, etc.
     * Retorna 400 em vez de 500.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Erro de leitura HTTP: {}", ex.getMessage());
        return ResponseEntity
            .badRequest()
            .body(ApiError.of(400, "BAD_REQUEST", "JSON malformado ou corpo da requisição inválido."));
    }

    /**
     * Erros de autenticação do Spring Security (ex: BadCredentialsException).
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ApiError.of(401, "UNAUTHORIZED", "Email ou senha inválidos."));
    }

    /**
     * Fallback para qualquer exceção não tratada.
     * Loga o erro completo internamente mas retorna mensagem genérica ao cliente.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Erro interno não tratado", ex);

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiError.of(500, "INTERNAL_SERVER_ERROR",
                "Erro interno. Tente novamente em instantes."));
    }
}
