package com.dipdv.shared.exception;

import com.dipdv.shared.module.dto.ModuleErrorResponse;
import com.dipdv.shared.module.exception.ModuleNotEnabledException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Handler global de excecoes - garante respostas JSON padronizadas.
 *
 * HIERARQUIA DE HANDLERS:
 * 1. MethodArgumentNotValidException -> 400 com lista de campos invalidos
 * 2. BusinessException -> status definido pelo service
 * 3. AccessDeniedException -> 403 para negacao de autorizacao
 * 4. Exception -> 500 sem expor detalhes internos
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiError.FieldError> fields = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new ApiError.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();

        return ResponseEntity
                .badRequest()
                .body(ApiError.ofValidation(fields));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex) {
        log.warn("BusinessException: {} - status {}", ex.getMessage(), ex.getStatus());

        return ResponseEntity
                .status(ex.getStatus())
                .body(ApiError.of(
                        ex.getStatus().value(),
                        ex.getStatus().name(),
                        ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Erro de leitura HTTP: {}", ex.getMessage());
        return ResponseEntity
                .badRequest()
                .body(ApiError.of(400, "BAD_REQUEST", "JSON malformado ou corpo da requisicao invalido."));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(401, "UNAUTHORIZED", "Email ou senha invalidos."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(403, "FORBIDDEN", "Sem permissao para esta operacao"));
    }

    @ExceptionHandler(ModuleNotEnabledException.class)
    public ResponseEntity<ModuleErrorResponse> handleModuleNotEnabled(ModuleNotEnabledException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(new ModuleErrorResponse("MODULE_NOT_ENABLED", ex.getModuleCode()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.warn("Optimistic locking conflict: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiError.of(
                        409,
                        "CONFLICT",
                        "Pedido foi modificado por outro operador. Recarregue e tente novamente."));
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleMediaTypeNotSupported(org.springframework.web.HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiError.of(415, "UNSUPPORTED_MEDIA_TYPE", "Content-Type não suportado. Use application/json."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Erro interno nao tratado", ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(500, "INTERNAL_SERVER_ERROR", "Erro interno. Tente novamente em instantes."));
    }
}
