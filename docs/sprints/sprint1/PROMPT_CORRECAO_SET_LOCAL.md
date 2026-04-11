# Prompt — Antigravity: Correção crítica SET LOCAL + Fechamento Sprint 1

---

## Contexto

A validação do Sprint 1 identificou **1 item que bloqueia o merge**:
concatenação de string no comando `SET LOCAL app.current_tenant`.

Este prompt corrige esse problema, roda os testes e fecha o PR.

---

## Tarefa 1 — Corrigir TenantContextService

### Problema atual (não aceito)
```java
// ERRADO — concatenação de string em SQL, mesmo com UUID
"SET LOCAL app.current_tenant = '" + tenantId + "'"
```

Concatenação de string em SQL é inaceitável por princípio —
independente da origem do dado. Se o padrão for aceito aqui,
ele se replica para outros lugares do código.

### Correção obrigatória

Localizar `TenantContextService.java` em `shared/tenant/`
e substituir o método `applyTenantContext` pela implementação abaixo:

```java
package com.dipdv.shared.tenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantContextService {

    /**
     * Padrão UUID válido — apenas hex e hífen.
     * Garante estruturalmente que nenhum caractere inválido
     * chegue ao comando SET LOCAL, tornando injeção impossível.
     */
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
    );

    private final jakarta.persistence.EntityManager entityManager;

    /**
     * Injeta o tenant_id na transação PostgreSQL atual via SET LOCAL.
     *
     * POR QUE NÃO USAR PARÂMETRO JDBC AQUI:
     * O comando SET do PostgreSQL não aceita parâmetros posicionais ($1).
     * O driver converte :tenantId para $1, que o SET rejeita com erro de sintaxe.
     *
     * SOLUÇÃO SEGURA:
     * Validar o UUID contra regex antes de interpolar — UUID tem charset
     * restrito (hex + hífen). Qualquer valor fora desse padrão lança exceção
     * antes de chegar ao SQL, tornando injeção estruturalmente impossível.
     *
     * REFERÊNCIA:
     * https://www.postgresql.org/docs/current/sql-set.html
     * "The SET command cannot use parameters" — limitação documentada do PostgreSQL.
     */
    @Transactional
    public void applyTenantContext(UUID tenantId) {
        String tenantIdStr = tenantId.toString().toLowerCase();

        // Validação defensiva — UUID.toString() já garante o formato,
        // mas validamos explicitamente para tornar a segurança auditável
        if (!UUID_PATTERN.matcher(tenantIdStr).matches()) {
            throw new IllegalArgumentException(
                "tenantId inválido — valor não corresponde ao padrão UUID: " + tenantIdStr
            );
        }

        entityManager.createNativeQuery(
            "SET LOCAL app.current_tenant = '" + tenantIdStr + "'"
        ).executeUpdate();

        log.debug("TenantContext aplicado: {}", tenantIdStr);
    }
}
```

### Por que essa solução é aceita

A interpolação permanece, mas agora é **blindada por duas camadas**:

1. `UUID tenantId` — o tipo Java já garante que só UUIDs válidos chegam ao método
2. `UUID_PATTERN.matcher()` — validação explícita e auditável antes do SQL

UUID tem charset de 36 caracteres: `[0-9a-f]` e `-`. É estruturalmente
impossível construir uma injeção SQL com esse charset. A validação torna
isso explícito e auditável no código.

---

## Tarefa 2 — Adicionar teste unitário para TenantContextService

**Arquivo:** `test/java/com/dipdv/shared/tenant/TenantContextServiceTest.java`

```java
package com.dipdv.shared.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantContextServiceTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @InjectMocks
    private TenantContextService tenantContextService;

    @Test
    void applyTenantContext_whenValidUuid_shouldExecuteSetLocal() {
        UUID tenantId = UUID.randomUUID();
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(0);

        tenantContextService.applyTenantContext(tenantId);

        // Verificar que o SQL executado contém o UUID correto
        verify(entityManager).createNativeQuery(
            "SET LOCAL app.current_tenant = '" + tenantId.toString().toLowerCase() + "'"
        );
        verify(query).executeUpdate();
    }

    @Test
    void applyTenantContext_whenNullUuid_shouldThrowException() {
        // UUID null não pode chegar aqui — o tipo Java garante,
        // mas documentamos o comportamento esperado
        assertThrows(NullPointerException.class, () ->
            tenantContextService.applyTenantContext(null)
        );
    }
}
```

---

## Tarefa 3 — Rodar todos os testes

```bash
cd backend
.\mvnw.cmd test
```

**Colar o output completo no relatório:**
```
[INFO] Tests run: X, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

O número total de testes deve ser **20 ou mais**
(18 anteriores + 2 novos do TenantContextServiceTest).

---

## Tarefa 4 — Abrir o PR

Acessar o link abaixo e abrir o Pull Request:

```
https://github.com/dinei84/DiPDV/compare/develop...feature/US03.3-modifier-groups
```

**Título do PR:**
```
feat(catalog): ModifierGroup + ModifierOption — Sprint 1 US03.3
```

**Descrição do PR:**
```markdown
## O que este PR faz
Implementa o sistema de modificadores do cardápio:
grupos de personalização (ex: "Ponto da carne") e opções
com suporte a quantidade (ex: "2x Bacon").

## User Story
Closes #XX (US03.3)

## Tipo de mudança
- [x] Nova funcionalidade

## Evidências
- 10 testes unitários passando (ModifierServiceTest)
- 2 testes unitários do TenantContextService
- 7 smoke tests validados com outputs reais
- Fetch join confirmado: 1 query SQL para produto + grupos + opções

## Checklist
- [x] Testes passando
- [x] Swagger atualizado
- [x] Migration V4 executada
- [x] Bug SET LOCAL corrigido com validação UUID
- [x] Sem segredos ou dados sensíveis no código
```

---

## Checklist desta tarefa

- [ ] `TenantContextService` corrigido com validação UUID_PATTERN
- [ ] `TenantContextServiceTest` criado com 2 cenários
- [ ] `.\mvnw.cmd test` — output colado (mínimo 20 testes)
- [ ] BUILD SUCCESS confirmado
- [ ] PR aberto com link colado no relatório

---

## O que NÃO fazer

- Não avançar para o módulo Order antes deste PR ser aprovado
- Não usar `@Transactional` diretamente em Filters
  (já aprendemos esse bug no Sprint 0)
- Não remover a validação UUID_PATTERN achando que é desnecessária
