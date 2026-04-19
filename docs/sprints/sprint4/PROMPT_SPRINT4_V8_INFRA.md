# Prompt 1 — Sprint 4: Migration V8 + Infraestrutura SUPER_ADMIN

---

## Contexto

MVP concluído com 62 testes. Esta sprint implementa o módulo de gestão
de tenants para o dono do SaaS (SUPER_ADMIN).

Este prompt cobre exclusivamente infraestrutura e segurança — sem endpoints
de negócio ainda. O módulo Admin (endpoints + frontend) vem no Prompt 2.

**Branch:** `feature/US-SA01-super-admin-infra` a partir de `develop`
**Commits:** `feat(admin): ...`, `chore(security): ...`

---

## Decisões de design — obrigatório ler antes de começar

### UUID master
```
ffffffff-ffff-ffff-ffff-ffffffffffff
```
Declarar como constante Java em um único lugar:
```java
// shared/security/MasterTenantConstants.java
public final class MasterTenantConstants {
    public static final UUID MASTER_TENANT_ID =
        UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    private MasterTenantConstants() {}
}
```

### RLS Kill Switch — dupla verificação
O bypass exige as DUAS condições simultaneamente:
```sql
OR (
  current_setting('app.is_super_admin', true) = 'true'
  AND current_setting('app.current_tenant', true)
      = 'ffffffff-ffff-ffff-ffff-ffffffffffff'
)
```
Isso impede que `app.is_super_admin = 'true'` acidentalmente funcione
em contexto de tenant normal.

### Planos
ENUM `tenant_plan`: `TRIAL`, `ACTIVE`, `SUSPENDED`
Default: `TRIAL`. Sem lógica de restrição por plano agora — apenas estrutura.

### `last_activity_at`
Atualizado pelo `OrderService.closeOrder()` via UPDATE direto na tabela
`tenants`. Policy de RLS `tenant_self_update` permite isso sem privilégios extras.

### `is_admin_action` no audit_log
Campo `BOOLEAN NOT NULL DEFAULT FALSE`. Setado como `true` pelo `AuditAspect`
quando `TenantContext.get()` retornar o UUID master.

---

## Tarefa 1 — Migration V8

**Arquivo:** `backend/src/main/resources/db/migration/V8__super_admin_setup.sql`

```sql
-- =============================================================================
-- DiPDV — V8__super_admin_setup.sql
-- Infraestrutura SUPER_ADMIN: tenant master, novos ENUMs, RLS atualizado
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Novos ENUMs
-- -----------------------------------------------------------------------------
ALTER TYPE user_role ADD VALUE IF NOT EXISTS 'SUPER_ADMIN';

CREATE TYPE tenant_plan AS ENUM ('TRIAL', 'ACTIVE', 'SUSPENDED');

-- -----------------------------------------------------------------------------
-- 2. Atualizar tabela tenants
-- -----------------------------------------------------------------------------
ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS plan_type       tenant_plan  NOT NULL DEFAULT 'TRIAL',
    ADD COLUMN IF NOT EXISTS trial_until     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_activity_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS owner_email     VARCHAR(180),
    ADD COLUMN IF NOT EXISTS slug            VARCHAR(60);

COMMENT ON COLUMN tenants.owner_email      IS 'Email do primeiro usuário ADMIN criado no onboarding';
COMMENT ON COLUMN tenants.last_activity_at IS 'Atualizado pelo OrderService ao fechar pedido — indica tenant ativo';
COMMENT ON COLUMN tenants.slug             IS 'Identificador amigável para futuras URLs customizadas';

-- -----------------------------------------------------------------------------
-- 3. Adicionar is_admin_action ao audit_log
-- -----------------------------------------------------------------------------
ALTER TABLE audit_log
    ADD COLUMN IF NOT EXISTS is_admin_action BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN audit_log.is_admin_action IS
    'TRUE quando a ação foi executada pelo SUPER_ADMIN — facilita queries de compliance';

-- -----------------------------------------------------------------------------
-- 4. Criar tenant master
-- -----------------------------------------------------------------------------
INSERT INTO tenants (id, name, slug, active, plan_type)
VALUES (
    'ffffffff-ffff-ffff-ffff-ffffffffffff',
    'DiPDV Master',
    'master',
    TRUE,
    'ACTIVE'
) ON CONFLICT (id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 5. Policy de auto-update para tenants (last_activity_at)
-- Permite que o contexto de um tenant atualize apenas sua própria linha
-- -----------------------------------------------------------------------------
CREATE POLICY tenant_self_update ON tenants
    FOR UPDATE
    USING (id = current_setting('app.current_tenant', true)::UUID);

-- -----------------------------------------------------------------------------
-- 6. Atualizar RLS em TODAS as tabelas — adicionar Kill Switch
--
-- Padrão do Kill Switch (dupla verificação):
--   OR (
--     current_setting('app.is_super_admin', true) = 'true'
--     AND current_setting('app.current_tenant', true) = 'ffffffff-...'
--   )
--
-- Procedimento: DROP policy existente + CREATE nova com Kill Switch
-- -----------------------------------------------------------------------------

-- users
DROP POLICY IF EXISTS tenant_isolation ON users;
CREATE POLICY tenant_isolation ON users
    USING (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (
            current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff'
        )
    )
    WITH CHECK (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (
            current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff'
        )
    );

-- categories
DROP POLICY IF EXISTS tenant_isolation ON categories;
CREATE POLICY tenant_isolation ON categories
    USING (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
    )
    WITH CHECK (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
    );

-- products
DROP POLICY IF EXISTS tenant_isolation ON products;
CREATE POLICY tenant_isolation ON products
    USING (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
    )
    WITH CHECK (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
    );

-- modifier_groups
DROP POLICY IF EXISTS tenant_isolation ON modifier_groups;
CREATE POLICY tenant_isolation ON modifier_groups
    USING (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
    )
    WITH CHECK (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
    );

-- cash_registers
DROP POLICY IF EXISTS tenant_isolation ON cash_registers;
CREATE POLICY tenant_isolation ON cash_registers
    USING (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
    )
    WITH CHECK (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
    );

-- orders
DROP POLICY IF EXISTS tenant_isolation ON orders;
CREATE POLICY tenant_isolation ON orders
    USING (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
    )
    WITH CHECK (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
    );

-- payments
DROP POLICY IF EXISTS tenant_isolation ON payments;
CREATE POLICY tenant_isolation ON payments
    USING (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
    )
    WITH CHECK (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
    );

-- stock_movements
DROP POLICY IF EXISTS tenant_isolation ON stock_movements;
CREATE POLICY tenant_isolation ON stock_movements
    USING (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
    )
    WITH CHECK (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
    );

-- audit_log
DROP POLICY IF EXISTS tenant_isolation ON audit_log;
CREATE POLICY tenant_isolation ON audit_log
    USING (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
    )
    WITH CHECK (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
    );

-- nfce_documents
DROP POLICY IF EXISTS tenant_isolation ON nfce_documents;
CREATE POLICY tenant_isolation ON nfce_documents
    USING (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
    )
    WITH CHECK (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
    );

-- Tabelas com RLS indireto (modifier_options, product_modifier_groups,
-- cash_movements, order_items, order_item_modifiers) — as subqueries
-- já acessam tabelas pai com RLS atualizado, o bypass propaga naturalmente.
-- Não precisam de alteração.

-- -----------------------------------------------------------------------------
-- 7. Índices novos
-- -----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_tenants_plan_type      ON tenants (plan_type);
CREATE INDEX IF NOT EXISTS idx_tenants_last_activity  ON tenants (last_activity_at DESC NULLS LAST);
CREATE INDEX IF NOT EXISTS idx_audit_log_admin_action ON audit_log (is_admin_action, created_at DESC)
    WHERE is_admin_action = TRUE;
```

---

## Tarefa 2 — Novos ENUMs Java

**`modules/auth/entity/enums/UserRole.java`** — adicionar valor:
```java
public enum UserRole {
    ADMIN,
    MANAGER,
    CASHIER,
    SUPER_ADMIN    // ← adicionar
}
```

**`shared/tenant/enums/TenantPlan.java`** *(criar)*:
```java
package com.dipdv.shared.tenant.enums;

public enum TenantPlan {
    TRIAL,      // período de avaliação
    ACTIVE,     // assinatura ativa
    SUSPENDED   // suspenso por falta de pagamento ou violação
}
```

---

## Tarefa 3 — Constante UUID master

**`shared/security/MasterTenantConstants.java`** *(criar)*:
```java
package com.dipdv.shared.security;

import java.util.UUID;

/**
 * UUID reservado para o contexto do SUPER_ADMIN.
 * Nenhum tenant de cliente pode ter este UUID.
 * Verificado via @PrePersist em todas as entidades com tenant_id.
 */
public final class MasterTenantConstants {

    public static final UUID MASTER_TENANT_ID =
        UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

    public static final String MASTER_TENANT_ID_STR =
        "ffffffff-ffff-ffff-ffff-ffffffffffff";

    private MasterTenantConstants() {}

    public static boolean isMasterTenant(UUID tenantId) {
        return MASTER_TENANT_ID.equals(tenantId);
    }
}
```

---

## Tarefa 4 — Entidade Tenant (nova)

**`shared/tenant/entity/Tenant.java`** *(criar)*:
```java
package com.dipdv.shared.tenant.entity;

import com.dipdv.shared.tenant.enums.TenantPlan;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenants")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Tenant {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 60, unique = true)
    private String slug;

    @Column(name = "owner_email", length = 180)
    private String ownerEmail;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false, length = 20)
    @Builder.Default
    private TenantPlan planType = TenantPlan.TRIAL;

    @Column(name = "trial_until")
    private OffsetDateTime trialUntil;

    @Column(name = "last_activity_at")
    private OffsetDateTime lastActivityAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
```

**`shared/tenant/repository/TenantRepository.java`** *(criar)*:
```java
package com.dipdv.shared.tenant.repository;

import com.dipdv.shared.tenant.entity.Tenant;
import com.dipdv.shared.tenant.enums.TenantPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Tenant> findByActiveTrue();

    List<Tenant> findByPlanType(TenantPlan planType);

    // Atualiza last_activity_at — chamado pelo OrderService
    @Modifying
    @Query("UPDATE Tenant t SET t.lastActivityAt = :now WHERE t.id = :tenantId")
    void updateLastActivity(@Param("tenantId") UUID tenantId,
                            @Param("now") OffsetDateTime now);
}
```

---

## Tarefa 5 — Guard @PrePersist em User

Localizar `modules/auth/entity/User.java` e adicionar:

```java
import com.dipdv.shared.security.MasterTenantConstants;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.springframework.security.core.context.SecurityContextHolder;

// Dentro da classe User, adicionar os métodos:

@PrePersist
@PreUpdate
private void guardMasterTenant() {
    if (MasterTenantConstants.isMasterTenant(this.tenantId)) {
        // Verificar se quem está salvando é SUPER_ADMIN
        var auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isSuperAdmin = auth != null &&
            auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));

        if (!isSuperAdmin) {
            throw new SecurityException(
                "Operação não permitida: tenant_id reservado para SUPER_ADMIN"
            );
        }
    }
}
```

---

## Tarefa 6 — TenantContextService atualizado

Localizar `shared/tenant/TenantContextService.java` e adicionar o método:

```java
/**
 * Seta o contexto de SUPER_ADMIN no PostgreSQL.
 * OBRIGATÓRIO: Ambas as flags devem ser setadas na mesma transação.
 * O Kill Switch do RLS exige is_super_admin=true E current_tenant=masterUUID.
 *
 * @param targetTenantId UUID do tenant que o SUPER_ADMIN está acessando.
 *                       Use MasterTenantConstants.MASTER_TENANT_ID para
 *                       operações cross-tenant (ex: métricas globais).
 */
@Transactional
public void applyTenantContextSuperAdmin(UUID targetTenantId) {
    String tenantStr = MasterTenantConstants.MASTER_TENANT_ID_STR;
    String targetStr = sanitizeUuid(targetTenantId.toString());

    entityManager.createNativeQuery(
        "SET LOCAL app.is_super_admin = 'true'; " +
        "SET LOCAL app.current_tenant = '" + tenantStr + "'"
    ).executeUpdate();

    // Registrar no TenantContext para rastreabilidade
    TenantContext.set(targetTenantId);

    log.info("[SUPER_ADMIN] Contexto ativado — target_tenant={}",
        targetStr.equals(tenantStr) ? "GLOBAL" : targetStr);
}

private String sanitizeUuid(String uuid) {
    if (!UUID_PATTERN.matcher(uuid).matches()) {
        throw new IllegalArgumentException("UUID inválido: " + uuid);
    }
    return uuid;
}
```

---

## Tarefa 7 — JwtAuthFilter atualizado

Localizar `shared/security/JwtAuthFilter.java`, método `authenticateRequest`.

Após extrair o role do JWT, adicionar:

```java
// Se SUPER_ADMIN, setar flag no TenantContext para uso posterior
if ("SUPER_ADMIN".equals(role)) {
    TenantContext.set(MasterTenantConstants.MASTER_TENANT_ID);
    // Nota: applyTenantContextSuperAdmin() é chamado pelos controllers admin
    // não aqui no Filter — o Filter apenas marca o contexto Java
}
```

---

## Tarefa 8 — AuditAspect atualizado

Localizar `shared/audit/AuditAspect.java`, método `logAudit`.

Adicionar detecção de ação admin:

```java
boolean isAdminAction = MasterTenantConstants.isMasterTenant(tenantId);

AuditLog log = AuditLog.builder()
    .tenantId(tenantId)
    .userId(userId)
    .action(auditable.action().name())
    .entity(auditable.entity())
    .entityId(entityId)
    .payload(Map.of(
        "method", joinPoint.getSignature().getName(),
        "args", summarizeArgs(joinPoint.getArgs())
    ))
    .isAdminAction(isAdminAction)    // ← novo campo
    .build();
```

Adicionar campo na entidade `shared/audit/AuditLog.java`:
```java
@Column(name = "is_admin_action", nullable = false)
@Builder.Default
private boolean isAdminAction = false;
```

---

## Tarefa 9 — AuditAction atualizado

Localizar `shared/audit/AuditAction.java` e adicionar:

```java
// Ações SUPER_ADMIN
SUPER_ADMIN_TENANT_CREATED,
SUPER_ADMIN_TENANT_DEACTIVATED,
SUPER_ADMIN_TENANT_UPDATED,
SUPER_ADMIN_DATA_ACCESS,
SUPER_ADMIN_LOGIN
```

---

## Tarefa 10 — OrderService atualizado

Localizar `modules/order/service/OrderService.java`, método `closeOrder`.

Após setar `status = CLOSED`, adicionar:

```java
// Atualizar last_activity_at do tenant
tenantRepository.updateLastActivity(
    TenantContext.getRequired(),
    OffsetDateTime.now()
);
```

Injetar `TenantRepository` no `OrderService` via `@RequiredArgsConstructor`.

---

## Tarefa 11 — DataInitializer atualizado

Localizar `shared/config/DataInitializer.java` e adicionar criação do SUPER_ADMIN:

```java
private static final UUID MASTER_TENANT_ID = MasterTenantConstants.MASTER_TENANT_ID;
private static final String SUPER_ADMIN_EMAIL = "superadmin@dipdv.app";

// No método run(), após criar o admin dev:

// Criar SUPER_ADMIN (apenas se não existir)
if (!userRepository.existsByEmailAndTenantIdAndDeletedAtIsNull(
        SUPER_ADMIN_EMAIL, MASTER_TENANT_ID)) {

    User superAdmin = User.builder()
        .tenantId(MASTER_TENANT_ID)
        .email(SUPER_ADMIN_EMAIL)
        .passwordHash(passwordEncoder.encode("SuperAdmin@2025!"))
        .name("Super Admin DiPDV")
        .role(UserRole.SUPER_ADMIN)
        .active(true)
        .build();

    userRepository.save(superAdmin);

    log.info("╔══════════════════════════════════════════╗");
    log.info("║         SUPER ADMIN CRIADO               ║");
    log.info("╠══════════════════════════════════════════╣");
    log.info("║  email  : superadmin@dipdv.app           ║");
    log.info("║  senha  : SuperAdmin@2025!               ║");
    log.info("║  role   : SUPER_ADMIN                    ║");
    log.info("╚══════════════════════════════════════════╝");
}
```

---

## Tarefa 12 — Testes

**`test/shared/security/MasterTenantConstantsTest.java`**:
```java
// 2 cenários
isMasterTenant_whenMasterUuid_shouldReturnTrue()
isMasterTenant_whenRegularUuid_shouldReturnFalse()
```

**`test/shared/tenant/TenantContextServiceTest.java`** — adicionar:
```java
applyTenantContextSuperAdmin_shouldSetBothFlags()
applyTenantContextSuperAdmin_whenInvalidUuid_shouldThrow()
```

**`test/modules/auth/entity/UserEntityTest.java`** *(criar)*:
```java
guardMasterTenant_whenSuperAdmin_shouldAllow()
guardMasterTenant_whenRegularUser_shouldThrowSecurity()
```

---

## Tarefa 13 — Validação

```bash
cd backend
.\mvnw.cmd test
```
Esperado: todos os testes passando + 7 novos.

```bash
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

Verificar no console:
```
╔══════════════════════════════════════════╗
║         SUPER ADMIN CRIADO               ║
```

```bash
# Testar login do SUPER_ADMIN
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "ffffffff-ffff-ffff-ffff-ffffffffffff",
    "email": "superadmin@dipdv.app",
    "password": "SuperAdmin@2025!"
  }' | jq '{role: .role, tenantId: .tenantId}'
```
Esperado: `{"role": "SUPER_ADMIN", "tenantId": "ffffffff-..."}`

```bash
# Verificar que V8 rodou no Flyway
docker exec -it dipdv-postgres psql -U dipdv_app -d dipdv_dev \
  -c "SELECT version, description, success FROM flyway_schema_history
      ORDER BY installed_rank DESC LIMIT 3;"
```

```bash
# Verificar tenant master no banco
docker exec -it dipdv-postgres psql -U dipdv_app -d dipdv_dev \
  -c "SELECT id, name, plan_type FROM tenants WHERE id = 'ffffffff-ffff-ffff-ffff-ffffffffffff';"
```

```bash
# Testar guard @PrePersist — tentar criar user com UUID master sem ser SUPER_ADMIN
# (deve retornar erro de segurança)
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"tenantId":"00000000-0000-0000-0000-000000000001",
       "email":"admin@dipdv.dev","password":"dipdv@2025"}' \
  | jq -r .token)
```

---

## Tarefa 14 — Commit e PR

```bash
git add backend/src/main/resources/db/migration/V8__super_admin_setup.sql
git add backend/src/main/java/com/dipdv/shared/
git add backend/src/main/java/com/dipdv/modules/auth/entity/
git add backend/src/main/java/com/dipdv/modules/order/service/
git add backend/src/main/java/com/dipdv/shared/config/DataInitializer.java
git add backend/src/test/java/com/dipdv/shared/

git commit -m "feat(admin): infraestrutura SUPER_ADMIN — Migration V8 e segurança

- V8: tenant master ffffffff..., ENUMs tenant_plan e SUPER_ADMIN
- V8: RLS Kill Switch dupla verificação em todas as 10 tabelas
- V8: is_admin_action em audit_log, last_activity_at em tenants
- MasterTenantConstants: UUID master como constante única
- TenantPlan enum: TRIAL, ACTIVE, SUSPENDED
- Tenant entity + TenantRepository com updateLastActivity
- User @PrePersist/@PreUpdate: bloqueia UUID master sem SUPER_ADMIN
- TenantContextService: applyTenantContextSuperAdmin() com Kill Switch
- JwtAuthFilter: detecta SUPER_ADMIN e marca TenantContext
- AuditAspect: is_admin_action setado automaticamente
- AuditAction: novos valores SUPER_ADMIN_*
- OrderService: atualiza last_activity_at ao fechar pedido
- DataInitializer: cria superadmin@dipdv.app em perfil dev
- 7 novos testes de segurança"

git push origin feature/US-SA01-super-admin-infra
```

---

## Checklist

- [ ] V8 executada — Flyway history mostra success
- [ ] Tenant master `ffffffff...` visível no banco
- [ ] Login SUPER_ADMIN retorna `role: SUPER_ADMIN`
- [ ] Console exibe log do SUPER_ADMIN criado
- [ ] `is_admin_action` coluna existe em `audit_log`
- [ ] `plan_type` e `last_activity_at` existem em `tenants`
- [ ] Todos os testes passando (anteriores + 7 novos)
- [ ] PR aberto com link

---

## O que NÃO implementar aqui

- Endpoints `/api/v1/admin/**` — Prompt 2
- Frontend admin app — Prompt 3
- Lógica de restrição por plano — pós-MVP
- Self-service de cadastro de tenant — nunca neste MVP
