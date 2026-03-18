# DiPDV

> Sistema de Ponto de Venda (PDV) para lanchonetes — SaaS multi-tenant, mobile-first.

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green)](https://spring.io/projects/spring-boot)
[![Next.js](https://img.shields.io/badge/Next.js-14-black)](https://nextjs.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue)](https://www.postgresql.org/)
[![Railway](https://img.shields.io/badge/Deploy-Railway-purple)](https://railway.app/)

---

## O que é o DiPDV

O DiPDV é um sistema de PDV moderno voltado para lanchonetes de pequeno e médio porte. Permite gerenciar vendas, caixa, estoque e relatórios via browser ou dispositivo móvel (PWA), sem necessidade de instalação.

O produto opera como **SaaS multi-tenant**: múltiplos estabelecimentos usam a mesma plataforma com isolamento total de dados via Row Level Security (RLS) no PostgreSQL.

---

## Módulos do MVP

| Módulo | Status |
|---|---|
| PDV / Vendas | 🔲 Em desenvolvimento |
| Pagamentos / Caixa | 🔲 Em desenvolvimento |
| Produtos / Cardápio | 🔲 Em desenvolvimento |
| Estoque Básico | 🔲 Em desenvolvimento |
| Relatórios | 🔲 Em desenvolvimento |
| Autenticação / Multi-tenancy | 🔲 Em desenvolvimento |
| Auditoria | 🔲 Em desenvolvimento |

---

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Java 21 + Spring Boot 3 |
| Frontend | Next.js 14 + Tailwind CSS (PWA) |
| Banco de Dados | PostgreSQL 16 |
| Migrations | Flyway |
| Autenticação | JWT + Refresh Token |
| Hospedagem | Railway |
| CI/CD | GitHub Actions |
| Gestão Ágil | GitHub Projects (Scrum) |

---

## Estrutura do Monorepo

```
dipdv/
├── backend/                        # Spring Boot — API REST
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/dipdv/
│   │   │   │   ├── modules/        # Módulos de negócio
│   │   │   │   │   ├── auth/
│   │   │   │   │   ├── catalog/
│   │   │   │   │   ├── order/
│   │   │   │   │   ├── payment/
│   │   │   │   │   ├── cashregister/
│   │   │   │   │   ├── inventory/
│   │   │   │   │   └── report/
│   │   │   │   ├── shared/         # Infra compartilhada
│   │   │   │   │   ├── audit/
│   │   │   │   │   ├── security/
│   │   │   │   │   └── tenant/
│   │   │   │   └── DiPdvApplication.java
│   │   │   └── resources/
│   │   │       ├── db/migration/   # Flyway migrations
│   │   │       │   ├── V1__initial_schema.sql
│   │   │       │   ├── V2__rls_policies.sql
│   │   │       │   └── V3__indexes.sql
│   │   │       ├── application.yml
│   │   │       ├── application-dev.yml
│   │   │       └── application-prod.yml
│   │   └── test/
│   └── pom.xml
├── frontend/                       # Next.js 14 — PWA mobile-first
│   ├── src/
│   ├── public/
│   └── package.json
├── docs/                           # Documentação do projeto
│   ├── README.md                   ← este arquivo
│   ├── ARCHITECTURE.md
│   ├── DATABASE.md
│   ├── CONTRIBUTING.md
│   └── SETUP.md
├── .github/
│   ├── workflows/
│   │   ├── ci-backend.yml
│   │   └── ci-frontend.yml
│   └── PULL_REQUEST_TEMPLATE.md
└── .gitignore
```

---

## Como rodar localmente

### Pré-requisitos

- Java 21+
- Maven 3.9+
- Node.js 20+
- Docker (para o PostgreSQL local)
- Git

### Backend

```bash
# 1. Subir o PostgreSQL via Docker
docker run --name dipdv-postgres \
  -e POSTGRES_DB=dipdv \
  -e POSTGRES_USER=dipdv_app \
  -e POSTGRES_PASSWORD=dipdv_local \
  -p 5432:5432 -d postgres:16

# 2. Configurar variáveis de ambiente (copiar o exemplo)
cp backend/src/main/resources/application-dev.yml.example \
   backend/src/main/resources/application-dev.yml

# 3. Rodar a aplicação (Flyway executa as migrations automaticamente)
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

A API estará disponível em `http://localhost:8080`.
Swagger UI em `http://localhost:8080/swagger-ui.html`.

### Frontend

```bash
cd frontend
npm install
npm run dev
```

O frontend estará disponível em `http://localhost:3000`.

---

## Documentação

| Documento | Conteúdo |
|---|---|
| [ARCHITECTURE.md](./ARCHITECTURE.md) | Decisões de arquitetura, padrões e módulos |
| [DATABASE.md](./DATABASE.md) | Modelagem do banco, ENUMs, decisões de design |
| [CONTRIBUTING.md](./CONTRIBUTING.md) | Git Flow, padrão de commits, processo de PR |
| [SETUP.md](./SETUP.md) | Guia completo de configuração do ambiente |

---

## Equipe

| Papel | Responsável |
|---|---|
| Desenvolvedor Principal | Dev |
| Tech Lead / Arquitetura | Claude (Anthropic) |
| Pair Programming | Antigravity |

---

## Licença

Projeto acadêmico e de desenvolvimento interno. Todos os direitos reservados.
