# Prompt — Validação do Banco Sprint 4 (V8)

## Contexto

Migration V8 implementada mas ainda não validada em runtime.
Esta validação é obrigatória antes de avançar para os endpoints Admin.

---

## Passo 1 — Subir ambiente

```bash
docker compose up -d
docker compose ps
```

Aguardar `postgres` com status `healthy`.

```bash
cd backend
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

---

## Passo 2 — Verificar Flyway

```bash
docker exec -it dipdv-postgres psql -U dipdv_app -d dipdv_dev \
  -c "SELECT version, description, success, execution_time
      FROM flyway_schema_history
      ORDER BY installed_rank DESC LIMIT 5;"
```

Esperado: V8 com `success = true`.

Se V8 aparecer com `success = false`:
```bash
# Ver o erro exato
docker exec -it dipdv-postgres psql -U dipdv_app -d dipdv_dev \
  -c "SELECT version, description, success, checksum
      FROM flyway_schema_history WHERE success = false;"
```
Colar o erro antes de prosseguir.

---

## Passo 3 — Verificar estrutura do banco

```bash
# Tenant master existe?
docker exec -it dipdv-postgres psql -U dipdv_app -d dipdv_dev \
  -c "SELECT id, name, slug, plan_type FROM tenants
      WHERE id = 'ffffffff-ffff-ffff-ffff-ffffffffffff';"
```
Esperado: 1 linha com `plan_type = ACTIVE`.

```bash
# Colunas novas em tenants existem?
docker exec -it dipdv-postgres psql -U dipdv_app -d dipdv_dev \
  -c "\d tenants" | grep -E "plan_type|last_activity|owner_email|slug"
```
Esperado: 4 colunas listadas.

```bash
# is_admin_action em audit_log existe?
docker exec -it dipdv-postgres psql -U dipdv_app -d dipdv_dev \
  -c "\d audit_log" | grep is_admin_action
```
Esperado: 1 linha com `boolean`.

```bash
# RLS Kill Switch ativo nas tabelas?
docker exec -it dipdv-postgres psql -U dipdv_app -d dipdv_dev \
  -c "SELECT tablename, policyname
      FROM pg_policies
      WHERE policyname = 'tenant_isolation'
      ORDER BY tablename;"
```
Esperado: 10+ tabelas listadas.

```bash
# SUPER_ADMIN no ENUM?
docker exec -it dipdv-postgres psql -U dipdv_app -d dipdv_dev \
  -c "SELECT enumlabel FROM pg_enum e
      JOIN pg_type t ON t.oid = e.enumtypid
      WHERE t.typname = 'user_role';"
```
Esperado: `ADMIN`, `MANAGER`, `CASHIER`, `SUPER_ADMIN`.

---

## Passo 4 — Validar login do SUPER_ADMIN

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "ffffffff-ffff-ffff-ffff-ffffffffffff",
    "email": "superadmin@dipdv.app",
    "password": "SuperAdmin@2025!"
  }' | jq '{role: .role, tenantId: .tenantId, name: .name}'
```
Esperado:
```json
{
  "role": "SUPER_ADMIN",
  "tenantId": "ffffffff-ffff-ffff-ffff-ffffffffffff",
  "name": "Super Admin DiPDV"
}
```

---

## Passo 5 — Validar guard @PrePersist

Tentar criar um usuário com o UUID master usando token de ADMIN normal
(deve retornar erro de segurança):

```bash
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"tenantId":"00000000-0000-0000-0000-000000000001",
       "email":"admin@dipdv.dev","password":"dipdv@2025"}' \
  | jq -r .token)

curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "ffffffff-ffff-ffff-ffff-ffffffffffff",
    "email": "tentativa@hack.com",
    "password": "qualquer"
  }' | jq .
```
Esperado: `401` ou erro de segurança — não deve criar usuário com UUID master.

---

## Passo 6 — Validar RLS Kill Switch

Com o token do SUPER_ADMIN, tentar acessar um endpoint normal:

```bash
SA_TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "ffffffff-ffff-ffff-ffff-ffffffffffff",
    "email": "superadmin@dipdv.app",
    "password": "SuperAdmin@2025!"
  }' | jq -r .token)

# SUPER_ADMIN acessando relatórios — deve funcionar (ou retornar vazio)
curl -s "http://localhost:8080/api/v1/reports/summary" \
  -H "Authorization: Bearer $SA_TOKEN" | jq .
```

---

## Passo 7 — Verificar log no console

No console da aplicação, confirmar que o seed imprimiu:
```
╔══════════════════════════════════════════╗
║         SUPER ADMIN CRIADO               ║
╠══════════════════════════════════════════╣
║  email  : superadmin@dipdv.app           ║
║  senha  : SuperAdmin@2025!               ║
║  role   : SUPER_ADMIN                    ║
╚══════════════════════════════════════════╝
```

---

## Passo 8 — Suite completa de testes com banco

```bash
.\mvnw.cmd test
```
Esperado: todos os testes passando, incluindo `contextLoads`.

Colar o output:
```
Tests run: XX, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## Checklist

- [ ] Flyway V8 → `success = true`
- [ ] Tenant master `ffffffff...` no banco
- [ ] 4 colunas novas em `tenants` confirmadas
- [ ] `is_admin_action` em `audit_log` confirmado
- [ ] 10+ tabelas com `tenant_isolation` policy
- [ ] `SUPER_ADMIN` no ENUM `user_role`
- [ ] Login SUPER_ADMIN → `role: SUPER_ADMIN`
- [ ] Guard @PrePersist bloqueando UUID master para não SUPER_ADMIN
- [ ] Log do seed visível no console
- [ ] Suite completa de testes passando

Colar output de cada comando antes de avançar para o Prompt 2.

---

## Se V8 falhar no Flyway

O erro mais provável é o `ALTER TYPE ... ADD VALUE` dentro de transação.
Se isso acontecer, a correção é dividir a migration:

```sql
-- Início do V8: executar FORA de transação
-- Adicionar no topo do arquivo:
-- flyway.executeInTransaction=false
```

Ou separar em `V8.1__add_enum_value.sql` (sem transação) e
`V8.2__super_admin_setup.sql` (com transação).

Reportar o erro exato para diagnóstico antes de aplicar qualquer correção.
