# Prompt — Antigravity: Validação Final Sprint 0 + Correção de Branch

---

## Contexto

O módulo de autenticação foi implementado e os 5 bugs foram corrigidos com sucesso.
Antes de declarar o Sprint 0 oficialmente fechado, há **dois problemas pendentes**
que precisam ser resolvidos nesta ordem:

1. Corrigir o fluxo de branches (commit foi para `main` direto — violação do Git Flow)
2. Validar os testes do endpoint que ficaram desmarcados no checklist

---

## Tarefa 1 — Corrigir o fluxo de branches

### Problema
O commit do Sprint 0 foi feito diretamente em `main`, pulando o `develop`.
Pelo CONTRIBUTING.md do projeto, `main` só recebe código via Pull Request
revisado após Sprint Review — nunca commit direto.

### Correção

```bash
# 1. Garantir que está atualizado
git fetch origin

# 2. Criar branch de correção a partir do estado atual de main
git checkout main
git checkout -b fix/sprint0-branch-flow

# 3. Verificar o histórico para confirmar o que foi commitado direto em main
git log --oneline -5

# 4. Garantir que develop tem o mesmo conteúdo de main
git checkout develop
git merge main --no-ff -m "chore: sincronizar develop com sprint 0 completo"

# 5. Push do develop atualizado
git push origin develop

# 6. Abrir Pull Request no GitHub: develop → main
#    Título: "Sprint 0 — Autenticação JWT e scaffold completo"
#    Descrição: colar o conteúdo do SPRINT0_CONCLUSAO.md
```

### Estado esperado após correção

```
main     ← recebe apenas via PR (branch protegida)
  │
develop  ← contém todo o Sprint 0 completo
  │
feature/* ← branches futuras do Sprint 1 partem daqui
```

> ⚠️ A partir de agora: todo trabalho novo começa em `feature/` a partir de
> `develop`. Nunca commitar direto em `main` ou `develop`.

---

## Tarefa 2 — Alinhar credenciais do banco

O `application-dev.yml` foi alterado para credenciais locais (`meu_banco` /
`admin` / `admin123`), diferentes do Docker Compose do projeto (`dipdv_dev` /
`dipdv_app` / `dipdv_local_2025`).

Duas opções — escolher uma:

**Opção A (recomendada):** Usar o Docker Compose do projeto

```bash
# Na raiz do repositório
docker compose up -d

# Ajustar application-dev.yml para bater com o compose
```

```yaml
# application-dev.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/dipdv_dev
    username: dipdv_app
    password: dipdv_local_2025
```

**Opção B:** Manter banco local, mas documentar no SETUP.md

Se preferir manter o banco local instalado na máquina, atualizar o
`docs/SETUP.md` seção "5. Banco de dados local" com as credenciais reais
usadas — para que qualquer dev que clonar o projeto saiba o que configurar.

---

## Tarefa 3 — Validar os testes pendentes do endpoint

Com a aplicação rodando (`.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev`),
executar os três testes e confirmar os resultados:

### Teste 1 — Login com sucesso (deve retornar 200)

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "00000000-0000-0000-0000-000000000001",
    "email": "admin@dipdv.dev",
    "password": "dipdv@2025"
  }' | jq .
```

**Resposta esperada:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 28800,
  "userId": "...",
  "tenantId": "00000000-0000-0000-0000-000000000001",
  "name": "Admin Dev",
  "role": "ADMIN"
}
```

Copiar o valor do campo `token` — será usado no Teste 4.

---

### Teste 2 — Senha incorreta (deve retornar 401)

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "00000000-0000-0000-0000-000000000001",
    "email": "admin@dipdv.dev",
    "password": "senha-errada"
  }' | jq .
```

**Resposta esperada:**
```json
{
  "status": 401,
  "error": "UNAUTHORIZED",
  "message": "Email ou senha inválidos",
  "timestamp": "..."
}
```

> Confirmar que a mensagem é **genérica** — não revela se o email existe ou não.

---

### Teste 3 — Payload inválido (deve retornar 400 com campos)

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": ""}' | jq .
```

**Resposta esperada:**
```json
{
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Campos inválidos na requisição",
  "timestamp": "...",
  "fields": [
    { "field": "tenantId", "message": "tenantId é obrigatório" },
    { "field": "email", "message": "email é obrigatório" },
    { "field": "password", "message": "senha é obrigatória" }
  ]
}
```

---

### Teste 4 — JWT válido acessa rota protegida (deve retornar 200)

Usar o token copiado no Teste 1:

```bash
curl -s http://localhost:8080/actuator/health \
  -H "Authorization: Bearer SEU_TOKEN_AQUI" | jq .
```

**Resposta esperada:**
```json
{ "status": "UP" }
```

> Este teste confirma que o `JwtAuthFilter` está funcionando — token válido
> passa pela cadeia de filtros sem erro.

---

### Teste 5 — Swagger UI

Abrir no browser: `http://localhost:8080/swagger-ui.html`

Confirmar:
- Endpoint `POST /api/v1/auth/login` está listado
- É possível executar o login diretamente pelo Swagger (botão "Try it out")
- Os três response codes (200, 400, 401) estão documentados

---

## Tarefa 4 — Commit de fechamento oficial

Após validar todos os testes:

```bash
# Criar feature branch para o fechamento
git checkout develop
git checkout -b feature/US06.2-validacao-sprint0

git add .
git commit -m "test(auth): validar endpoint de login e corrigir fluxo de branches

- Confirmados os 3 cenários do POST /api/v1/auth/login (200, 401, 400)
- Credenciais de banco alinhadas com docker-compose.yml
- Branch flow corrigido: develop sincronizado com main

Closes #XX (US06.2)"

git push origin feature/US06.2-validacao-sprint0
```

Abrir Pull Request: `feature/US06.2-validacao-sprint0` → `develop`

---

## Checklist final — Sprint 0

- [ ] `develop` sincronizado com o estado atual de `main`
- [ ] PR aberto de `develop` → `main` com título do Sprint 0
- [ ] Credenciais do `application-dev.yml` alinhadas com o time
- [ ] `POST /auth/login` credenciais corretas → 200 + JWT ✓
- [ ] `POST /auth/login` senha errada → 401 mensagem genérica ✓
- [ ] `POST /auth/login` payload vazio → 400 com lista de campos ✓
- [ ] JWT válido passa pelo `JwtAuthFilter` sem erro ✓
- [ ] Swagger exibe endpoint com 3 responses documentados ✓
- [ ] Commit de fechamento em `feature/` + PR para `develop` ✓

Ao reportar: colar o output de cada curl e screenshot do Swagger.

---

## O que NÃO fazer

- Não criar nenhum código novo neste prompt — apenas validar e corrigir fluxo
- Não mergear PR direto — aguardar Sprint Review com o tech lead
- Não alterar migrations já executadas
