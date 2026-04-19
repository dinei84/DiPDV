# Prompt 2 — Sprint 4: Módulo Admin (Endpoints Cross-Tenant)

---

## Contexto

Infraestrutura V8 validada com 65 testes. Este prompt implementa os
endpoints do painel administrativo do SUPER_ADMIN.

**Branch:** `feature/US-SA01-super-admin-infra` (continuar na mesma)
**Commits:** `feat(admin): ...`

---

## Decisões de design — ler antes de começar

### Acesso cross-tenant
Todos os endpoints `/api/v1/admin/**` devem chamar
`tenantContextService.applyTenantContextSuperAdmin(MasterTenantConstants.MASTER_TENANT_ID)`
antes de qualquer query. Isso ativa o Kill Switch do RLS.

### Tenant SUSPENDED bloqueia login
No `AuthService.login()`, após buscar o usuário, verificar se o tenant
está `SUSPENDED`:
```java
Tenant tenant = tenantRepository.findById(request.tenantId())
    .orElseThrow(() -> new BusinessException("Tenant não encontrado", HttpStatus.NOT_FOUND));

if (tenant.getPlanType() == TenantPlan.SUSPENDED) {
    throw new BusinessException(
        "Conta suspensa. Entre em contato com o suporte.", HttpStatus.FORBIDDEN);
}
```

### Queries SQL nativas no AdminRepository
Mesma abordagem do `ReportRepository` — `EntityManager` com SQL nativo,
mapeamento manual para records. Sem `JpaRepository` neste repository.

### Segurança de todos os endpoints Admin
```java
@PreAuthorize("hasRole('SUPER_ADMIN')")
```
Nunca misturar com outras roles — endpoints Admin são exclusivos do SUPER_ADMIN.

---

## Estrutura a criar

```
modules/admin/
├── repository/
│   └── AdminRepository.java          ← queries cross-tenant via EntityManager
├── dto/
│   ├── TenantListResponse.java        ← lista leve (A)
│   ├── TenantSummaryResponse.java     ← detalhe 360º (B)
│   ├── GlobalStatsResponse.java       ← dashboard global (C)
│   ├── TenantMetricsResponse.java     ← métricas por tenant
│   ├── CreateTenantRequest.java       ← onboarding atômico
│   └── UpdateTenantStatusRequest.java ← trocar plano/status
├── service/
│   ├── AdminTenantService.java        ← CRUD de tenants + onboarding
│   └── AdminMetricsService.java       ← métricas e health check
└── controller/
    └── AdminController.java           ← todos os endpoints /admin/**

test/java/com/dipdv/modules/admin/service/
├── AdminTenantServiceTest.java
└── AdminMetricsServiceTest.java
```

---

## Tarefa 1 — AdminRepository

**Arquivo:** `modules/admin/repository/AdminRepository.java`

```java
@Repository
@RequiredArgsConstructor
public class AdminRepository {

    private final EntityManager entityManager;

    /**
     * Lista leve de todos os tenants (exceto o master).
     * Inclui last_activity_at para identificar churn.
     */
    @SuppressWarnings("unchecked")
    public List<TenantListResponse> listAllTenants() {
        return entityManager.createNativeQuery("""
            SELECT
                t.id,
                t.name,
                t.slug,
                t.owner_email,
                t.plan_type,
                t.active,
                t.last_activity_at,
                t.created_at,
                COUNT(DISTINCT u.id) AS user_count
            FROM tenants t
            LEFT JOIN users u ON u.tenant_id = t.id
                              AND u.deleted_at IS NULL
            WHERE t.id != 'ffffffff-ffff-ffff-ffff-ffffffffffff'
            GROUP BY t.id
            ORDER BY t.created_at DESC
        """)
        .getResultList()
        .stream()
        .map(row -> {
            Object[] r = (Object[]) row;
            return new TenantListResponse(
                UUID.fromString(r[0].toString()),
                r[1].toString(),
                r[2] != null ? r[2].toString() : null,
                r[3] != null ? r[3].toString() : null,
                r[4].toString(),
                (Boolean) r[5],
                r[6] != null ? ((java.sql.Timestamp) r[6]).toInstant()
                    .atOffset(java.time.ZoneOffset.UTC) : null,
                ((java.sql.Timestamp) r[7]).toInstant()
                    .atOffset(java.time.ZoneOffset.UTC),
                ((Number) r[8]).longValue()
            );
        })
        .toList();
    }

    /**
     * Visão 360º de um tenant específico.
     * Inclui dados cadastrais + métricas de uso.
     */
    public TenantSummaryResponse getTenantSummary(UUID tenantId) {
        Object[] tenant = (Object[]) entityManager.createNativeQuery("""
            SELECT
                t.id, t.name, t.slug, t.owner_email,
                t.plan_type, t.active, t.last_activity_at, t.created_at,
                COUNT(DISTINCT u.id)  AS user_count,
                COUNT(DISTINCT p.id)  AS product_count,
                COUNT(DISTINCT o.id)  AS order_count,
                COALESCE(SUM(pay.amount), 0) AS total_revenue
            FROM tenants t
            LEFT JOIN users u     ON u.tenant_id = t.id AND u.deleted_at IS NULL
            LEFT JOIN products p  ON p.tenant_id = t.id AND p.deleted_at IS NULL
            LEFT JOIN orders o    ON o.tenant_id = t.id AND o.status = 'CLOSED'
            LEFT JOIN payments pay ON pay.order_id = o.id AND pay.status = 'PAID'
            WHERE t.id = :tenantId
            GROUP BY t.id
        """)
        .setParameter("tenantId", tenantId)
        .getSingleResult();

        // Auditoria recente do tenant (últimas 10 ações)
        List<Object[]> recentAudit = entityManager.createNativeQuery("""
            SELECT action, entity, entity_id, created_at
            FROM audit_log
            WHERE tenant_id = :tenantId
              AND is_admin_action = FALSE
            ORDER BY created_at DESC
            LIMIT 10
        """)
        .setParameter("tenantId", tenantId)
        .getResultList();

        return TenantSummaryResponse.from(tenant, recentAudit);
    }

    /**
     * Dashboard global — faturamento e pedidos agrupados por tenant.
     * Query única com GROUP BY, sem iterar no Java.
     */
    @SuppressWarnings("unchecked")
    public GlobalStatsResponse getGlobalStats() {
        // Totais gerais
        Object[] totals = (Object[]) entityManager.createNativeQuery("""
            SELECT
                COUNT(DISTINCT t.id)                                AS tenant_count,
                COUNT(DISTINCT CASE WHEN t.active THEN t.id END)    AS active_tenant_count,
                COUNT(DISTINCT o.id)                                AS total_orders,
                COALESCE(SUM(p.amount), 0)                          AS total_revenue
            FROM tenants t
            LEFT JOIN orders o   ON o.tenant_id = t.id AND o.status = 'CLOSED'
            LEFT JOIN payments p ON p.order_id = o.id  AND p.status = 'PAID'
            WHERE t.id != 'ffffffff-ffff-ffff-ffff-ffffffffffff'
        """)
        .getSingleResult();

        // Métricas por tenant (últimos 30 dias) — para ranking
        List<Object[]> byTenant = entityManager.createNativeQuery("""
            SELECT
                t.id, t.name, t.plan_type,
                COUNT(DISTINCT o.id)       AS orders_30d,
                COALESCE(SUM(p.amount), 0) AS revenue_30d
            FROM tenants t
            LEFT JOIN orders o   ON o.tenant_id = t.id
                                 AND o.status = 'CLOSED'
                                 AND o.closed_at >= NOW() - INTERVAL '30 days'
            LEFT JOIN payments p ON p.order_id = o.id AND p.status = 'PAID'
            WHERE t.id != 'ffffffff-ffff-ffff-ffff-ffffffffffff'
            GROUP BY t.id
            ORDER BY revenue_30d DESC
            LIMIT 10
        """)
        .getResultList();

        return GlobalStatsResponse.from(totals, byTenant);
    }

    /**
     * Pedidos por tenant nos últimos 30 dias — health check de engajamento.
     */
    @SuppressWarnings("unchecked")
    public List<TenantMetricsResponse> getEngagementMetrics() {
        return entityManager.createNativeQuery("""
            SELECT
                t.id, t.name, t.plan_type, t.last_activity_at,
                COUNT(DISTINCT o.id)       AS orders_30d,
                COALESCE(SUM(p.amount), 0) AS revenue_30d,
                CASE
                    WHEN t.last_activity_at IS NULL THEN 'NEVER'
                    WHEN t.last_activity_at < NOW() - INTERVAL '30 days' THEN 'INACTIVE'
                    WHEN t.last_activity_at < NOW() - INTERVAL '7 days'  THEN 'AT_RISK'
                    ELSE 'ACTIVE'
                END AS engagement_status
            FROM tenants t
            LEFT JOIN orders o   ON o.tenant_id = t.id
                                 AND o.closed_at >= NOW() - INTERVAL '30 days'
            LEFT JOIN payments p ON p.order_id = o.id AND p.status = 'PAID'
            WHERE t.id != 'ffffffff-ffff-ffff-ffff-ffffffffffff'
            GROUP BY t.id
            ORDER BY orders_30d DESC
        """)
        .getResultList()
        .stream()
        .map(row -> {
            Object[] r = (Object[]) row;
            return new TenantMetricsResponse(
                UUID.fromString(r[0].toString()),
                r[1].toString(),
                r[2].toString(),
                r[3] != null ? ((java.sql.Timestamp) r[3]).toInstant()
                    .atOffset(java.time.ZoneOffset.UTC) : null,
                ((Number) r[4]).longValue(),
                ((Number) r[5]).doubleValue(),
                r[6].toString()
            );
        })
        .toList();
    }
}
```

---

## Tarefa 2 — DTOs

**`TenantListResponse.java`** — record com:
`id`, `name`, `slug`, `ownerEmail`, `planType`, `active`,
`lastActivityAt`, `createdAt`, `userCount`

**`TenantSummaryResponse.java`** — record com:
`id`, `name`, `slug`, `ownerEmail`, `planType`, `active`,
`lastActivityAt`, `createdAt`, `userCount`, `productCount`,
`orderCount`, `totalRevenue`, `recentAudit` (`List<AuditItem>`)

```java
// Record interno
public record AuditItem(
    String action,
    String entity,
    String entityId,
    OffsetDateTime createdAt
) {}

// Factory method
public static TenantSummaryResponse from(Object[] row, List<Object[]> audit) { ... }
```

**`GlobalStatsResponse.java`** — record com:
`tenantCount`, `activeTenantCount`, `totalOrders`, `totalRevenue`,
`topTenants` (`List<TenantRankItem>`)

```java
public record TenantRankItem(
    UUID id, String name, String planType,
    long orders30d, double revenue30d
) {}

public static GlobalStatsResponse from(Object[] totals, List<Object[]> byTenant) { ... }
```

**`TenantMetricsResponse.java`** — record com:
`id`, `name`, `planType`, `lastActivityAt`,
`orders30d`, `revenue30d`, `engagementStatus`

> `engagementStatus`: `ACTIVE`, `AT_RISK`, `INACTIVE`, `NEVER`
> Calculado na query SQL — não no Java.

**`CreateTenantRequest.java`** — record com:
```java
public record CreateTenantRequest(
    @NotBlank @Size(max = 120) String name,
    @NotBlank @Size(max = 60)  String slug,
    @NotBlank @Email           String ownerEmail,
    @NotBlank                  String ownerName,
    @NotBlank                  String ownerPassword
) {}
```

**`UpdateTenantStatusRequest.java`** — record com:
```java
public record UpdateTenantStatusRequest(
    @NotNull TenantPlan planType,
    Boolean active,
    String reason    // obrigatório quando active = false
) {}
```

---

## Tarefa 3 — AdminTenantService

**Arquivo:** `modules/admin/service/AdminTenantService.java`

```java
@Service @RequiredArgsConstructor @Slf4j
public class AdminTenantService {

    private final AdminRepository adminRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantContextService tenantContextService;
    private final AuditLogRepository auditLogRepository;

    /**
     * Lista todos os tenants com métricas básicas.
     * Ativa contexto SUPER_ADMIN antes das queries.
     */
    @Transactional(readOnly = true)
    public List<TenantListResponse> listTenants() {
        tenantContextService.applyTenantContextSuperAdmin(
            MasterTenantConstants.MASTER_TENANT_ID);
        return adminRepository.listAllTenants();
    }

    /**
     * Visão 360º de um tenant específico.
     */
    @Transactional(readOnly = true)
    public TenantSummaryResponse getTenantSummary(UUID tenantId) {
        guardNotMasterTenant(tenantId);
        tenantContextService.applyTenantContextSuperAdmin(
            MasterTenantConstants.MASTER_TENANT_ID);
        return adminRepository.getTenantSummary(tenantId);
    }

    /**
     * Onboarding atômico: cria tenant + primeiro usuário ADMIN.
     * Backend gera o UUID — nunca o frontend.
     * SUPER_ADMIN é obrigatório no SecurityContext para o @PrePersist passar.
     */
    @Transactional
    @Auditable(action = AuditAction.SUPER_ADMIN_TENANT_CREATED, entity = "tenants")
    public TenantListResponse createTenant(CreateTenantRequest request) {
        // Validar slug único
        if (tenantRepository.existsBySlug(request.slug())) {
            throw new BusinessException(
                "Slug já está em uso: " + request.slug(), HttpStatus.CONFLICT);
        }

        // Ativar contexto SUPER_ADMIN para o @PrePersist passar
        tenantContextService.applyTenantContextSuperAdmin(
            MasterTenantConstants.MASTER_TENANT_ID);

        // 1. Criar tenant (UUID gerado pelo banco via gen_random_uuid())
        Tenant tenant = Tenant.builder()
            .id(UUID.randomUUID())           // gerado no backend
            .name(request.name())
            .slug(request.slug())
            .ownerEmail(request.ownerEmail())
            .active(true)
            .planType(TenantPlan.TRIAL)
            .trialUntil(OffsetDateTime.now().plusDays(30))
            .build();

        tenantRepository.save(tenant);

        // 2. Criar primeiro usuário ADMIN do tenant
        // Usar JdbcTemplate para contornar o @PrePersist (owner é do tenant, não do master)
        // O @PrePersist bloqueia apenas tentativas de usar o UUID MASTER — não UUIDs novos
        User owner = User.builder()
            .tenantId(tenant.getId())
            .email(request.ownerEmail())
            .passwordHash(passwordEncoder.encode(request.ownerPassword()))
            .name(request.ownerName())
            .role(UserRole.ADMIN)
            .active(true)
            .build();

        userRepository.save(owner);

        log.info("[SUPER_ADMIN] Tenant criado: id={} slug={} owner={}",
            tenant.getId(), tenant.getSlug(), request.ownerEmail());

        return adminRepository.listAllTenants().stream()
            .filter(t -> t.id().equals(tenant.getId()))
            .findFirst()
            .orElseThrow();
    }

    /**
     * Atualiza plano e status do tenant.
     * SUSPENDED bloqueia login automaticamente via AuthService.
     */
    @Transactional
    @Auditable(action = AuditAction.SUPER_ADMIN_TENANT_UPDATED, entity = "tenants")
    public TenantListResponse updateTenantStatus(UUID tenantId,
                                                  UpdateTenantStatusRequest request) {
        guardNotMasterTenant(tenantId);

        if (Boolean.FALSE.equals(request.active())
                && (request.reason() == null || request.reason().isBlank())) {
            throw new BusinessException(
                "Motivo obrigatório ao desativar um tenant", HttpStatus.BAD_REQUEST);
        }

        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new BusinessException(
                "Tenant não encontrado", HttpStatus.NOT_FOUND));

        tenant.setPlanType(request.planType());
        if (request.active() != null) {
            tenant.setActive(request.active());
        }

        tenantRepository.save(tenant);

        log.info("[SUPER_ADMIN] Tenant atualizado: id={} plan={} active={}",
            tenantId, request.planType(), request.active());

        return adminRepository.listAllTenants().stream()
            .filter(t -> t.id().equals(tenantId))
            .findFirst()
            .orElseThrow();
    }

    /**
     * Desativa tenant — SUSPENDED bloqueia login de todos os usuários.
     */
    @Transactional
    @Auditable(action = AuditAction.SUPER_ADMIN_TENANT_DEACTIVATED, entity = "tenants")
    public void deactivateTenant(UUID tenantId, String reason) {
        guardNotMasterTenant(tenantId);

        if (reason == null || reason.isBlank()) {
            throw new BusinessException(
                "Motivo obrigatório para desativar tenant", HttpStatus.BAD_REQUEST);
        }

        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new BusinessException(
                "Tenant não encontrado", HttpStatus.NOT_FOUND));

        tenant.setActive(false);
        tenant.setPlanType(TenantPlan.SUSPENDED);
        tenantRepository.save(tenant);

        log.warn("[SUPER_ADMIN] Tenant SUSPENSO: id={} reason={}", tenantId, reason);
    }

    private void guardNotMasterTenant(UUID tenantId) {
        if (MasterTenantConstants.isMasterTenant(tenantId)) {
            throw new BusinessException(
                "Operação não permitida no tenant master", HttpStatus.FORBIDDEN);
        }
    }
}
```

---

## Tarefa 4 — AdminMetricsService

**Arquivo:** `modules/admin/service/AdminMetricsService.java`

```java
@Service @RequiredArgsConstructor @Slf4j
public class AdminMetricsService {

    private final AdminRepository adminRepository;
    private final TenantContextService tenantContextService;

    /**
     * Dashboard global — totais da plataforma + top 10 tenants.
     */
    @Transactional(readOnly = true)
    public GlobalStatsResponse getGlobalStats() {
        tenantContextService.applyTenantContextSuperAdmin(
            MasterTenantConstants.MASTER_TENANT_ID);
        return adminRepository.getGlobalStats();
    }

    /**
     * Health check de engajamento por tenant.
     * Identifica tenants inativos (churn) e em risco.
     * engagementStatus: ACTIVE | AT_RISK | INACTIVE | NEVER
     */
    @Transactional(readOnly = true)
    public List<TenantMetricsResponse> getEngagementMetrics() {
        tenantContextService.applyTenantContextSuperAdmin(
            MasterTenantConstants.MASTER_TENANT_ID);
        return adminRepository.getEngagementMetrics();
    }
}
```

---

## Tarefa 5 — AuthService atualizado

Localizar `modules/auth/service/AuthService.java`, método `login`.

Após buscar o usuário, adicionar verificação de tenant suspenso:

```java
// Verificar se tenant está suspenso (antes de validar a senha)
tenantRepository.findById(request.tenantId()).ifPresent(tenant -> {
    if (tenant.getPlanType() == TenantPlan.SUSPENDED) {
        throw new BusinessException(
            "Conta suspensa. Entre em contato com o suporte DiPDV.",
            HttpStatus.FORBIDDEN);
    }
    if (!tenant.isActive()) {
        throw new BusinessException(
            "Conta inativa. Entre em contato com o suporte DiPDV.",
            HttpStatus.FORBIDDEN);
    }
});
```

Injetar `TenantRepository` no `AuthService`.

---

## Tarefa 6 — AdminController

**Arquivo:** `modules/admin/controller/AdminController.java`

```
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Painel SUPER_ADMIN — gestão de tenants")
```

| Método | Path | Descrição |
|---|---|---|
| GET | `/tenants` | Lista todos os tenants com métricas básicas |
| GET | `/tenants/{id}/summary` | Visão 360º de um tenant |
| POST | `/tenants` | Criar tenant + primeiro usuário (onboarding atômico) |
| PATCH | `/tenants/{id}/status` | Atualizar plano e status |
| DELETE | `/tenants/{id}` | Suspender tenant (body: `{"reason": "..."}`) |
| GET | `/dashboard/stats` | Métricas globais da plataforma |
| GET | `/dashboard/engagement` | Health check de engajamento por tenant |

Todos com `@PreAuthorize("hasRole('SUPER_ADMIN')")`.

```java
// Exemplo do endpoint de criação
@PostMapping("/tenants")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Operation(summary = "Criar novo tenant com onboarding atômico")
@ApiResponses({
    @ApiResponse(responseCode = "201", description = "Tenant criado"),
    @ApiResponse(responseCode = "409", description = "Slug já em uso")
})
public ResponseEntity<TenantListResponse> createTenant(
        @Valid @RequestBody CreateTenantRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(adminTenantService.createTenant(request));
}
```

Adicionar rotas Admin como públicas no `SecurityConfig` — apenas para SUPER_ADMIN:
```java
// Já protegido por @PreAuthorize, mas garantir que não é bloqueado antes:
.requestMatchers("/api/v1/admin/**").authenticated()
```

---

## Tarefa 7 — Testes unitários

### AdminTenantServiceTest (6 cenários)

```java
createTenant_whenSlugAlreadyExists_shouldThrowConflict()
createTenant_whenValid_shouldCreateTenantAndOwner()
updateTenantStatus_whenSuspended_shouldSetPlanSuspended()
updateTenantStatus_whenDeactivatingWithoutReason_shouldThrowBadRequest()
deactivateTenant_whenMasterTenant_shouldThrowForbidden()
getTenantSummary_whenMasterTenant_shouldThrowForbidden()
```

### AdminMetricsServiceTest (2 cenários)

```java
getGlobalStats_shouldActivateSuperAdminContext()
getEngagementMetrics_shouldReturnStatusForEachTenant()
```

---

## Tarefa 8 — Validação com banco

```bash
cd backend
.\mvnw.cmd test
```
Esperado: mínimo **73 testes** (65 anteriores + 8 novos).

```bash
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

```bash
SA_TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "ffffffff-ffff-ffff-ffff-ffffffffffff",
    "email": "superadmin@dipdv.app",
    "password": "SuperAdmin@2025!"
  }' | jq -r .token)
```

**[1] Listar tenants:**
```bash
curl -s "http://localhost:8080/api/v1/admin/tenants" \
  -H "Authorization: Bearer $SA_TOKEN" | jq .
```
Esperado: lista com tenant dev `00000000-0000-0000-0000-000000000001`.

**[2] Dashboard global:**
```bash
curl -s "http://localhost:8080/api/v1/admin/dashboard/stats" \
  -H "Authorization: Bearer $SA_TOKEN" | jq .
```
Esperado: `tenantCount`, `totalOrders`, `totalRevenue`.

**[3] Engajamento:**
```bash
curl -s "http://localhost:8080/api/v1/admin/dashboard/engagement" \
  -H "Authorization: Bearer $SA_TOKEN" | jq .
```
Esperado: tenant dev com `engagementStatus` calculado.

**[4] Criar novo tenant (onboarding atômico):**
```bash
curl -s -X POST "http://localhost:8080/api/v1/admin/tenants" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $SA_TOKEN" \
  -d '{
    "name": "Lanchonete Teste",
    "slug": "lanchonete-teste",
    "ownerEmail": "dono@lanchonete.com",
    "ownerName": "João Dono",
    "ownerPassword": "Senha@123"
  }' | jq .
```
Esperado: 201 com o novo tenant.

**[5] Login com novo tenant:**
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "UUID_DO_NOVO_TENANT",
    "email": "dono@lanchonete.com",
    "password": "Senha@123"
  }' | jq '{role: .role, tenantId: .tenantId}'
```
Esperado: `role: "ADMIN"`.

**[6] Suspender tenant e testar bloqueio de login:**
```bash
NEW_TENANT_ID="UUID_DO_NOVO_TENANT"
curl -s -X DELETE "http://localhost:8080/api/v1/admin/tenants/$NEW_TENANT_ID" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $SA_TOKEN" \
  -d '{"reason": "Teste de suspensão"}' | jq .

# Login deve retornar 403
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "'$NEW_TENANT_ID'",
    "email": "dono@lanchonete.com",
    "password": "Senha@123"
  }' | jq .status
```
Esperado: `403`.

**[7] Acesso sem token SUPER_ADMIN → 403:**
```bash
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"tenantId":"00000000-0000-0000-0000-000000000001",
       "email":"admin@dipdv.dev","password":"dipdv@2025"}' \
  | jq -r .token)

curl -s "http://localhost:8080/api/v1/admin/tenants" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq .status
```
Esperado: `403`.

---

## Tarefa 9 — Commit e PR

```bash
git add backend/src/main/java/com/dipdv/modules/admin/
git add backend/src/main/java/com/dipdv/modules/auth/service/AuthService.java
git add backend/src/test/java/com/dipdv/modules/admin/

git commit -m "feat(admin): endpoints SUPER_ADMIN cross-tenant — Sprint 4 Fase 2

- AdminRepository: 4 queries SQL nativas cross-tenant (list, summary, stats, engagement)
- AdminTenantService: listar, criar, atualizar e suspender tenants
- AdminMetricsService: dashboard global e health check de engajamento
- AdminController: 7 endpoints /api/v1/admin/** exclusivos SUPER_ADMIN
- Onboarding atômico: tenant + owner em transação única
- AuthService: bloqueia login em tenants SUSPENDED ou inativos (403)
- engagementStatus calculado no SQL: ACTIVE|AT_RISK|INACTIVE|NEVER
- 8 novos testes (AdminTenantServiceTest + AdminMetricsServiceTest)

Closes #XX (US-SA01, US-SA02)"

git push origin feature/US-SA01-super-admin-infra
```

---

## Checklist final

- [ ] 73+ testes passando
- [ ] `GET /admin/tenants` → lista com tenant dev
- [ ] `GET /admin/dashboard/stats` → métricas globais
- [ ] `GET /admin/dashboard/engagement` → status por tenant
- [ ] `POST /admin/tenants` → 201 + tenant criado
- [ ] Login com novo tenant → `role: ADMIN`
- [ ] Suspender tenant → login retorna 403
- [ ] Admin normal tentando `/admin/**` → 403
- [ ] `audit_log` com `is_admin_action = true` após criação de tenant
- [ ] PR atualizado com link

---

## O que NÃO implementar aqui

- Frontend admin app — Prompt 3
- Lógica de restrição de features por plano — pós-MVP
- Relatório de erros/exceções cross-tenant — pós-MVP
- Impersonation (entrar como ADMIN de um cliente) — pós-MVP
