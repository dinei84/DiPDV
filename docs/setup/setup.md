# SETUP — Configuração Completa do Backend DiPDV

> Guia passo a passo para o Antigravity (pair programming) configurar o ambiente backend do zero.
> Leia este documento inteiro antes de começar.

---

## Pré-requisitos

| Ferramenta | Versão mínima | Verificar |
|---|---|---|
| Java (JDK) | 21 | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Docker Desktop | Qualquer | `docker -v` |
| Git | 2.40+ | `git -version` |
| IDE | IntelliJ IDEA (recomendado) ou VS Code | — |

---

## 1. Clonar o repositório

```bash
git clone https://github.com/SEU_USUARIO/DiPDV.git
cd DiPDV

# Configurar branch padrão como develop
git checkout develop
```

---

## 2. Estrutura esperada do repositório

Antes de prosseguir, confirme que a estrutura do monorepo está assim:

```
DiPDV/
├── backend/
├── frontend/
├── docs/
│   ├── README.md
│   ├── ARCHITECTURE.md
│   ├── DATABASE.md
│   ├── CONTRIBUTING.md
│   └── SETUP.md          ← este arquivo
└── .github/
```

Se a pasta `backend/` ainda não existir, siga o passo 3 para criá-la via Spring Initializr.

---

## 3. Criar o projeto Spring Boot

### Opção A — Spring Initializr (recomendado)

Acesse [start.spring.io](https://start.spring.io) e configure:

| Campo | Valor |
|---|---|
| Project | Maven |
| Language | Java |
| Spring Boot | 3.3.x (última estável) |
| Group | `com.dipdv` |
| Artifact | `backend` |
| Name | `DiPDV Backend` |
| Package name | `com.dipdv` |
| Packaging | Jar |
| Java | 21 |

**Dependências a adicionar:**

| Dependência | Categoria | Motivo |
|---|---|---|
| Spring Web | Web | API REST |
| Spring Data JPA | SQL | ORM / Repositórios |
| PostgreSQL Driver | SQL | Driver JDBC |
| Flyway Migration | SQL | Migrations versionadas |
| Spring Security | Security | Autenticação e autorização |
| Validation | I/O | Bean Validation (@Valid) |
| Lombok | Developer Tools | Reduzir boilerplate |
| Spring Boot DevTools | Developer Tools | Hot reload no dev |
| Spring Boot Actuator | Ops | Health check /actuator/health |

Clique em **Generate**, extraia o ZIP e mova o conteúdo para a pasta `backend/` do repositório.

### Opção B — Maven via terminal

```bash
mvn archetype:generate \
  -DgroupId=com.dipdv \
  -DartifactId=backend \
  -DarchetypeArtifactId=maven-archetype-quickstart \
  -DinteractiveMode=false
```

---

## 4. Dependências adicionais no pom.xml

Adicione estas dependências que **não estão no Spring Initializr** mas são necessárias:

```xml
<!-- JWT — autenticação via token -->
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

<!-- SpringDoc OpenAPI — Swagger UI automático -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.6.0</version>
</dependency>

<!-- MapStruct — mapeamento Entity ↔ DTO sem boilerplate -->
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

**Plugin Maven para MapStruct + Lombok (obrigatório — sem isso os mappers não compilam):**

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

> ⚠️ **Atenção:** Lombok deve vir **antes** do MapStruct no `annotationProcessorPaths`. A ordem importa — o MapStruct processa os getters/setters gerados pelo Lombok.

---

## 5. Banco de dados local com Docker

```bash
# Subir o PostgreSQL (rodar na raiz do repositório)
docker run --name dipdv-postgres \
  -e POSTGRES_DB=dipdv_dev \
  -e POSTGRES_USER=dipdv_app \
  -e POSTGRES_PASSWORD=dipdv_local_2025 \
  -p 5432:5432 \
  --restart unless-stopped \
  -d postgres:16

# Confirmar que subiu
docker ps | grep dipdv-postgres

# Conectar ao banco para verificar (opcional)
docker exec -it dipdv-postgres psql -U dipdv_app -d dipdv_dev
```

> O Flyway vai criar todas as tabelas automaticamente no primeiro boot. Não execute os scripts SQL manualmente.

---

## 6. Configuração do application.yml

### `application.yml` (base — compartilhado entre ambientes)

```yaml
spring:
  application:
    name: dipdv-backend
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  jpa:
    hibernate:
      ddl-auto: validate        # Flyway gerencia o schema — nunca usar create/update
    show-sql: false             # Ativar apenas para debug pontual
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
        default_schema: public
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false  # Nunca true em produção

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
  endpoint:
    health:
      show-details: when-authorized

# Configurações customizadas do DiPDV
dipdv:
  jwt:
    secret: ${JWT_SECRET}             # obrigatório via variável de ambiente
    expiration-ms: 900000             # 15 minutos
    refresh-expiration-ms: 604800000  # 7 dias
  security:
    bcrypt-strength: 12
```

### `application-dev.yml` (ambiente local)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/dipdv_dev
    username: dipdv_app
    password: dipdv_local_2025
    hikari:
      maximum-pool-size: 5
      minimum-idle: 2
      connection-timeout: 20000
  jpa:
    show-sql: true              # Mostrar SQL no dev para aprendizado

logging:
  level:
    com.dipdv: DEBUG
    org.springframework.security: DEBUG
    org.flywaydb: INFO

dipdv:
  jwt:
    secret: dev-secret-key-minimo-256-bits-para-hmac-sha256-obrigatorio
```

### `application-prod.yml` (Railway — variáveis de ambiente)

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
      idle-timeout: 600000
      max-lifetime: 1800000

logging:
  level:
    com.dipdv: INFO
    org.springframework.security: WARN
```

---

## 7. Estrutura de pacotes a criar

Crie os pacotes vazios abaixo dentro de `src/main/java/com/dipdv/`:

```
com/dipdv/
├── DiPdvApplication.java               ← já existe (criado pelo Initializr)
│
├── modules/
│   ├── auth/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── entity/
│   │   └── dto/
│   ├── catalog/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── entity/
│   │   └── dto/
│   ├── order/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── entity/
│   │   └── dto/
│   ├── payment/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── entity/
│   │   └── dto/
│   ├── cashregister/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── entity/
│   │   └── dto/
│   ├── inventory/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── entity/
│   │   └── dto/
│   └── report/
│       ├── controller/
│       ├── service/
│       └── dto/
│
└── shared/
    ├── audit/
    ├── security/
    ├── tenant/
    └── exception/
```

---

## 8. Migrations Flyway

Copie os arquivos de migration para o caminho correto:

```bash
# Criar o diretório
mkdir -p backend/src/main/resources/db/migration

# Copiar as migrations (se estiverem na raiz do repositório)
cp V1__initial_schema.sql backend/src/main/resources/db/migration/
cp V2__rls_policies.sql   backend/src/main/resources/db/migration/
cp V3__indexes.sql        backend/src/main/resources/db/migration/
```

O Flyway vai executar esses arquivos automaticamente na ordem numérica no primeiro boot.

---

## 9. Primeiro boot e validação

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

**O que deve acontecer:**
1. Flyway executa V1, V2 e V3 (logs no console)
2. Spring Boot sobe na porta 8080
3. Swagger UI acessível em `http://localhost:8080/swagger-ui.html`
4. Health check em `http://localhost:8080/actuator/health` retorna `{"status":"UP"}`

**Se der erro no Flyway:**
```bash
# Verificar logs de migration
docker exec -it dipdv-postgres psql -U dipdv_app -d dipdv_dev \
  -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

---

## 10. Variáveis de ambiente (Railway — produção)

Configure no painel do Railway (Settings → Variables):

| Variável | Exemplo | Obrigatório |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://host:5432/dipdv` | ✅ |
| `DATABASE_USERNAME` | `dipdv_app` | ✅ |
| `DATABASE_PASSWORD` | `senha-segura` | ✅ |
| `JWT_SECRET` | string base64 ≥ 256 bits | ✅ |
| `SPRING_PROFILES_ACTIVE` | `prod` | ✅ |
| `PORT` | `8080` | Railway injeta automaticamente |

**Gerar um JWT_SECRET seguro:**
```bash
openssl rand -base64 64
```

---

## 11. GitHub Actions — CI

Crie o arquivo `.github/workflows/ci-backend.yml`:

```yaml
name: CI — Backend

on:
  push:
    branches: [ develop, main ]
    paths: [ 'backend/**' ]
  pull_request:
    branches: [ develop ]
    paths: [ 'backend/**' ]

jobs:
  build-and-test:
    runs-on: ubuntu-latest

    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_DB: dipdv_test
          POSTGRES_USER: dipdv_app
          POSTGRES_PASSWORD: dipdv_test
        ports: [ '5432:5432' ]
        options: --health-cmd pg_isready --health-interval 10s --health-timeout 5s --health-retries 5

    steps:
      - uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven

      - name: Build and test
        working-directory: backend
        run: mvn verify -Dspring.profiles.active=test
        env:
          SPRING_DATASOURCE_URL: jdbc:postgresql://localhost:5432/dipdv_test
          SPRING_DATASOURCE_USERNAME: dipdv_app
          SPRING_DATASOURCE_PASSWORD: dipdv_test
          JWT_SECRET: test-secret-key-minimo-256-bits-para-hmac-sha256
```

---

## 12. Próximos passos após o setup

Ordem recomendada de implementação (Sprint 0 → Sprint 1):

1. `shared/tenant/TenantContext.java` — ThreadLocal com o UUID do tenant
2. `shared/tenant/TenantFilter.java` — Servlet Filter que injeta `SET LOCAL app.current_tenant`
3. `shared/security/JwtService.java` — Geração e validação de JWT
4. `shared/security/JwtAuthFilter.java` — Filter que extrai e valida token
5. `shared/security/SecurityConfig.java` — Configuração do Spring Security
6. `modules/auth/` — Login e refresh token
7. `modules/catalog/` — CRUD de produtos e categorias
8. `modules/order/` — PDV: abrir pedido, adicionar itens, fechar
9. `shared/audit/` — @Aspect de auditoria

> Cada item acima corresponde a Tasks do Sprint 0 e Sprint 1 no GitHub Projects.

---

## Dúvidas e referências

- Documentação do projeto: `/docs/`
- Decisões arquiteturais: `ARCHITECTURE.md`
- Modelagem do banco: `DATABASE.md`
- Padrão de branches e commits: `CONTRIBUTING.md`
- Tech Lead: abrir issue no GitHub ou consultar Claude no projeto DiPDV
