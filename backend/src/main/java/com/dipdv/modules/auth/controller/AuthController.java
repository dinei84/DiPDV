package com.dipdv.modules.auth.controller;

import com.dipdv.modules.auth.dto.AuthResponse;
import com.dipdv.modules.auth.dto.LoginRequest;
import com.dipdv.modules.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticação", description = "Login e gerenciamento de tokens")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(
        summary = "Login do usuário",
        description = "Autentica com email e senha dentro de um tenant. Retorna JWT válido por 8 horas."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login bem-sucedido",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "401", description = "Credenciais inválidas",
            content = @Content),
        @ApiResponse(responseCode = "400", description = "Payload inválido (campos obrigatórios ausentes)",
            content = @Content)
    })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
