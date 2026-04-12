# Plano de Ação: Sprint 1 (Módulo Catalog)

Este documento descreve o plano de ação detalhado para executar a Sprint 1 do projeto DiPDV, cujo escopo é o Módulo Catalog (CRUD de Category e Product). Ele serve como contexto unificado para LLMs e desenvolvedores.

## 🚀 Fase 1: Preparação do Ambiente
1. **Branching:** A partir da branch `develop`, criar a nova branch para a feature atual: 
   `git checkout -b feature/US03.1-catalog-category-product`
2. **Revisão:** Garantir que o banco de dados via Docker (`dipdv-postgres`) e todas as configurações da Sprint 0 estejam em plena execução e sem pendências no projeto base (Backend).

## 🏗️ Fase 2: Criação das Entidades (JPA)
*Local: `modules/catalog/entity/`*
1. **Entidade `Category`:**
   - Mapear tabela `categories`.
   - Adicionar PK UUID, `tenantId` (sem relacionamento gerenciado, pois o RLS fará o isolamento de consultas).
   - Campos: `name`, `active`, `position`, `createdAt`, `updatedAt` (com timestamps atuando automaticamente).
   - *Nota:* Sem mapeamento bidirecional `@OneToMany` com produtos para evitar lazy loadings excessivos.
2. **Entidade `Product`:**
   - Mapear tabela `products` com PK UUID e `tenantId`.
   - Implementar soft delete usando um campo `deletedAt`.
   - Configurar chaves estrangeiras (`categoryId` com `ON DELETE SET NULL`).
   - Adicionar atributos com valores padrões devidamente mapeados com `@Builder.Default` (`active = true`, `stockQuantity = 0`).

## 🗄️ Fase 3: Camada de Repositórios (Spring Data JPA)
*Local: `modules/catalog/repository/`*
1. **`CategoryRepository`:**
   - Buscar categorias baseadas no `tenantId`, com listagem ativa (`active=true`) e ordenar por `position`.
   - Verificar duplicidades de nomes para prevenir o cadastro de itens idênticos no mesmo tenant.
2. **`ProductRepository`:**
   - Buscar produtos baseados no `tenantId`, listagem ativa e filtrando registros que não tenham sofrido 'Soft Delete' (`deletedAt IS NULL`).
   - Query personalizada (JPQL) para mapear produtos sob o nível mínimo de estoque para alertas.

## 📦 Fase 4: Camada de DTOs (Data Transfer Objects)
*Local: `modules/catalog/dto/`*
- Criação dos mapeamentos de entrada (`Request`) adicionando *Bean Validations* para interceptar regras ainda na entrada (`NotBlank`, `Min`, `DecimalMin`, `Size`).
- Criação dos mapeamentos de saída (`Response`) retornando os atributos necessários.
- *Regra estrita:* Nunca retornar o `tenantId` nos responses para evitar vazar contexto interno.

## ⚙️ Fase 5: Regras de Negócio e Serviços
*Local: `modules/catalog/service/`*
- Implementar as regras de acesso via `CatalogService`.
- Injetar dependências para os dois repositórios criados.
- Implementar lógicas específicas de negócio:
   - Validar se o nome já existe antes da criação. Se existir: disparar `BusinessException` (Http 409 Conflict).
   - Caso ID não seja encontrado, lançar `BusinessException` (Http 404).
   - Acionar *soft delete* via preenchimento de data/hora atual no campo `deletedAt`.

## 🛡️ Fase 6: Testes Unitários
*Local: `test/java/com/dipdv/modules/catalog/service/`*
- Adicionar ao menos **8 Cenários de Testes** (JUnit + Mockito) usando `@ExtendWith(MockitoExtension.class)`, garantindo total cobertura do `CatalogService`.
- Realizar mock de segurança do contexto locado (`TenantContext`) utilizando `mockStatic()` do Mockito 5.
- Testar caminhos felizes e validações de falhas (nome duplicado, deletes em produtos inexistentes, sucesso etc).

## 🌐 Fase 7: Controladores e Documentação de API
*Local: `modules/catalog/controller/`*
1. **`CategoryController` & `ProductController`:**
   - Especificar anotações de RestController com base paths `/api/v1/categories` e `/api/v1/products`.
   - Configurar o `@PreAuthorize` com checagens de Cargo ("CASHIER", "ADMIN", "MANAGER") nos endpoints adequados.
   - Habilitar Paginação com `Pageable`.
   - Documentar os Endpoints via anotações nativas do **Swagger (SpringDoc OpenAPI)** (200, 201, 400, 403 etc.).

## 🧪 Fase 8: Validação e Execução
1. Executar testes inteiros na suíte backend: `mvnw test`.
2. Confirmar a compilação do Maven: `mvnw compile`.
3. Rodar aplicação via perfil de desenvolvimento: `mvnw spring-boot:run -Dspring-boot.run.profiles=dev`.
4. *Testes manuais visando fumaça (Smoke Tests)* (via Postman / cURL para criação e exclusão, além da interface Swagger).

## 🚢 Fase 9: Commit e PR
1. `git commit -m "feat(catalog): implementar CRUD de categorias e produtos ... "`
2. Subir ao repositório origin (Push).
3. Gerar PR para a `develop`.
