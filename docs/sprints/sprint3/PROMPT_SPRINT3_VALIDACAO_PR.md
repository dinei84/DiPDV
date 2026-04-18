# Prompt — Antigravity: Validação Final Sprint 3 + PR

---

## Contexto

Sprint 3 implementado com 58 testes unitários passando e frontend buildando.
Docker já está rodando. Este prompt executa os smoke tests, valida o
frontend em runtime e fecha o PR conforme a regra do projeto.

---

## Tarefa 1 — Rodar suite completa com banco disponível

```bash
cd backend
.\mvnw.cmd test
```

Esperado: **todos os testes passando**, incluindo `DiPdvApplicationTests`
e `CategoryControllerSecurityIT` que falhavam sem banco.

Colar o output completo:
```
Tests run: XX, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## Tarefa 2 — Subir a aplicação e validar health

```bash
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

```bash
curl -s http://localhost:8080/actuator/health | jq .
```
Esperado: `{"status":"UP"}`

---

## Tarefa 3 — Smoke tests do backend

Obter token:
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"tenantId":"00000000-0000-0000-0000-000000000001",
       "email":"admin@dipdv.dev","password":"dipdv@2025"}' \
  | jq -r .token)
echo "Token obtido: ${TOKEN:0:20}..."
```

**[1] Resumo de vendas → esperado: JSON com orderCount, totalRevenue, avgTicket**
```bash
TODAY=$(date +%Y-%m-%d)
curl -s "http://localhost:8080/api/v1/reports/summary?from=$TODAY&to=$TODAY" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

**[2] Top produtos → esperado: lista ordenada (pode ser vazia se não houver vendas)**
```bash
curl -s "http://localhost:8080/api/v1/reports/top-products?limit=5&from=$TODAY&to=$TODAY" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

**[3] Faturamento por forma de pagamento → esperado: agrupado por CASH/PIX**
```bash
curl -s "http://localhost:8080/api/v1/reports/payment-methods?from=$TODAY&to=$TODAY" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

**[4] Limite máximo de 50 em top-products → esperado: retorna no máximo 50 itens**
```bash
curl -s "http://localhost:8080/api/v1/reports/top-products?limit=999&from=$TODAY&to=$TODAY" \
  -H "Authorization: Bearer $TOKEN" | jq 'length <= 50'
```
Esperado: `true`

**[5] PDF de vendas → esperado: arquivo gerado com tamanho > 0**
```bash
curl -s "http://localhost:8080/api/v1/reports/summary/pdf?from=$TODAY&to=$TODAY" \
  -H "Authorization: Bearer $TOKEN" \
  -o relatorio-sprint3.pdf

ls -lh relatorio-sprint3.pdf
```
Esperado: arquivo existente com tamanho > 0 (ex: `12K relatorio-sprint3.pdf`)

**[6] Acesso sem token → esperado: 401**
```bash
curl -s "http://localhost:8080/api/v1/reports/summary" | jq .status
```
Esperado: `401`

**[7] CASHIER tentando acessar relatório → esperado: 403**

> Criar um usuário CASHIER pelo banco ou usar um token com role CASHIER
> para confirmar que o @PreAuthorize está bloqueando corretamente:

```bash
docker exec -it dipdv-postgres psql -U dipdv_app -d dipdv_dev -c "
INSERT INTO users (id, tenant_id, email, password_hash, name, role)
VALUES (
  gen_random_uuid(),
  '00000000-0000-0000-0000-000000000001',
  'cashier@dipdv.dev',
  '\$2a\$12\$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQyCkJ5yI0m.6kgJJ9q2Y5Jmi',
  'Caixa Teste',
  'CASHIER'
) ON CONFLICT DO NOTHING;"

CASHIER_TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"tenantId":"00000000-0000-0000-0000-000000000001",
       "email":"cashier@dipdv.dev","password":"dipdv@2025"}' \
  | jq -r .token)

curl -s "http://localhost:8080/api/v1/reports/summary" \
  -H "Authorization: Bearer $CASHIER_TOKEN" | jq .status
```
Esperado: `403`

---

## Tarefa 4 — Validar o frontend

```bash
cd frontend
npm run dev
```

Abrir `http://localhost:3000` e verificar:

**[8] Login funcional**
- Tela de login carrega em `/login`
- Preencher: tenantId `00000000-0000-0000-0000-000000000001`,
  email `admin@dipdv.dev`, senha `dipdv@2025`
- Após login: redireciona para `/`

**[9] Dashboard com dados**
- Cards de "Pedidos Hoje", "Faturamento Hoje" e "Ticket Médio" visíveis
- Gráfico de pizza aparece se houver pagamentos do dia
- Se não houver dados do dia, os cards mostram 0 sem erro

**[10] Página de relatórios**
- Navegar para `/reports`
- Ajustar filtro de data para um período com vendas
- Clicar "Aplicar" e confirmar que a tabela de top produtos carrega
- Clicar "Exportar PDF" e confirmar que o download é iniciado

> Colar screenshot ou descrição do que foi visto em cada item.

---

## Tarefa 5 — Commits e PR

Confirmar que não há arquivos desnecessários pendentes:
```bash
git status
```

Fazer commits separados por escopo:

```bash
# Backend — relatórios
git add backend/pom.xml
git add backend/src/main/java/com/dipdv/modules/report/
git add backend/src/test/java/com/dipdv/modules/report/

git commit -m "feat(report): relatorios de vendas, top produtos, caixa e PDF

- ReportRepository com 4 queries SQL nativas via EntityManager
- ReportService com filtros from/to e limite seguro (1-50)
- PdfReportService: HTML -> PDF via OpenHTMLtoPDF
- ReportController: 6 endpoints (4 JSON + 2 PDF download)
- 4 testes unitarios ReportServiceTest

Closes #XX (US05.1, US05.2, US05.3, US05.4)"

# Frontend
git add frontend/

git commit -m "feat(frontend): dashboard, login e pagina de relatorios

- Tela de login com JWT e redirecionamento
- DashboardWidget embutido no layout com cards e Chart.js
- Pagina /reports com filtros de data, top produtos e download PDF
- lib/api.ts e lib/auth.ts SSR-safe
- chart.js v4.5.1

Closes #XX (US04.1, US04.2, US04.3)"

git push origin feature/US05.1-reports-dashboard
```

Abrir PR no GitHub:
**De:** `feature/US05.1-reports-dashboard`
**Para:** `develop`
**Título:** `feat(report): Relatórios + Dashboard + PDF — Sprint 3 (MVP Final)`

**Descrição do PR:**
```markdown
## O que este PR faz
Implementa o módulo completo de relatórios e o dashboard do PDV,
fechando o MVP do DiPDV.

## User Stories
Closes #XX (US05.1, US05.2, US05.3, US05.4, US04.1)

## Tipo de mudança
- [x] Nova funcionalidade (relatórios backend)
- [x] Nova funcionalidade (frontend Next.js)

## Evidências
- 58/58 testes unitários passando
- Suite completa com banco: XX/XX (colar número real)
- 7 smoke tests backend validados
- Frontend buildando sem erros TypeScript
- PDF gerado com OpenHTMLtoPDF (ls -lh confirmado)

## Checklist
- [x] Testes passando com banco ativo
- [x] Swagger atualizado com novos endpoints
- [x] PDF download com header Content-Disposition correto
- [x] @PreAuthorize bloqueando CASHIER nos relatórios (403 confirmado)
- [x] Frontend buildando (next build SUCCESS)
- [x] Sem segredos ou dados sensíveis commitados
```

---

## Checklist final

- [ ] Suite completa com banco → output colado (sem failures)
- [ ] [1] `GET /reports/summary` → JSON com os 3 campos
- [ ] [2] `GET /reports/top-products` → lista (pode ser vazia)
- [ ] [3] `GET /reports/payment-methods` → agrupado por método
- [ ] [4] `limit=999` → retorna `true` para `length <= 50`
- [ ] [5] PDF gerado → `ls -lh relatorio-sprint3.pdf` > 0
- [ ] [6] Sem token → 401
- [ ] [7] CASHIER → 403
- [ ] [8] Login funcional no frontend
- [ ] [9] Dashboard com cards visíveis
- [ ] [10] `/reports` com filtros e download PDF
- [ ] Commits feitos separados por escopo
- [ ] PR aberto com link colado

---

## O que NÃO fazer

- Não commitar `application-dev.yml` com credenciais locais
- Não commitar arquivos de IDE ou `target/`
- Não mergear o PR — aguardar revisão do tech lead
