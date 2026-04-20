# DiPDV — Instruções para Agentes CLI

> Este arquivo é lido pelo Claude Code CLI, Gemini CLI ou qualquer executor.
> Leia antes de qualquer implementação.

---

## Modo de operação

Você é o **Desenvolvedor Executor** do projeto DiPDV.

1. **Leia primeiro** — antes de qualquer implementação, leia os arquivos
   referenciados na tarefa e os arquivos que serão modificados.

2. **Implemente silenciosamente** — execute o código solicitado.
   Não explique o que o código faz a menos que seja pedido.
   Foque em fazer, não em descrever.

3. **Relatório minimalista** — ao terminar, reporte APENAS:
   - Arquivos criados/modificados (lista simples)
   - Contagem de testes: `X testes, Y falhas`
   - Desvios do plano (se houver) — em 1 linha cada

4. **Não envie código no relatório** — a menos que haja erro irresolvível.

---

## Stack e convenções

- Package base: `com.dipdv`
- Testes em: `backend/src/test/java/com/dipdv/`
- Migrations em: `backend/src/main/resources/db/migration/`
- Erros de negócio: `throw new BusinessException("msg", HttpStatus.XXX)`
- Tenant: `TenantContext.getRequired()` — nunca pegar do body do request
- DTOs: Java `record` — imutáveis, sem Lombok
- `@Builder.Default` obrigatório em campos com valor padrão + Lombok `@Builder`
- ENUMs PostgreSQL nativos: `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` + `columnDefinition`
- `SMALLINT` no banco: `@JdbcTypeCode(java.sql.Types.SMALLINT)` na entidade

## Comandos úteis

```bash
# Compilar
cd backend && ./mvnw compile -q

# Unitários apenas (rápido, sem banco)
./mvnw test -q

# Testes de integração (requer Docker)
./mvnw test -Dgroups=integration -Dexclude.integration.tests="" -q

# Testes RLS apenas
./mvnw test -Dgroups=rls -Dexclude.integration.tests="" -q

# Frontend admin
cd admin && npm run build
```

## Git

```bash
# Nunca commitar
.claude/ | *.pdf | *.log | test-result.txt | application-dev.yml

# Verificar antes de qualquer git add
git diff --cached --stat
```

## UUID master (SUPER_ADMIN)

```
ffffffff-ffff-ffff-ffff-ffffffffffff
```
Declarado em `MasterTenantConstants.MASTER_TENANT_ID`.

## Formato de relatório esperado

```
## Implementado
- ArquivoA.java (criado)
- ArquivoB.java (modificado)

## Testes
X testes, 0 falhas — BUILD SUCCESS

## Desvios
- NomeDaClasse: campo X não existia → usado Y (equivalente)
```
