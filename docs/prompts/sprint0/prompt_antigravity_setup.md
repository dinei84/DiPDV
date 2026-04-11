# Prompt — Antigravity: Organização Inicial do Projeto DiPDV

---

## Contexto

Você é o pair programmer do projeto **DiPDV** — um SaaS de PDV para lanchonetes, desenvolvido em monorepo com Spring Boot 3 (backend) e Next.js 14 (frontend).

Todo o planejamento, arquitetura, modelagem de banco e camada de segurança já foram definidos e documentados. Sua tarefa agora é **organizar o repositório existente** com tudo que foi produzido, na estrutura correta, sem inventar nada além do que está descrito aqui.

---

## Estrutura esperada do monorepo

```
DiPDV/
├── backend/                         ← projeto Spring Boot (criar via Spring Initializr)
│   └── src/
│       ├── main/
│       │   ├── java/com/dipdv/
│       │   │   ├── DiPdvApplication.java
│       │   │   ├── modules/
│       │   │   │   ├── auth/
│       │   │   │   │   ├── controller/
│       │   │   │   │   ├── service/
│       │   │   │   │   ├── repository/
│       │   │   │   │   ├── entity/
│       │   │   │   │   └── dto/
│       │   │   │   ├── catalog/
│       │   │   │   │   ├── controller/
│       │   │   │   │   ├── service/
│       │   │   │   │   ├── repository/
│       │   │   │   │   ├── entity/
│       │   │   │   │   └── dto/
│       │   │   │   ├── order/
│       │   │   │   │   ├── controller/
│       │   │   │   │   ├── service/
│       │   │   │   │   ├── repository/
│       │   │   │   │   ├── entity/
│       │   │   │   │   └── dto/
│       │   │   │   ├── payment/
│       │   │   │   │   ├── controller/
│       │   │   │   │   ├── service/
│       │   │   │   │   ├── repository/
│       │   │   │   │   ├── entity/
│       │   │   │   │   └── dto/
│       │   │   │   ├── cashregister/
│       │   │   │   │   ├── controller/
│       │   │   │   │   ├── service/
│       │   │   │   │   ├── repository/
│       │   │   │   │   ├── entity/
│       │   │   │   │   └── dto/
│       │   │   │   ├── inventory/
│       │   │   │   │   ├── controller/
│       │   │   │   │   ├── service/
│       │   │   │   │   ├── repository/
│       │   │   │   │   ├── entity/
│       │   │   │   │   └── dto/
│       │   │   │   └── report/
│       │   │   │       ├── controller/
│       │   │   │       ├── service/
│       │   │   │       └── dto/
│       │   │   └── shared/
│       │   │       ├── audit/
│       │   │       ├── exception/
│       │   │       ├── security/    ← arquivos já prontos (ver abaixo)
│       │   │       └── tenant/      ← arquivos já prontos (ver abaixo)
│       │   └── resources/
│       │       ├── db/
│       │       │   └── migration/   ← migrations já prontas (ver abaixo)
│       │       ├── application.yml
│       │       ├── application-dev.yml
│       │       └── application-prod.yml
│       └── test/
│           └── java/com/dipdv/
├── frontend/                        ← projeto Next.js (criar via create-next-app)
│   ├── src/
│   │   ├── app/
│   │   ├── components/
│   │   ├── lib/
│   │   └── styles/
│   └── package.json
├── docs/                            ← documentação já pronta (ver abaixo)
│   ├── README.md
│   ├── ARCHITECTURE.md
│   ├── DATABASE.md
│   ├── CONTRIBUTING.md
│   └── SETUP.md
├── .github/
│   └── workflows/
│       └── ci-backend.yml           ← CI já pronto (ver SETUP.md)
└── .gitignore
```

---

## Tarefas em ordem

### 1. Criar o projeto Spring Boot

Acesse [start.spring.io](https://start.spring.io) e gere com:

- **Project:** Maven
- **Language:** Java
- **Spring Boot:** 3.3.x
- **Group:** `com.dipdv`
- **Artifact:** `backend`
- **Packaging:** Jar
- **Java:** 21

**Dependências via Initializr:**
Spring Web, Spring Data JPA, PostgreSQL Driver, Flyway Migration, Spring Security, Validation, Lombok, Spring Boot DevTools, Spring Boot Actuator

Mova o conteúdo gerado para a pasta `backend/` do repositório.

---

### 2. Adicionar dependências extras no pom.xml

Adicione ao `pom.xml` gerado:

```xml
<!-- JWT -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>

<!-- SpringDoc OpenAPI (Swagger UI) -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.6.0</version>
</dependency>

<!-- MapStruct -->
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
    <version>1.6.3</version>
</dependency>
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct-processor</artifactId>
    <version>1.6.3</version>
    <scope>provided</scope>
</dependency>
```

E configure o plugin do compilador com Lombok + MapStruct na ordem correta (Lombok antes do MapStruct):

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.13.0</version>
            <configuration>
                <source>21</source>
                <target>21</target>
                <annotationProcessorPaths>
                    <path>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                        <version>${lombok.version}</version>
                    </path>
                    <path>
                        <groupId>org.mapstruct</groupId>
                        <artifactId>mapstruct-processor</artifactId>
                        <version>1.6.3</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

---

### 3. Criar os pacotes vazios

Dentro de `src/main/java/com/dipdv/`, crie todos os pacotes listados na estrutura acima. Pode ser com um arquivo `.gitkeep` em cada pasta vazia para o Git rastreá-las.

---

### 4. Posicionar os arquivos já prontos

#### 4a. Camada de segurança e tenant

Coloque esses arquivos nos pacotes corretos (o código-fonte completo está nos arquivos entregues):

| Arquivo | Pacote |
|---|---|
| `TenantContext.java` | `com.dipdv.shared.tenant` |
| `TenantFilter.java` | `com.dipdv.shared.tenant` |
| `JwtService.java` | `com.dipdv.shared.security` |
| `JwtAuthFilter.java` | `com.dipdv.shared.security` |
| `DiPdvAuthDetails.java` | `com.dipdv.shared.security` |
| `SecurityConfig.java` | `com.dipdv.shared.security` |

#### 4b. Migrations Flyway

Mova os três arquivos SQL para `backend/src/main/resources/db/migration/`:

```
V1__initial_schema.sql
V2__rls_policies.sql
V3__indexes.sql
```

#### 4c. Documentação

Mova os cinco arquivos `.md` para a pasta `docs/` na raiz do repositório:

```
README.md
ARCHITECTURE.md
DATABASE.md
CONTRIBUTING.md
SETUP.md
```

---

### 5. Criar os arquivos de configuração

#### `application.yml` (base)

```yaml
spring:
  application:
    name: dipdv-backend
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
        default_schema: public
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false

springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
    operationsSorter: method

management:
  endpoints:
    web:
      exposure:
        include: health, info

dipdv:
  jwt:
    secret: ${JWT_SECRET}
    expiration-ms: 28800000
```

#### `application-dev.yml`

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/dipdv_dev
    username: dipdv_app
    password: dipdv_local_2025
    hikari:
      maximum-pool-size: 5
      minimum-idle: 2
  jpa:
    show-sql: true

logging:
  level:
    com.dipdv: DEBUG
    org.springframework.security: DEBUG
    org.flywaydb: INFO

dipdv:
  jwt:
    secret: dev-secret-key-minimo-256-bits-para-hmac-sha256-aqui
```

#### `application-prod.yml`

```yaml
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
    hikari:
      maximum-pool-size: 10
      minimum-idle: 3
      connection-timeout: 30000

logging:
  level:
    com.dipdv: INFO
    org.springframework.security: WARN
```

---

### 6. Criar o projeto Next.js

```bash
cd DiPDV
npx create-next-app@latest frontend \
  --typescript \
  --tailwind \
  --eslint \
  --app \
  --src-dir \
  --import-alias "@/*"
```

Estrutura esperada após criação:

```
frontend/
├── src/
│   ├── app/
│   ├── components/
│   ├── lib/
│   └── styles/
└── package.json
```

---

### 7. Criar o .gitignore na raiz

```gitignore
# Java / Maven
backend/target/
backend/*.class
backend/.mvn/

# Spring Boot
backend/src/main/resources/application-dev.yml

# Node / Next.js
frontend/node_modules/
frontend/.next/
frontend/out/
frontend/.env.local

# IDEs
.idea/
*.iml
.vscode/
*.swp

# OS
.DS_Store
Thumbs.db

# Variáveis de ambiente — nunca commitar
.env
.env.*
!.env.example
```

---

### 8. Criar o CI no GitHub Actions

Crie `.github/workflows/ci-backend.yml` com o conteúdo completo que está no arquivo `SETUP.md` (seção "11. GitHub Actions — CI").

---

### 9. Subir o PostgreSQL local e validar o boot

```bash
docker run --name dipdv-postgres \
  -e POSTGRES_DB=dipdv_dev \
  -e POSTGRES_USER=dipdv_app \
  -e POSTGRES_PASSWORD=dipdv_local_2025 \
  -p 5432:5432 \
  --restart unless-stopped \
  -d postgres:16

cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

**Validação — o boot deve:**
- Executar V1, V2 e V3 via Flyway sem erros
- Responder `{"status":"UP"}` em `http://localhost:8080/actuator/health`
- Exibir Swagger UI em `http://localhost:8080/swagger-ui.html`

---

### 10. Commit inicial

```bash
git add .
git commit -m "chore(infra): scaffold inicial do monorepo DiPDV

- Estrutura Spring Boot 3 + Next.js 14
- Migrations Flyway V1/V2/V3
- Camada de segurança: JWT + RLS + TenantContext
- Documentação completa em /docs
- CI GitHub Actions configurado"

git push origin develop
```

---

## O que NÃO fazer

- Não criar entidades JPA ainda — isso será feito na próxima sessão
- Não modificar as migrations já entregues
- Não alterar os arquivos Java de segurança já prontos
- Não instalar dependências além das listadas aqui
- Não criar endpoints além do que já existe — o próximo passo é o AuthController

---

## Referências

Toda a documentação de contexto está em `docs/`:
- Decisões de arquitetura → `ARCHITECTURE.md`
- Modelagem do banco → `DATABASE.md`
- Padrão de commits e branches → `CONTRIBUTING.md`
- Guia detalhado de setup → `SETUP.md`
