# Contexto para LLM (Claude)

Este documento fornece contexto mínimo para uso de LLMs ao trabalhar neste repositório. O objetivo é dar orientação suficiente sem expor dados sensíveis.

## Visão geral do projeto

DiPDV é um sistema de Ponto de Venda (PDV) para lanchonetes, com abordagem SaaS multi-tenant e foco mobile-first. O repositório é um monorepo com backend Java/Spring e frontend Next.js.

## Onde encontrar informação

- Arquitetura e banco: `docs/architecture/`
- Backlog e requisitos: `docs/backlog/`, `docs/requirements/`
- Sprints e relatórios: `docs/sprints/sprintN/`
- Prompts de desenvolvimento: `docs/prompts/`
- Setup e contribuição: `docs/setup/`

## Convenções úteis

- Documentos usam `snake_case` (relatórios de sprint usam `UPPER_SNAKE_CASE`).
- Sprints ficam em `docs/sprints/sprintN/`.
- Prompts ficam em `docs/prompts/general/` ou `docs/prompts/sprintN/`.
- Nunca criar arquivos soltos na raiz do projeto ou diretamente em `docs/`.

## Boas práticas ao atuar no repo

- Preserve a estrutura de pastas e nomes padronizados.
- Evite editar artefatos binários (`.pdf`, `.docx`) sem necessidade.
- Prefira atualizar markdowns e indicar mudanças relevantes nos índices.
- Ao criar documentos novos, atualize `docs/README.md`.

## Escopo esperado de respostas

- Se a tarefa envolver documentação, coloque o arquivo na pasta temática correta.
- Se criar novos documentos, registre-os no índice `docs/README.md`.
- Mantenha a linguagem em português, quando possível.
