package com.dipdv.shared.security;

import java.util.UUID;

/**
 * Detalhes customizados de autenticação armazenados no SecurityContext.
 *
 * Acessível nos Controllers via @AuthenticationPrincipal ou
 * injeção manual do SecurityContextHolder.
 *
 * EXEMPLO DE USO NO CONTROLLER:
 *
 *   @GetMapping("/me")
 *   public ResponseEntity<?> getMe(Authentication auth) {
 *       DiPdvAuthDetails details = (DiPdvAuthDetails) auth.getDetails();
 *       UUID userId   = details.userId();
 *       UUID tenantId = details.tenantId();
 *       return ResponseEntity.ok(...);
 *   }
 *
 * Usar record (Java 16+) para imutabilidade automática —
 * sem necessidade de Lombok ou getters manuais.
 */
public record DiPdvAuthDetails(
        UUID userId,
        UUID tenantId,
        String role
) {}
