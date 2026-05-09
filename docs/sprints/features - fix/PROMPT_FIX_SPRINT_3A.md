# Prompt — Correções pós-validação Sprint 3a

Branch: continuar em `feature/pdv-modules-consumer`.

---

## Contexto

A validação manual do usuário detectou problemas. Sua implementação
**não compilou** quando rodou `npm run dev`. Isso significa que
você declarou "Fase 1 completa" sem executar build local — vimos o
mesmo padrão acontecer em sprints anteriores e precisa parar.

Adicionalmente, uma decisão de produto precisa ser revertida.

---

## Correções obrigatórias

### 1. CRÍTICO — Erro de compilação em `useModules`

**Erro:**
```
./src/lib/hooks/useModules.ts:49:7
Expected '>', got 'ident'
```

**Causa:** o arquivo está com extensão `.ts` mas contém JSX
(`<ModulesContext.Provider>`). Parser TypeScript trata `<` como
operador, não como abertura de tag.

**Correção:**
- Renomear `frontend/src/lib/hooks/useModules.ts` → `useModules.tsx`
- Atualizar **todos** os imports que apontam para esse caminho.
  Provavelmente em `frontend/src/app/(pdv)/layout.tsx` e talvez
  em `ModuleGate.tsx` ou outros consumidores.
- Confirmar via `grep` no projeto que nenhum import quebrado
  permaneceu.

### 2. Reverter mensagem específica de SUPER_ADMIN no login

A versão atual do `AuthGuard` no PDV redireciona SUPER_ADMIN para
`/login?error=use_admin_panel` com mensagem específica. Decisão
revisada — isso vaza informação:

**Risco de segurança:** mensagem específica confirma que o email
existe E é SUPER_ADMIN no sistema. Permite enumeração de contas
privilegiadas por força bruta de emails.

**Correção:**
- `AuthGuard.tsx`: SUPER_ADMIN → ainda redireciona para `/login`,
  ainda chama `clearAuth()`, mas SEM query param de erro
  específico. Trate igual qualquer "role não permitida".
- `(auth)/login/page.tsx`: remover a leitura de
  `?error=use_admin_panel` e a mensagem associada. Mantém apenas
  mensagens genéricas tipo "Credenciais inválidas" quando login
  falhar normalmente.

Note que o admin (porta 3001) **mantém** o seu padrão atual
(`?error=forbidden` + "Acesso restrito a SUPER_ADMIN") porque lá
o cenário é diferente: usuário ADMIN tentou logar num painel
SUPER_ADMIN, e a mensagem ajuda a corrigir o erro de canal sem
expor info sensível.

### 3. Registrar dívidas técnicas em `PROJECT_STATE.md`

Adicionar duas dívidas técnicas:

```markdown
- **`frontend/src/app/page.tsx`** — Raiz do PDV ainda com template
  default do Next.js. Causa: não foi consertado durante sprints
  anteriores. Acessar `localhost:3000/` mostra "To get started,
  edit the page.tsx" em vez de redirecionar para login. Impacto
  baixo (navegação real começa em `/login`). Corrigir em sprint
  de polimento futuro com simples redirect para `/login`.

- **PDV — cor de texto em inputs quase invisível.** Inputs nas
  telas autenticadas do PDV têm contraste muito baixo entre
  texto digitado e fundo. Validação visual reportada pelo
  usuário. Corrigir em sprint de polimento visual futura.
```

---

## Antes de declarar concluído (OBRIGATÓRIO)

Execute na ordem e reporte o resultado de cada um:

1. `cd frontend && npm run build` — deve completar sem erros
2. `cd frontend && npm run dev` — deve subir sem erros no terminal
3. Abrir `http://localhost:3000/login` no browser e confirmar que
   a página renderiza sem `Expected '>'` ou outros erros de parse
   no console

**Se qualquer um falhar, não declare concluído.** Reporte o erro,
investigue, corrija, repita.

---

## Princípio (lembrete)

Você listou "Validação Pendente" corretamente — isso foi bom
progresso em relação a sprints anteriores. Mas declarar "Fase 1
completa" sem rodar `npm run build` localmente é versão mais
sutil do mesmo padrão: presumir que código funciona porque "parece
correto na leitura".

**Build local é mandatório** antes de declarar implementação
completa. Não é validação manual de UX (essa é do usuário) —
é validação de que o código sequer compila.

Adicione isso à sua rotina padrão: implementou → `build` → reportou.

---

## Relatório esperado

Ao terminar:

- Confirmação de rename `useModules.ts` → `.tsx` + lista de imports atualizados
- Confirmação da reversão da mensagem "use_admin_panel"
- `PROJECT_STATE.md` com as duas dívidas registradas
- Saída de `npm run build` (se sucesso, declarar; se falhou, mostrar erro)
- Saída de `npm run dev` (primeiras linhas após startup)
- Próximos passos: usuário re-executa os 8 cenários da validação

Sem simular validação. Sem listar "Resultados Observados". Apenas
reporta build OK e devolve a bola para o usuário validar.
