package com.dipdv.shared.tenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Serviço auxiliar para executar o SET LOCAL do RLS dentro de uma transação
 * gerenciada.
 *
 * POR QUE UM SERVICE SEPARADO?
 * O @Transactional em métodos protected de OncePerRequestFilter não funciona
 * com CGLIB.
 * O Spring não consegue criar proxy de filtros do Tomcat (contexto marcado como
 * final).
 * Extrair a lógica transacional para um @Service é o padrão correto para este
 * cenário.
 *
 * O TenantFilter delega para cá — a transação é aberta no Service e o
 * EntityManager
 * executa o SET LOCAL dentro dessa mesma transação, garantindo o escopo correto
 * para o RLS.
 */
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
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

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
     * "The SET command cannot use parameters" — limitação documentada do
     * PostgreSQL.
     */
    @Transactional
    public void applyTenantContext(UUID tenantId) {
        String tenantIdStr = tenantId.toString().toLowerCase();

        // Validação defensiva — UUID.toString() já garante o formato,
        // mas validamos explicitamente para tornar a segurança auditável
        if (!UUID_PATTERN.matcher(tenantIdStr).matches()) {
            throw new IllegalArgumentException(
                    "tenantId inválido — valor não corresponde ao padrão UUID: " + tenantIdStr);
        }

        entityManager.createNativeQuery(
                "SET LOCAL app.current_tenant = '" + tenantIdStr + "'").executeUpdate();

        log.debug("TenantContext aplicado: {}", tenantIdStr);
    }
}
