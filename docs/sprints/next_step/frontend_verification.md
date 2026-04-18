# Verificação e Diagnóstico do Frontend do DiPDV

Este relatório descreve as causas dos erros encontrados no terminal durante a execução do frontend e elenca os passos necessários para que o sistema se comporte adequadamente como um PDV real.

---

## 1. Análise dos Erros Encontrados

### Erro 1: Hydration Mismatch (`A tree hydrated but some attributes of the server rendered HTML didn't match...`)

**O que significa:** O React gerou o HTML no servidor de uma forma, mas quando carregou no navegador, o HTML estava diferente. 

**A Causa Exata:** Se você olhar de perto o log: `<body className="..." - cz-shortcut-listen="true">`, a presença do atributo `cz-shortcut-listen` indica que uma **extensão do seu navegador** (muito provavelmente o ColorZilla ou alguma de captura de tela/atalhos) inseriu código na tag `<body>` antes mesmo de o React iniciar.

**Como resolver:**
- **No código:** Adicione `suppressHydrationWarning` na tag `<body>` dentro do arquivo `src/app/layout.tsx`. Alterando de `<body className="...">` para `<body className="..." suppressHydrationWarning>`.
- Isso instruirá o Next.js a ignorar diferenças causadas por extensões na tag do corpo (body).

### Erro 2: `Failed to load resource: the server responded with a status of 401 ()` e `Token ausente ou inválido`

**O que significa:** A requisição para a API de relatórios exigia autenticação, mas a página não a forneceu (ou forneceu um token vazio/inválido).

**A Causa:** O usuário acessou a tela `/reports` diretamente sem ter feito login com sucesso. O sistema tentou chamar `apiFetch` (em `api.ts`), que buscou o `dipdv_token` no `localStorage`, mas ele estava vazio, fazendo com que a API retornasse `HTTP Status 401 Unauthorized`. Como não há um tratamento de erros global que avise o usuário ou o redirecione para o login, a página lançou uma exceção (`Uncaught (in promise)`).

---

## 2. O que está faltando para o Frontend funcionar como um PDV? (Próximos Passos)

Para que essa aplicação pareça e aja como um software maduro e evite as falhas descritas acima, as seguintes implementações são necessárias nas próximas sprints:

### 2.1. Proteção de Rotas (Route Guards ou Middleware)
Atualmente as pessoas conseguem acessar páginas reservadas (como `/reports` ou `/dashboard`) mesmo sem estarem autenticadas.
- **Implementação:** Precisamos implementar um "Auth Guard" (Client-side) ou um `middleware.ts` no Next.js. Se o usuário tentar acessar qualquer rota que não seja o `/login` e não possuir um token válido, ele deve ser levado compulsoriamente para a tela de Login.
- **Revisão da Persistência de Sessão:** Hoje o token fica no `localStorage`. Para que o Next.js lide melhor com roteamento em SSR, o ideal é salvar esse Token também em **Cookies**.

### 2.2. Interceptor de HTTP e Tratamento Global de Erros (Axios ou fetch Wrapper)
A aplicação atual trava com `Uncaught (in promise)` e deixa o usuário "no escuro" ao receber um `401 Unauthorized`.
- **Implementação:** Construir uma validação no `apiFetch`. Se a resposta for 401, o sistema deve automaticamente limpar a sessão (`localStorage.removeItem('dipdv_token')`), exibir uma notificação visual usando Toast ("Sua sessão expirou") e redirecionar a aplicação para o fluxo de `/login`.

### 2.3. Resolução da Rota Principal (Root Page `page.tsx`)
Se você acessar `localhost:3000`, verá a tela padrão de propaganda do Next.js. Isso acontece porque o arquivo de boilerplate não foi removido.
- **Implementação:** Excluir ou redirecionar o boilerplate de `src/app/page.tsx` e fazer o roteamento principal direcionar ou para o Dashboard verdadeiro `(pdv)/page.tsx` ou para o `/login` (se não estiver autenticado).

### 2.4 Tratamento Corrigido de Inicialização Temporal (SSR x Client)
No arquivo de Relatórios, existe a linha: `const today = new Date().toISOString().split('T')[0];`. Essa construção roda no Servidor e novamente no Cliente.
- **Implementação:** Para ser seguro e evitar erros de "Hydration" em horários da virada de dia, essas inicializações devem idealmente começar com string vazia ou serem populadas via `useEffect` após o componente ser montado.

---

### Conclusão e Instrução Imediata
Para testar **agora mesmo** e ver o front funcionar, você deve primeiro **fazer Login** pela rota `http://localhost:3000/login` utilizando um tenant e conta válidos. O token gerado será armazenado no `localStorage`, e a partir disso você poderá ir até `/reports` que a tabela e os botões funcionarão corretamente sem devolver Erro 401.
