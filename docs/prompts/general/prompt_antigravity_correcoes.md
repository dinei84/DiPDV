# Prompt — Antigravity: Correções + Docker Compose + Validação do Boot

---

## Contexto

O scaffold inicial foi executado com sucesso (`BUILD SUCCESS`). Este prompt cobre
três tarefas pendentes antes de prosseguirmos com o desenvolvimento:

1. Corrigir o posicionamento dos arquivos Java (estão na raiz, devem estar nos pacotes)
2. Criar o `docker-compose.yml` com os containers necessários
3. Executar o primeiro boot e validar as migrations

---

## Tarefa 1 — Mover arquivos Java para os pacotes corretos

Os 6 arquivos abaixo estão na **raiz do repositório** (`DiPDV/`).
Mova cada um para o caminho correto dentro do backend:

| Arquivo (origem — raiz) | Destino correto |
|---|---|
| `JwtService.java` | `backend/src/main/java/com/dipdv/shared/security/` |
| `JwtAuthFilter.java` | `backend/src/main/java/com/dipdv/shared/security/` |
| `DiPdvAuthDetails.java` | `backend/src/main/java/com/dipdv/shared/security/` |
| `SecurityConfig.java` | `backend/src/main/java/com/dipdv/shared/security/` |
| `TenantContext.java` | `backend/src/main/java/com/dipdv/shared/tenant/` |
| `TenantFilter.java` | `backend/src/main/java/com/dipdv/shared/tenant/` |

Após mover, apague os arquivos da raiz.

**Validar depois:**
```bash
cd backend
.\mvnw.cmd compile
```
Esperado: `BUILD SUCCESS` — sem erros de pacote ou import.

---

## Tarefa 2 — Criar o docker-compose.yml

Crie o arquivo `docker-compose.yml` na **raiz do repositório** (`DiPDV/`):

```yaml
version: '3.8'

services:

  postgres:
    image: postgres:16
    container_name: dipdv-postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: dipdv_dev
      POSTGRES_USER: dipdv_app
      POSTGRES_PASSWORD: dipdv_local_2025
    ports:
      - "5432:5432"
    volumes:
      - dipdv_postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U dipdv_app -d dipdv_dev"]
      interval: 10s
      timeout: 5s
      retries: 5

  pgadmin:
    image: dpage/pgadmin4:latest
    container_name: dipdv-pgadmin
    restart: unless-stopped
    environment:
      PGADMIN_DEFAULT_EMAIL: admin@dipdv.local
      PGADMIN_DEFAULT_PASSWORD: dipdv_admin
    ports:
      - "5050:80"
    depends_on:
      postgres:
        condition: service_healthy
    volumes:
      - dipdv_pgadmin_data:/var/lib/pgadmin

volumes:
  dipdv_postgres_data:
  dipdv_pgadmin_data:
```

**Serviços incluídos:**

| Serviço | Porta | Acesso |
|---|---|---|
| PostgreSQL 16 | 5432 | Driver JDBC / DBeaver / psql |
| pgAdmin 4 | 5050 | `http://localhost:5050` (interface web) |

**pgAdmin — conexão após subir:**
- Email: `admin@dipdv.local`
- Senha: `dipdv_admin`
- Para conectar ao banco: Add Server → Host: `postgres`, Port: `5432`, User: `dipdv_app`, Password: `dipdv_local_2025`
  > ⚠️ O host é `postgres` (nome do serviço no compose), não `localhost`.

---

## Tarefa 3 — Subir os containers

```bash
# Na raiz do repositório (DiPDV/)
docker compose up -d

# Verificar se os containers estão healthy
docker compose ps
```

Aguardar o status `healthy` no postgres antes de prosseguir.

```bash
# Confirmar conexão com o banco
docker exec -it dipdv-postgres psql -U dipdv_app -d dipdv_dev -c "\dt"
```
Esperado: `Did not find any relations.` (banco vazio — Flyway ainda não rodou).

---

## Tarefa 4 — Primeiro boot com Flyway

```bash
cd backend
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

**O que deve aparecer no console (em ordem):**
```
Flyway ... Migrating schema "public" to version 1 - initial schema
Flyway ... Migrating schema "public" to version 2 - rls policies
Flyway ... Migrating schema "public" to version 3 - indexes
Flyway ... Successfully applied 3 migrations
Started DiPdvApplication in X.XXX seconds
```

Se aparecer erro no Flyway, cole o log aqui antes de continuar.

---

## Tarefa 5 — Validar o boot

Com a aplicação rodando, verificar os três endpoints:

**1. Health check**
```bash
curl http://localhost:8080/actuator/health
```
Esperado:
```json
{"status":"UP"}
```

**2. Swagger UI**
Abrir no browser: `http://localhost:8080/swagger-ui.html`
Esperado: página do Swagger carregando (pode estar vazia de endpoints — é normal neste estágio).

**3. Tabelas criadas pelo Flyway**
```bash
docker exec -it dipdv-postgres psql -U dipdv_app -d dipdv_dev -c "\dt"
```
Esperado: listar todas as tabelas — `tenants`, `users`, `products`, `orders`, etc.

---

## Tarefa 6 — Commit inicial

```bash
# Na raiz do repositório
git add .
git commit -m "chore(infra): scaffold inicial do monorepo DiPDV

- Estrutura Spring Boot 3 + Next.js 14
- Migrations Flyway V1/V2/V3 executadas com sucesso
- Camada de segurança: JWT + RLS + TenantContext
- Docker Compose: PostgreSQL 16 + pgAdmin 4
- Documentação completa em /docs
- CI GitHub Actions configurado"

git push origin develop
```

---

## Checklist final

Antes de reportar conclusão, confirmar:

- [ ] 6 arquivos Java movidos para os pacotes corretos
- [ ] `.\mvnw.cmd compile` → `BUILD SUCCESS`
- [ ] `docker compose up -d` → ambos containers `running`
- [ ] Flyway executou V1, V2 e V3 sem erros
- [ ] `http://localhost:8080/actuator/health` → `{"status":"UP"}`
- [ ] `http://localhost:8080/swagger-ui.html` → Swagger carrega
- [ ] Tabelas visíveis no banco via `\dt`
- [ ] Commit inicial feito e push para `develop`

Reportar o resultado de cada item ao concluir.

---

## O que NÃO fazer

- Não criar nenhum endpoint ou classe Java além do que já existe
- Não modificar os arquivos de segurança já prontos
- Não alterar as migrations já executadas
- Não mergear para `main` — permanecer em `develop`
