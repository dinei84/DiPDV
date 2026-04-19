# Prompt — Commit Sprint 4 Fase 2

## Tarefa única — commit seletivo e push

```bash
cd backend

# Verificar o que está pendente
git status
git diff --stat
```

Adicionar apenas os arquivos do módulo admin e a alteração no AuthService:

```bash
git add src/main/java/com/dipdv/modules/admin/
git add src/main/java/com/dipdv/modules/auth/service/AuthService.java
git add src/test/java/com/dipdv/modules/admin/

# Confirmar o que está em stage antes de commitar
git diff --cached --stat
```

Esperado no diff: 12 arquivos novos em `modules/admin/` + 1 arquivo modificado
`AuthService.java` + 2 arquivos de teste.

```bash
git commit -m "feat(admin): endpoints SUPER_ADMIN cross-tenant — Sprint 4 Fase 2

- AdminRepository: 4 queries SQL nativas cross-tenant (list, summary, stats, engagement)
- AdminTenantService: listar, criar, atualizar e suspender tenants
- AdminMetricsService: dashboard global e health check de engajamento
- AdminController: 7 endpoints /api/v1/admin/** exclusivos SUPER_ADMIN
- Onboarding atomico: tenant + owner em transacao unica
- AuthService: bloqueia login em tenants SUSPENDED ou inativos (403)
- engagementStatus calculado no SQL: ACTIVE|AT_RISK|INACTIVE|NEVER
- 8 novos testes (AdminTenantServiceTest + AdminMetricsServiceTest)
- 73/73 testes passando, smoke tests validados com banco

Closes #XX (US-SA01, US-SA02)"

git push origin feature/US-SA01-super-admin-infra
```

## Checklist

- [ ] `git diff --cached --stat` mostra apenas arquivos do módulo admin + AuthService
- [ ] Nenhum `application-dev.yml`, `.claude/` ou arquivo de IDE no stage
- [ ] Push feito para `feature/US-SA01-super-admin-infra`
- [ ] PR no GitHub atualizado automaticamente
