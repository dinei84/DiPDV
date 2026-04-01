# DiPDV
## Product Backlog — MVP v1.
### Sistema de PDV para Lanchonetes
Março 2025 · Versão 1.0 · Scrum / GitHub Projects

## Legenda e Convenções
Este documento segue a estrutura Épico → User Story → Task, padrão de mercado para gestão ágil com Scrum.

| Tipo      | Formato | ID     | Exemplo | Descrição |
|-----------|---------|--------|---------|-----------|
| Épico     | EP##    | EP01   |         | Agrupamento de funcionalidades por módulo |
| User Story| US##.#  | US01.1 |         | Funcionalidade do ponto de vista do usuário. Formato: Como [persona], quero [ação] para [objetivo] |
| Task      | T##.#.# | T01.1.1|         | Tarefa técnica vinculada a uma User Story. Prefixos: [BE] Backend · [FE] Frontend · [DB] Banco · [INFRA] DevOps |

| Prioridade | Cor     | Critério | Impacto no MVP |
|------------|---------|----------|----------------|
| 🔴 Alta    | Vermelho|          | Bloqueia o MVP se ausente. Obrigatório nas Sprints 0 e 1 |
| 🟡 Média   | Amarelo |          | Importante, mas não bloqueia o MVP inicial. Sprints 2 e 3 |
| 🟢 Baixa   | Verde   |          | Desejável, pode entrar em releases futuras. Pós-MVP |

## Planejamento de Sprints — MVP
O MVP é entregue em 4 sprints de 2 semanas cada, totalizando 8 semanas de desenvolvimento. Sprint 0 foca em infraestrutura, não entrega funcionalidades de usuário.

| Sprint    | Foco              | Entregas Principais | Duração   | Status |
|-----------|-------------------|---------------------|-----------|--------|
| Sprint 0  | Fundação técnica  | Repositório, CI/CD, estrutura Spring Boot, auth, RLS, multi-tenancy | 2 semanas | 🔲 A iniciar |
| Sprint 1  | PDV funcional     | Pedidos, modificadores, cancelamento, produtos, auditoria, roles | 2 semanas | 🔲 A iniciar |
| Sprint 2  | Caixa e Estoque   | Abertura/fechamento de caixa, movimentações, estoque básico, modificadores avançados | 2 semanas | 🔲 A iniciar |
| Sprint 3  | Relatórios e Ajustes | Dashboard, top produtos, faturamento por pagamento, relatório de caixa, ajuste de estoque | 2 semanas | 🔲 A iniciar |

## Matriz de Papéis e Permissões
Controle de acesso por perfil implementado via Spring Security com @PreAuthorize. Cada tenant possui seus próprios usuários e perfis.

| Funcionalidade          | ADMIN | MANAGER | CASHIER |
|-------------------------|-------|---------|---------|
| PDV / Pedidos           | ✅ Completo | ✅ Completo | ✅ Completo |
| Cancelar Pedido         | ✅ | ✅ | ❌ |
| Caixa (abertura/fechamento) | ✅ | ✅ | Apenas abertura |
| Relatórios              | ✅ | ✅ | ❌ |
| Produtos / Cardápio     | ✅ | ✅ | ❌ |
| Estoque                 | ✅ | ✅ (ajuste manual) | Apenas visualização |
| Auditoria               | ✅ (leitura) | ✅ (leitura) | ❌ |

## Product Backlog — Completo
Todos os Épicos, User Stories e Tasks do MVP organizados por módulo e sprint.

| ID      | Descrição | Tipo | Prioridade | Sprint | Critérios / Obs. |
|---------|-----------|------|------------|--------|------------------|
EP01 PDV e Vendas Épico (^) 🔴 Alta 1 – (^2)

US01.
Como operador, quero abrir um novo pedido e
adicionar itens do cardápio para registrar uma
venda
User Story (^) 🔴 Alta Sprint 1
Pedido criado com
status OPEN; itens
listados por
categoria; total
calculado
automaticamente
T01.1.1 ↳ [BE] POST /orders — criar pedido com tenant_id Task Backend Sprint 1 (^)
T01.1.2 (^) pedido↳^ [BE] POST /orders/{id}/items —^ adicionar item ao Task Backend Sprint 1 (^)
T01.1.3 (^) categorias↳^ [FE] Tela de PDV com grid de produtos e Task Frontend Sprint 1 (^)
T01.1.4 ↳ [FE] Componente de totalizador em tempo real Task Frontend Sprint 1 (^)
US01.2 Como operador, quero suportar modificadores por item para personalizar pedidos User Story (^) 🔴 Alta Sprint 1
Modificador
incrementa o valor;
grupo reutilizável
entre produtos;
máx/mín
configurável
T01.2.1 ↳ [BE] CRUD de grupos de modificadores Task Backend Sprint 1 (^)
T01.2.2 (^) acréscimo↳^ [BE] Vincular modificador ao item do pedido com Task Backend Sprint 1 (^)
T01.2.3 (^) adicionar item↳^ [FE] Modal de seleção de modificadores ao Task Frontend Sprint 1 (^)
US01.3 Como operador, quero editar ou remover itens antes de finalizar o pedido para corrigir erros User Story (^) 🔴 Alta Sprint 1
Só permitido com
status OPEN;
operação registrada
em audit_log
T01.3.1 (^) item↳^ [BE] PATCH /orders/{id}/items/{itemId} —^ editar Task Backend Sprint 1 (^)
T01.3.2 (^) item↳^ [BE] DELETE /orders/{id}/items/{itemId} —^ remover Task Backend Sprint 1 (^)
T01.3.3 ↳ [FE] Ação de editar/remover no carrinho do PDV Task Frontend Sprint 1 (^)

ID Descrição Tipo Prioridade Sprint Critérios /
Obs.
US01.4 Como operador, quero cancelar um pedido aberto para desfazer um registro incorreto User Story (^) 🔴 Alta Sprint 1
Status muda para
CANCELED; motivo
obrigatório;
registrado em
audit_log com
user_id
T01.4.1 (^) obrigatório↳^ [BE] PATCH /orders/{id}/cancel com motivo Task Backend Sprint 1 (^)
T01.4.2 (^) gravar audit_log↳^ [BE] AOP @Aspect —^ interceptar cancelamento e Task Backend Sprint 1 (^)
T01.4.3 (^) campo motivo↳^ [FE] Botão cancelar com modal de confirmação e Task Frontend Sprint 1 (^)

US01.
Como operador, quero que dois caixas não
editem o mesmo pedido simultaneamente para
evitar conflitos
User Story 🔴 Alta Sprint 1
Retorna HTTP 409
se versão divergir;
frontend exibe
mensagem clara
T01.5.1 (^) (Optimistic Locking)↳^ [BE] Campo @Version na entidade Order Task Backend Sprint 1 (^)
T01.5.2 (^) operador↳^ [FE] Tratamento de erro 409 com mensagem ao Task Frontend Sprint 1 (^)
EP02 Pagamentos e Caixa Épico (^) 🔴 Alta 1 – 2

US02.
Como operador, quero registrar o pagamento
de um pedido em dinheiro, cartão ou Pix para
finalizar a venda
User Story 🔴 Alta Sprint 1
Transação criada
com status
PENDING → PAID;
troco calculado para
dinheiro;
comprovante
gerado
T02.1.1 (^) idempotency_key↳^ [BE] POST /payments —^ registrar pagamento com Task Backend Sprint 1 (^)
T02.1.2 (^) FAILED, CANCELED, REFUNDED↳^ [BE] Enum PaymentStatus: PENDING, PAID, Task Backend Sprint 1 (^)
T02.1.3 (^) processar (evitar Pix duplicado)↳^ [BE] Validação de idempotency_key antes de Task Backend Sprint 1 (^)
T02.1.4 (^) pagamento e cálculo de troco↳^ [FE] Tela de checkout com seleção de forma de Task Frontend Sprint 1 (^)
T02.1.5 ↳ [FE] Geração de comprovante digital (PDF/tela) Task Frontend Sprint 2 (^)
US02.2 Como operador, quero abrir o caixa com saldo inicial para iniciar o turno User Story (^) 🔴 Alta Sprint 2
Um caixa por turno
por tenant; status
OPEN; registra
user_id e horário

ID Descrição Tipo Prioridade Sprint Critérios /
Obs.
T02.2.1 ↳ [BE] POST /cash-registers — abertura de caixa Task Backend Sprint 2 (^)
T02.2.2 (^) inicial↳^ [FE] Tela de abertura de caixa com campo saldo Task Frontend Sprint 2 (^)

US02.
Como operador, quero registrar entradas e
saídas manuais durante o turno para controle
de sangria/suprimento
User Story (^) 🔴 Alta Sprint 2
Tipo: SUPPLY ou
BLEEDING; valor e
descrição
obrigatórios;
impacta saldo do
caixa
T02.3.1 ↳ [BE] POST /cash-registers/{id}/movements Task Backend Sprint 2 (^)
T02.3.2 (^) caixa↳^ [FE] Formulário de sangria/suprimento no painel do Task Frontend Sprint 2 (^)
US02.4 Como gerente, quero fechar o caixa com resumo do turno para conferência financeira User Story 🔴 Alta Sprint 2
Exibe total em
dinheiro/cartão/Pix;
permite informar
saldo físico; grava
diferença; registra
em audit_log
T02.4.1 (^) fechamento com totalizadores↳^ [BE] PATCH /cash-registers/{id}/close —^ Task Backend Sprint 2 (^)
T02.4.2 (^) audit_log↳^ [BE] AOP —^ interceptar fechamento e gravar Task Backend Sprint 2 (^)
T02.4.3 (^) forma de pagamento↳^ [FE] Tela de fechamento de caixa com resumo por Task Frontend Sprint 2 (^)
EP03 Produtos e Cardápio Épico (^) 🔴 Alta 1
US03.1 Como admin, quero cadastrar, editar e inativar produtos para manter o cardápio atualizado User Story (^) 🔴 Alta Sprint 1
Campos: nome,
preço, categoria,
disponibilidade;
inativação não
exclui;
aparece/desaparece
no PDV
T03.1.1 ↳ [BE] CRUD /products com soft delete (ativo/inativo) Task Backend Sprint 1 (^)
T03.1.2 (^) ativo/inativo↳^ [FE] Tela de gerenciamento de produtos com toggle Task Frontend Sprint 1 (^)
US03.2 Como admin, quero organizar produtos em categorias para facilitar a navegação no PDV User Story 🔴 Alta Sprint 1
CRUD de
categorias; produto
pertence a uma
categoria; PDV filtra
por categoria

ID Descrição Tipo Prioridade Sprint Critérios /
Obs.
T03.2.1 ↳ [BE] CRUD /categories vinculado a produtos Task Backend Sprint 1 (^)
T03.2.2 ↳ [FE] Filtro de categorias na tela do PDV Task Frontend Sprint 1 (^)

US03.
Como admin, quero criar grupos de
modificadores reutilizáveis para agilizar o
cadastro de produtos
User Story 🟡 Média Sprint 2
Grupo tem nome,
mín/máx seleção e
lista de opções com
preço; reutilizável
em N produtos
T03.3.1 ↳ [BE] CRUD /modifier-groups com opções e preços Task Backend Sprint 2 (^)
T03.3.2 (^) (N:N)↳^ [BE] Vincular grupo de modificadores a produtos Task Backend Sprint 2 (^)
T03.3.3 (^) backoffice↳^ [FE] Gerenciamento de grupos de modificadores no Task Frontend Sprint 2 (^)
EP04 Estoque Básico Épico 🟡 Média 2 – 3
US04.1 Como admin, quero cadastrar o estoque por produto para controlar a quantidade disponível User Story (^) 🟡 Média Sprint 2
Quantidade inicial;
nível mínimo
configurável;
histórico de
movimentações
T04.1.1 (^) Product↳^ [BE] Campo stock_quantity e stock_min_level em Task Backend Sprint 2 (^)
T04.1.2 (^) produto↳^ [FE] Campo de estoque na tela de cadastro de Task Frontend Sprint 2 (^)

US04.
Como sistema, quero abater automaticamente 1
unidade do estoque a cada venda finalizada
para manter o controle em tempo real
User Story (^) 🟡 Média Sprint 2
Abate ocorre ao
mudar status do
pedido para
CLOSED; falha não
bloqueia venda
(apenas alerta)
T04.2.1 (^) fechamento do pedido↳^ [BE] Service de abate de estoque chamado no Task Backend Sprint 2 (^)
T04.2.2 (^) stock_min_level↳^ [BE] Evento de alerta quando stock_quantity <= Task Backend Sprint 2 (^)
US04.3 Como gerente, quero ajustar o estoque manualmente para registrar entradas e perdas User Story 🟡 Média Sprint 3
Tipo: ENTRY ou
LOSS; quantidade e
motivo obrigatórios;
registrado em
audit_log
T04.3.1 (^) com tipo e motivo↳^ [BE] POST /stock/movements —^ ajuste manual Task Backend Sprint 3 (^)

ID Descrição Tipo Prioridade Sprint Critérios /
Obs.
T04.3.2 ↳ [FE] Tela de ajuste manual de estoque Task Frontend Sprint 3 (^)
EP05 Relatórios e Dashboard Épico (^) 🟡 Média 3

US05.
Como gerente, quero visualizar o total de
vendas do dia e ticket médio para monitorar a
operação
User Story (^) 🟡 Média Sprint 3
Filtrável por data;
atualização a cada
carregamento;
exibido em
dashboard
responsivo
T05.1.1 (^) vendas e ticket médio↳^ [BE] GET /reports/summary?from=&to= —^ total de Task Backend Sprint 3 (^)
T05.1.2 (^) ticket médio↳^ [FE] Card de resumo no dashboard com totais e Task Frontend Sprint 3 (^)
US05.2 Como gerente, quero ver os itens mais vendidos por período para decisões de cardápio User Story (^) 🟡 Média Sprint 3
Top 10 itens;
filtrável por período;
ordenado por
quantidade
T05.2.1 (^) contagem↳^ [BE] GET /reports/top - products —^ ranking com Task Backend Sprint 3 (^)
T05.2.2 ↳ [FE] Gráfico/lista de top produtos no dashboard Task Frontend Sprint 3 (^)
US05.3 Como gerente, quero ver o faturamento por forma de pagamento para conciliação financeira User Story (^) 🟡 Média Sprint 3
Total e % por forma:
dinheiro, cartão, Pix;
filtrável por período
T05.3.1 (^) agrupado por PaymentMethod↳^ [BE] GET /reports/payments - by-method —^ Task Backend Sprint 3 (^)
T05.3.2 (^) forma de pagamento↳^ [FE] Gráfico de pizza ou barra com faturamento por Task Frontend Sprint 3 (^)
US05.4 Como gerente, quero visualizar o relatório de fechamento de caixa por turno para conferência User Story (^) 🟡 Média Sprint 3
Exibe saldo inicial,
movimentações,
total vendas,
diferença;
exportável
T05.4.1 (^) completo do turno↳^ [BE] GET /cash - registers/{id}/report —^ relatório Task Backend Sprint 3 (^)
T05.4.2 ↳ [FE] Tela de relatório de fechamento por turno Task Frontend Sprint 3 (^)
EP06 Multi-tenancy e Autenticação Épico (^) 🔴 Alta 0 – 1
US06.1 Como sistema, quero garantir o isolamento total de dados entre tenants via RLS no PostgreSQL User Story 🔴 Alta Sprint 0
RLS habilitado em
todas as tabelas;
tenant_id injetado

ID Descrição Tipo Prioridade Sprint Critérios /
Obs.
automaticamente;
teste de isolamento
obrigatório
T06.1.1 (^) todas as entidades↳^ [DB] Criar policies de RLS no PostgreSQL para Task Banco Sprint 0 (^)
T06.1.2 (^) tenant_id via Filter/Interceptor↳^ [BE] TenantContext —^ injeção automática de Task Backend Sprint 0 (^)
T06.1.3 (^) entre tenants↳^ [BE] Teste de integração validando isolamento Task Backend Sprint 0 (^)
US06.2 Como usuário, quero autenticar no sistema com e-mail e senha para acessar meu tenant User Story 🔴 Alta Sprint 0
JWT com claims
tenant_id e role;
refresh token; logout
invalida token
T06.2.1 ↳ [BE] POST /auth/login — JWT + refresh token Task Backend Sprint 0 (^)
T06.2.2 ↳ [BE] POST /auth/refresh — renovação de token Task Backend Sprint 0 (^)
T06.2.3 (^) JWT↳^ [FE] Tela de login com armazenamento seguro do Task Frontend Sprint 0 (^)

US06.
Como sistema, quero controlar o acesso por
perfil (ADMIN, MANAGER, CASHIER) para
proteger operações sensíveis
User Story 🔴 Alta Sprint 1
ADMIN: tudo;
MANAGER:
relatórios +
fechamento;
CASHIER: apenas
PDV; Spring
Security
@PreAuthorize
T06.3.1 (^) @PreAuthorize por endpoint↳^ [BE] Enum Role + Spring Security com Task Backend Sprint 1 (^)
T06.3.2 (^) frontend↳^ [FE] Controle de visibilidade de menus por role no Task Frontend Sprint 1 (^)
EP07 Auditoria e Segurança Épico (^) 🔴 Alta 1

US07.
Como sistema, quero registrar automaticamente
ações críticas (cancelamento, fechamento de
caixa, ajuste de estoque) para rastreabilidade
User Story 🔴 Alta Sprint 1
Log com: tenant_id,
user_id, action,
entity, entity_id,
payload JSON,
created_at; imutável
T07.1.1 (^) permissão de UPDATE/DELETE↳^ [BE] Criar tabela audit_log com RLS e sem Task Backend Sprint 1 (^)
T07.1.2 (^) fechamentos e ajustes↳^ [BE] @Aspect AOP interceptando cancelamentos, Task Backend Sprint 1 (^)

ID Descrição Tipo Prioridade Sprint Critérios /
Obs.
T07.1.3 (^) de logs↳^ [BE] Teste unitário do Aspect validando gravação Task Backend Sprint 1 (^)
EP08 Infraestrutura e Configuração Épico 🔴 Alta 0

US08.
Como dev, quero estruturar o repositório com
Git Flow e CI básico para garantir qualidade nas
entregas
User Story 🔴 Alta Sprint 0
Branches: main,
develop, feature/*;
CI roda build +
testes no push; PR
obrigatório para
develop
T08.1.1 (^) GitHub↳^ [INFRA] Configurar branch protection rules no Task DevOps Sprint 0 (^)
T08.1.2 (^) testes↳^ [INFRA] GitHub Actions: CI com build Maven + Task DevOps Sprint 0 (^)
T08.1.3 (^) em main↳^ [INFRA] Deploy automático no Railway ao merge Task DevOps Sprint 0 (^)

US08.
Como dev, quero a estrutura base do monolito
modular Spring Boot para começar o
desenvolvimento
User Story (^) 🔴 Alta Sprint 0
Módulos: pdv,
payment, inventory,
report, auth;
arquitetura em
camadas; Flyway
para migrations
T08.2.1 (^) pacotes↳^ [BE] Inicializar projeto Spring Boot 3 com módulos e Task Backend Sprint 0 (^)
T08.2.2 ↳ [BE] Configurar Flyway + datasource multi-tenant Task Backend Sprint 0 (^)
T08.2.3 ↳ [BE] Configurar Spring Security base + JWT filter Task Backend Sprint 0 (^)
T08.2.4 (^) estrutura de pastas↳^ [FE] Inicializar projeto Next.js 14 + Tailwind CSS + Task Frontend Sprint 0 (^)
T08.2.5 (^) em todas as tabelas↳^ [DB] Migration inicial: schema com tenant_id e RLS Task Banco Sprint 0 (^)
DiPDV · Product Backlog MVP v1.0 · Documento gerado em Março 2025 · Atualizar a cada Sprint Review