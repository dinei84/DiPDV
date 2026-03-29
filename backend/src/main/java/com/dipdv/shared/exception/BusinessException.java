package com.dipdv.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Exceção de negócio — lançada pelos Services para erros esperados.
 * O GlobalExceptionHandler a captura e retorna o status HTTP correto.
 *
 * Exemplos de uso:
 *   throw new BusinessException("Pedido não encontrado", HttpStatus.NOT_FOUND);
 *   throw new BusinessException("Caixa já está fechado", HttpStatus.CONFLICT);
 *   throw new BusinessException("Email ou senha inválidos", HttpStatus.UNAUTHORIZED);
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
