# DiPDV — Documentação

Hub de documentação do projeto DiPDV. Use este índice para encontrar rapidamente arquitetura, requisitos, backlog, sprints e materiais de apoio.

---

## Mapa de Pastas

```
docs/
├── architecture/           Decisões arquiteturais e modelo de banco de dados
├── backlog/                Product Backlog MVP (PDF, DOCX, Markdown)
├── requirements/           Documento de requisitos (PDF, DOCX)
├── setup/                  Guia de setup e guia de contribuição
├── prompts/                Prompts usados nas sessões de desenvolvimento com IA
│   ├── general/            Prompts de alinhamento e configuração geral
│   ├── sprint0/            Prompts do Sprint 0 (Auth, setup inicial)
│   └── sprint1/            Prompts do Sprint 1 (Catalog, Modifiers)
├── sprints/                Relatórios, logs de execução e fechamentos por sprint
│   ├── sprint0/            Sprint 0 — Autenticação JWT
│   ├── sprint1/            Sprint 1 — Catalog, Modifiers, Order, Audit
│   └── sprint2/            Sprint 2 — CashRegister, Payment, NFC-e
├── claude.md               Contexto para LLM (lido automaticamente pelo Claude Code)
└── README.md               Este arquivo
```

---

## Índice de Conteúdo

### Arquitetura
- [`architecture/architecture.md`](architecture/architecture.md) — Decisões arquiteturais do sistema
- [`architecture/database.md`](architecture/database.md) — Modelo de banco de dados e migrações

### Backlog e Requisitos
- [`backlog/product_backlog_mvp_v1.0.pdf`](backlog/product_backlog_mvp_v1.0.pdf) — Product Backlog MVP v1.0
- [`backlog/product_backlog.litcoffee`](backlog/product_backlog.litcoffee) — Backlog em formato texto
- [`requirements/requisitos.pdf`](requirements/requisitos.pdf) — Documento de requisitos

### Setup
- [`setup/setup.md`](setup/setup.md) — Como rodar o projeto localmente
- [`setup/run_fullstack_local.md`](setup/run_fullstack_local.md) — Passo a passo para subir backend + frontend
- [`setup/contributing.md`](setup/contributing.md) — Guia de contribuição

### Sprints

#### Sprint 0 — Autenticação JWT
- [`sprints/sprint0/sprint0_conclusao.md`](sprints/sprint0/sprint0_conclusao.md) — Relatório de fechamento
- [`sprints/sprint0/sprint0_running.md`](sprints/sprint0/sprint0_running.md) — Como rodar e testar
- [`sprints/sprint0/errors_corrected_for_sprint0.md`](sprints/sprint0/errors_corrected_for_sprint0.md) — Bugs corrigidos

#### Sprint 1 — Catalog, Modifiers, Order, Audit
- [`sprints/sprint1/sprint1_running.md`](sprints/sprint1/sprint1_running.md) — Como rodar e testar
- [`sprints/sprint1/sprint1_conclusao.md`](sprints/sprint1/sprint1_conclusao.md) — Relatório de fechamento
- [`sprints/sprint1/sprint1_final.md`](sprints/sprint1/sprint1_final.md) — Relatório final do Catalog
- [`sprints/sprint1/PROMPT_SPRINT1_ORDER_FINAL.md`](sprints/sprint1/PROMPT_SPRINT1_ORDER_FINAL.md) — Relatório final do Order + AuditAspect
- [`sprints/sprint1/SPRINT1_VALIDACAO_US033_FINAL.md`](sprints/sprint1/SPRINT1_VALIDACAO_US033_FINAL.md) — Validação final US03.3

#### Sprint 2 — CashRegister, Payment, NFC-e
- [`sprints/sprint2/PROMPT_SPRINT2_CASHREGISTER_PAYMENT_NFCE.md`](sprints/sprint2/PROMPT_SPRINT2_CASHREGISTER_PAYMENT_NFCE.md) — Spec completa da sprint
- [`sprints/sprint2/PROMPT_SPRINT2_CASHREGISTER_PAYMENT_NFCE_running.md`](sprints/sprint2/PROMPT_SPRINT2_CASHREGISTER_PAYMENT_NFCE_running.md) — Relatório de execução

#### Sprint 3 — Relatórios, Dashboard e PDF
- [`sprints/sprint3/PROMPT_SPRINT3_REPORTS.md`](sprints/sprint3/PROMPT_SPRINT3_REPORTS.md) — Spec completa da sprint
- [`sprints/sprint3/sprint3_running.md`](sprints/sprint3/sprint3_running.md) — Relatório de execução e status atual

### Prompts de Desenvolvimento
- [`prompts/general/PROMPT_ALINHAMENTO_PROCESSO.md`](prompts/general/PROMPT_ALINHAMENTO_PROCESSO.md) — Alinhamento de processo
- [`prompts/general/prompt_antigravity_correcoes.md`](prompts/general/prompt_antigravity_correcoes.md) — Correções Docker + boot
- [`prompts/sprint0/prompt_antigravity_setup.md`](prompts/sprint0/prompt_antigravity_setup.md) — Setup inicial do projeto
- [`prompts/sprint0/prompt_sprint0_auth.md`](prompts/sprint0/prompt_sprint0_auth.md) — AuthController Sprint 0
- [`prompts/sprint0/prompt_sprint0_validacao_final.md`](prompts/sprint0/prompt_sprint0_validacao_final.md) — Validação final Sprint 0
- [`prompts/sprint1/prompt_sprint1_modifiers.md`](prompts/sprint1/prompt_sprint1_modifiers.md) — Modifiers Sprint 1
- [`prompts/sprint1/prompt_sprint1_validacao_modifiers.md`](prompts/sprint1/prompt_sprint1_validacao_modifiers.md) — Validação Modifiers

---

## Convenções

- Nomes de arquivos em `snake_case` (exceto siglas e relatórios em `UPPER_SNAKE_CASE`).
- Novos documentos vão na pasta temática correta — nunca soltos em `docs/`.
- Sprints sempre em `docs/sprints/sprintN/`.
- Prompts de desenvolvimento sempre em `docs/prompts/sprintN/` ou `docs/prompts/general/`.
- Relatórios de fechamento com sufixo `_conclusao.md` ou `_FINAL.md`.
