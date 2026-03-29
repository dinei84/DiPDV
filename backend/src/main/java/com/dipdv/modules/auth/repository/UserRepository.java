package com.dipdv.modules.auth.repository;

import com.dipdv.modules.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Busca usuário ativo por email dentro de um tenant específico.
     * Ignora usuários com soft delete (deleted_at IS NOT NULL).
     *
     * NOTA: Esta query bypassa o RLS porque é executada sem SET LOCAL.
     * É segura aqui pois o login ainda não tem tenant no JWT.
     * Após autenticado, todas as demais queries passam pelo RLS normalmente.
     */
    @Query("""
        SELECT u FROM User u
        WHERE u.email = :email
          AND u.tenantId = :tenantId
          AND u.active = true
          AND u.deletedAt IS NULL
    """)
    Optional<User> findActiveByEmailAndTenantId(
        @Param("email") String email,
        @Param("tenantId") UUID tenantId
    );

    /**
     * Verifica se um email já está em uso no tenant.
     * Útil para validação no cadastro de usuários (Sprint 1).
     */
    boolean existsByEmailAndTenantIdAndDeletedAtIsNull(String email, UUID tenantId);
}
