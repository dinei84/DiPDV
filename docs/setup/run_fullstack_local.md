# Relatorio de Execucao Local (Backend + Frontend)

Este guia mostra um passo a passo direto para rodar o DiPDV localmente no Windows (PowerShell), com backend Spring Boot e frontend Next.js.

## 1) Pre-requisitos

- Java 21 (`java -version`)
- Docker Desktop (`docker --version`)
- Node.js 20+ (`node -v`)
- npm 10+ (`npm -v`)

## 2) Subir infraestrutura (PostgreSQL + pgAdmin)

Na raiz do projeto (`DiPDV/`), execute:

```powershell
docker compose up -d
docker ps
```

Validacao esperada:
- container `dipdv-postgres` em execucao
- container `dipdv-pgadmin` em execucao

Portas usadas:
- PostgreSQL: `localhost:5433` (host) -> `5432` (container)
- pgAdmin: `http://localhost:5050`

Credenciais do banco (docker-compose):
- database: `dipdv_dev`
- user: `dipdv_app`
- password: `dipdv_local_2025`

## 3) Rodar o backend (Spring Boot)

Entre na pasta do backend:

```powershell
cd backend
```

Defina as variaveis de ambiente na sessao atual do PowerShell:

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5433/dipdv_dev"
$env:SPRING_DATASOURCE_USERNAME="dipdv_app"
$env:SPRING_DATASOURCE_PASSWORD="dipdv_local_2025"
$env:JWT_SECRET="dipdv_dev_secret_minimo_32_caracteres_123"
```

Suba a API:

```powershell
.\mvnw.cmd spring-boot:run
```

Validacoes do backend:
- Health check: `http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

Observacao importante:
- O `JWT_SECRET` precisa ter no minimo 32 caracteres, senao o backend nao inicia.

## 4) Rodar o frontend (Next.js)

Abra outro terminal e entre na pasta:

```powershell
cd frontend
```

Instale dependencias:

```powershell
npm install
```

Opcional (recomendado): fixar a URL da API no frontend:

```powershell
$env:NEXT_PUBLIC_API_URL="http://localhost:8080"
```

Suba o frontend:

```powershell
npm run dev
```

Validacao do frontend:
- Aplicacao web: `http://localhost:3000`

## 5) Checklist rapido de verificacao

Se tudo estiver correto:
- `docker ps` mostra postgres e pgadmin ativos
- backend responde `{"status":"UP"}` no actuator
- swagger abre sem erro
- frontend abre em `localhost:3000`
- tela de login/autenticacao consegue chamar a API sem erro de conexao

## 6) Erros comuns e como corrigir

### Erro de conexao com banco no backend

Causa mais comum: URL/porta errada.

Conferir:
- se o Docker esta ativo
- se o banco esta em `5433` no host
- se variaveis `SPRING_DATASOURCE_*` foram definidas no mesmo terminal do backend

### Erro de CORS ou API no frontend

Conferir:
- backend realmente ativo em `8080`
- `NEXT_PUBLIC_API_URL` apontando para `http://localhost:8080` (se estiver setada)

### Erro de JWT_SECRET no startup

Conferir:
- valor com 32+ caracteres
- variavel definida antes de rodar `.\mvnw.cmd spring-boot:run`

## 7) Como parar tudo

- Backend/frontend: `Ctrl + C` nos terminais
- Containers:

```powershell
docker compose stop
```

Para remover containers (mantendo volume):

```powershell
docker compose down
```

Para remover containers e dados:

```powershell
docker compose down -v
```
