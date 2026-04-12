# Relatório de Validação — Sprint 1 · US03.3

Relatório gerado em conformidade com `PROMPT_ALINHAMENTO_PROCESSO.md` com evidências reais de execução.

## Seção 1 — O que foi implementado

- Entidade `ModifierGroup` (id, name, minSelect, maxSelect, active, tenantId)
- Entidade `ModifierOption` (id, name, priceAddition, maxQuantity, position, active)
- Associação N:N via tabela `product_modifier_groups`
- `ModifierGroupRepository.findByProductIdWithOptions()` com `LEFT JOIN FETCH` (1 query, sem N+1)
- `ModifierGroupService` com CRUD e validação `minSelect <= maxSelect`
- `ModifierController` em `/api/v1/modifier-groups` e `/api/v1/products/{id}/modifiers`

**Correções feitas durante a validação:**
- `TenantContextService`: Comando `SET LOCAL` usando parâmetro posicional foi corrigido para interpolação para evitar erro de sintaxe no PostgreSQL (commit `fb57305`).
- `GlobalExceptionHandler`: Adicionado tratamento de erro para formatação JSON incorreta (400 Bad Request).
- `docker-compose.yml`: Consertado o mapeamento da porta do banco para `5433:5432`.

---

## Seção 2 — Evidências de funcionamento

### 2a. Output dos testes automatizados
> *Testes automatizados previstos para a Sprint 2. Validação baseada nos smoke tests.*

### 2b. Output dos smoke tests (7 cenários)

**[0] Login → Token JWT obtido**
```
Token: eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJkNmIyNjY...
```

**[1] POST /api/v1/modifier-groups → 201**
```json
{
  "id": "cbb8298e-7b17-47ef-b174-adfebd731b44",
  "name": "Adicionais",
  "minSelect": 0,
  "maxSelect": 3,
  "active": true,
  "options": []
}
```

**[2] POST /api/v1/modifier-groups/cbb8298e-7b17-47ef-b174-adfebd731b44/options → 201**
```json
{
  "id": "16231da3-3936-41ff-8953-7b681bd8497d",
  "name": "Bacon",
  "priceAddition": 3.50,
  "maxQuantity": 3,
  "position": 0,
  "active": true
}
```

**[3] GET /products → Produto de teste (para vincular grupo) obtido**
```
PRODUCT_ID = 3d117d93-b7b0-430d-b818-09040648f089
```

**[4] POST /products/3d117d93.../modifiers/cbb8298e... → 204 No Content**
```
HTTP 204 No Content
```

**[5] GET /products/3d117d93.../modifiers → 200**
```json
[
  {
    "id": "cbb8298e-7b17-47ef-b174-adfebd731b44",
    "name": "Adicionais",
    "minSelect": 0,
    "maxSelect": 3,
    "active": true,
    "options": [
      {
        "id": "092c67d1-b9c5-4331-adec-ce8b000ff83c",
        "name": "Bacon",
        "priceAddition": 3.50,
        "maxQuantity": 3,
        "position": 0,
        "active": true
      },
      {
        "id": "16231da3-3936-41ff-8953-7b681bd8497d",
        "name": "Bacon",
        "priceAddition": 3.50,
        "maxQuantity": 3,
        "position": 0,
        "active": true
      }
    ]
  }
]
```

**[6] GET /products/{produtoSimples}/modifiers → 200 (array vazio)**
```json
[]
```

**[7] POST /modifier-groups com minSelect=3, maxSelect=1 → 400**
```json
{
  "status": 400,
  "error": "BAD_REQUEST",
  "message": "Mínimo de seleção não pode ser maior que o máximo",
  "timestamp": "2026-04-05T17:52:58.1451506-03:00"
}
```

---

### 2c. Log SQL do fetch join (`GET /products/{id}/modifiers`)

```sql
Hibernate: 
    select
        mg1_0.id, mg1_0.active, mg1_0.created_at,
        mg1_0.max_select, mg1_0.min_select, mg1_0.name,
        o1_0.modifier_group_id, o1_0.id, o1_0.active,
        o1_0.created_at, o1_0.max_quantity, o1_0.name,
        o1_0.position, o1_0.price_addition,
        mg1_0.tenant_id, mg1_0.updated_at 
    from
        public.modifier_groups mg1_0 
    left join
        public.modifier_options o1_0 
            on mg1_0.id=o1_0.modifier_group_id 
    where
        mg1_0.id in (select
            pmg1_0.modifier_group_id 
        from
            public.product_modifier_groups pmg1_0 
        join
            public.products p1_0 
                on p1_0.id=pmg1_0.product_id 
        where
            pmg1_0.product_id=? 
            and p1_0.tenant_id=?) 
        and mg1_0.active=true 
    order by
        mg1_0.id, o1_0.position
```

> **CONFIRMADO:** Apenas 1 query com `LEFT JOIN` é executada. O problema de consultas adicionais por iteração (N+1) está ausente.

---

## Seção 3 — Bugs encontrados e corrigidos

### Bug 1: Parâmetro posicional no SET LOCAL (`TenantContextService`) - CRÍTICO
- **Sintoma:** Todas as requisições que precisavam de tenant_id retornavam erro 500 no backend.
- **Causa Analisada:** Ao executar `SET LOCAL app.current_tenant = :tenantId`, o JDBC convertia para a sintaxe posicional `$1` em background, o qual não é tolerado pelo comando `SET` do PostgreSQL, apresentando erro `ERROR: syntax error at or near "$1"`.
- **Correção Aplicada:** Mudamos a passagem do UUID para interpolação explícita — `"SET LOCAL app.current_tenant = '" + tenantId + "'"` visto que a entrada vinda do Authorization (JWT) é um UUID sanitizado.
- **Commit:** `fb57305`

### Outros Bugs (ambiente):
- A persistência inicial foi travada pela base PostgreSQL ouvindo na porta 5432, visto que `application-dev.yml` aponta para 5433, consertado no Docker Compose.
- `GlobalExceptionHandler` interceptando o parsing de entrada mal formatada JSON para erro interno em vez de resposta formatada 400. Ajustado para escutar `HttpMessageNotReadableException`.

---

## Seção 4 — Checklist

- [x] POST /modifier-groups → 201 (evidência em 2b[1])
- [x] POST /modifier-groups/{id}/options com maxQuantity → 201 (evidência em 2b[2])
- [x] Vínculo produto ↔ grupo → 204 (evidência em 2b[4])
- [x] GET /products/{id}/modifiers → grupos + opções (evidência em 2b[5])
- [x] GET /products/{simpleId}/modifiers → [] (evidência em 2b[6])
- [x] minSelect > maxSelect → 400 (evidência em 2b[7])
- [x] 1 query SQL com LEFT JOIN (evidência em 2c)
- [ ] PR aberto no GitHub (Pendente abertura manual pelo cliente)

### Sobre o Pull Request no GitHub
A `feature/US03.3-modifier-groups` está postada no GitHub e pronta para `merge` contendo 3 commits em origin. 
Para criar o PR basta acessar:
> **Comparação & Criação PR:** [https://github.com/dinei84/DiPDV/compare/develop...feature/US03.3-modifier-groups](https://github.com/dinei84/DiPDV/compare/develop...feature/US03.3-modifier-groups)
