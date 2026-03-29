 # Rodando e Testando o DiPDV Backend — Sprint 0

> **Stack:** Spring Boot 3.3.7 · Java 21 · PostgreSQL 17 (Docker) · JWT · Flyway
> **Porta:** `8080` (API REST) · `5432` (PostgreSQL) · `5050` (pgAdmin)

---

## Pré-requisitos

| Ferramenta | Versão mínima | Verificar |
|---|---|---|
| Java JDK | 21 | `java -version` |
| Docker Desktop | qualquer recente | `docker --version` |
| Maven Wrapper | incluso no projeto | `.\mvnw.cmd -version` |
| Postman ou Insomnia | qualquer | — |

---

## 1. Subindo o Banco de Dados (Docker)

Na raiz do projeto (onde está o `docker-compose.yml`), execute:

```bash
docker compose up -d
```

Verifique se os containers estão rodando:

```bash
docker ps
```

Você deve ver dois containers ativos:

| Container | Porta | Status |
|---|---|---|
| `meu-postgres` | `5432` | `Up` |
| `meu-pgadmin` | `5050` | `Up` |

---

## 2. Subindo o Backend

No diretório `backend/`, execute com o Maven Wrapper:

```bash
# Windows PowerShell (use .\mvnw.cmd)
.\mvnw.cmd spring-boot:run

# Linux / Mac (use ./mvnw)
./mvnw spring-boot:run
```

> **Perfil ativo:** `dev` (padrão configurado em `application.yml`)
> O Flyway executará as migrations automaticamente na primeira inicialização.

### Saída esperada no console

```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
...
:: Spring Boot ::                (v3.3.7)

INFO  --- Flyway: Successfully applied 3 migrations to schema "public"
INFO  --- Tomcat started on port 8080 (http) with context path '/'
INFO  --- Started DiPdvApplication in 8.X seconds

INFO  --- ╔══════════════════════════════════════════╗
INFO  --- ║         SEED DE DESENVOLVIMENTO          ║
INFO  --- ╠══════════════════════════════════════════╣
INFO  --- ║  tenantId : 00000000-0000-0000-0000-000000000001  ║
INFO  --- ║  email    : admin@dipdv.dev              ║
INFO  --- ║  senha    : dipdv@2025                   ║
INFO  --- ║  role     : ADMIN                        ║
INFO  --- ╚══════════════════════════════════════════╝
```

> Se o banco já foi inicializado antes, verá: `[DEV] Usuário de teste já existe — pulando seed`

---

## 3. Credenciais do Ambiente Dev

### Banco de Dados

| Campo | Valor |
|---|---|
| Host | `localhost:5432` |
| Database | `meu_banco` |
| Usuário | `admin` |
| Senha | `admin123` |

### pgAdmin (inspeção visual do banco)

Acesse `http://localhost:5050` com:
- **Email:** `admin@admin.com`
- **Senha:** `admin`

Para conectar ao servidor PostgreSQL dentro do pgAdmin:
- **Host:** `db-postgres` (nome do serviço Docker) ou `host.docker.internal`
- **Port:** `5432`
- **Usuário / Senha:** `admin` / `admin123`

### Usuário de Teste (seed automático)

| Campo | Valor |
|---|---|
| `tenantId` | `00000000-0000-0000-0000-000000000001` |
| `email` | `admin@dipdv.dev` |
| `senha` | `dipdv@2025` |
| `role` | `ADMIN` |

---

## 4. Testando via Swagger UI

Acesse no navegador:

```
http://localhost:8080/swagger-ui.html
```

O Swagger UI lista todos os endpoints disponíveis com formulários interativos.

### Endpoint disponível na Sprint 0

| Método | Rota | Descrição | Auth |
|---|---|---|---|
| `POST` | `/api/v1/auth/login` | Autenticação JWT | Pública |
| `GET` | `/actuator/health` | Health check | Pública |

### Fazendo login pelo Swagger

1. Expanda o grupo **"Autenticação"**
2. Clique em `POST /api/v1/auth/login`
3. Clique em **"Try it out"**
4. Preencha o body com:
```json
{
  "tenantId": "00000000-0000-0000-0000-000000000001",
  "email": "admin@dipdv.dev",
  "password": "dipdv@2025"
}
```
5. Clique em **Execute**

**Resposta esperada (HTTP 200):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 28800,
  "userId": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "tenantId": "00000000-0000-0000-0000-000000000001",
  "name": "Admin Dev",
  "role": "ADMIN"
}
```

---

## 5. Testando via Postman

### Collection: autenticação e rota protegida

#### Request 1 — Login

| Campo | Valor |
|---|---|
| Método | `POST` |
| URL | `http://localhost:8080/api/v1/auth/login` |
| Content-Type | `application/json` |

**Body (raw JSON):**
```json
{
  "tenantId": "00000000-0000-0000-0000-000000000001",
  "email": "admin@dipdv.dev",
  "password": "dipdv@2025"
}
```

**Script de teste (aba "Tests" do Postman)** — salva o token automaticamente:
```javascript
const json = pm.response.json();
pm.collectionVariables.set("jwt_token", json.token);
pm.collectionVariables.set("tenant_id", json.tenantId);
pm.test("Login bem-sucedido", () => pm.response.to.have.status(200));
pm.test("Token presente", () => pm.expect(json.token).to.be.a("string"));
```

#### Request 2 — Rota Protegida (Health com auth header)

| Campo | Valor |
|---|---|
| Método | `GET` |
| URL | `http://localhost:8080/actuator/health` |
| Authorization | `Bearer {{jwt_token}}` |

#### Request 3 — Tentativa sem token (deve retornar 401)

| Campo | Valor |
|---|---|
| Método | `GET` |
| URL | `http://localhost:8080/api/v1/categories` |
| Authorization | nenhuma |

**Resposta esperada (HTTP 401):**
```json
{
  "error": "UNAUTHORIZED",
  "message": "Token ausente ou inválido",
  "status": 401
}
```

---

## 6. Testando via cURL

### Login
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "00000000-0000-0000-0000-000000000001",
    "email": "admin@dipdv.dev",
    "password": "dipdv@2025"
  }'
```

### Salvar token e usar em request autenticado
```bash
# Salvar token
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"tenantId":"00000000-0000-0000-0000-000000000001","email":"admin@dipdv.dev","password":"dipdv@2025"}' \
  | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

# Usar token em rota protegida
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/actuator/health
```

### Health check (sem auth)
```bash
curl http://localhost:8080/actuator/health
```

**Resposta esperada:**
```json
{"status":"UP"}
```

---

## 7. Casos de Erro para Testar

### Credenciais inválidas (HTTP 401)
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"tenantId":"00000000-0000-0000-0000-000000000001","email":"admin@dipdv.dev","password":"senhaerrada"}'
```

### Payload incompleto — email faltando (HTTP 400)
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"tenantId":"00000000-0000-0000-0000-000000000001","password":"dipdv@2025"}'
```

### Rota protegida sem token (HTTP 401)
```bash
curl http://localhost:8080/api/v1/categories
```

### Token malformado (HTTP 401)
```bash
curl -H "Authorization: Bearer tokeninvalido" http://localhost:8080/api/v1/categories
```

---

## 8. Encerrando o Ambiente

### Parar o backend
Pressione `Ctrl + C` no terminal onde o Spring Boot está rodando.

### Parar os containers Docker (mantém dados)
```bash
docker compose stop
```

### Parar e remover containers (mantém volume de dados)
```bash
docker compose down
```

### Remover tudo incluindo os dados do banco
```bash
docker compose down -v
```

> **Atenção:** `down -v` apaga o volume `pg_data` e todos os dados. Na próxima inicialização, o Flyway rodará as migrations do zero e o seed recriará o usuário de teste.
