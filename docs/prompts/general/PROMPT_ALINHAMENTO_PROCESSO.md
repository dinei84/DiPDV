# Prompt — Antigravity: Alinhamento de Processo

---

## Contexto

Antes de continuar com o módulo Order, precisamos alinhar
como as entregas devem ser documentadas neste projeto.

Os últimos relatórios descrevem o que foi implementado,
mas não provam que funciona em runtime. No Scrum, um item
só é considerado **Done** quando há evidência de execução real —
não apenas descrição da implementação.

A partir de agora, todo relatório de conclusão deve seguir
o padrão abaixo.

---

## Padrão de relatório obrigatório

Todo relatório de sprint ou tarefa deve conter:

### Seção 1 — O que foi implementado
Descrição técnica do que foi feito. *(já está sendo feito corretamente)*

### Seção 2 — Evidências de funcionamento

**2a. Output dos testes automatizados**
Colar o trecho real do terminal:
```
[INFO] Tests run: X, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**2b. Output dos smoke tests (curl)**
Colar o JSON real retornado por cada endpoint testado.
Não descrever — colar o output.

Exemplo do que é aceito:
```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "name": "Adicionais",
  "minSelect": 0,
  "maxSelect": 3
}
```

Exemplo do que NÃO é aceito:
```
"Criação de Grupo: Validado nome duplicado e limites de seleção."
```

**2c. Evidência de comportamento crítico**
Para funcionalidades com critério de aceitação específico
(ex: "1 query SQL", "retorna 403", "deleted_at preenchido"),
colar o trecho de log ou screenshot que comprova.

### Seção 3 — Bugs encontrados e corrigidos
Manter o padrão atual — está bom.

### Seção 4 — Checklist
Cada item marcado apenas se houver evidência na Seção 2.

---

## Tarefa imediata — Reexecutar validação do Sprint 1 Modifiers

Executar o arquivo `PROMPT_SPRINT1_VALIDACAO_MODIFIERS.md`
que está no repositório e entregar o relatório no novo padrão.

Os três itens pendentes são:

**1. PR no GitHub**
Confirmar que `feature/US03.3-modifier-groups` → `develop`
está aberto. Colar o link do PR.

**2. Outputs dos curls**
Executar os 7 comandos do prompt de validação e colar
cada response JSON completo.

**3. Log SQL do fetch join**
Ao chamar `GET /products/{productId}/modifiers`,
copiar as linhas `Hibernate: select...` do console
e colar no relatório.

Esperado — 1 query com JOIN:
```sql
Hibernate: select mg1_0.id, mg1_0.active, mg1_0.max_select,
           mg1_0.min_select, mg1_0.name, mg1_0.tenant_id,
           o1_0.modifier_group_id, o1_0.id, o1_0.active,
           o1_0.max_quantity, o1_0.name, o1_0.position,
           o1_0.price_addition
           from modifier_groups mg1_0
           left join modifier_options o1_0
           on mg1_0.id=o1_0.modifier_group_id
           where mg1_0.id in (select ...)
```

Se aparecerem múltiplas queries separadas para `modifier_options`,
reportar o trecho exato — corrigiremos antes de avançar.

---

## Por que isso importa

No mercado, um Pull Request sem evidências de teste
é devolvido pelo revisor antes do merge.

No projeto DiPDV, o tech lead (Claude) revisa cada entrega
antes de declarar a sprint fechada. Relatórios sem evidências
travam o progresso — exatamente como aconteceu agora.

Adotar esse padrão desde já prepara para o fluxo real
de qualquer empresa de desenvolvimento.

---

## Checklist desta tarefa

- [ ] Novo padrão de relatório entendido e confirmado
- [ ] PR `feature/US03.3-modifier-groups` → `develop` — link colado
- [ ] 7 outputs de curl colados com JSON real
- [ ] Log SQL do fetch join colado — 1 query confirmada
