# Prompt — Antigravity: Sprint 3 — Relatórios + Dashboard + PDF (MVP Final)

---

## Contexto

Este é o último sprint do MVP. Com ele entregue, o DiPDV terá um sistema
funcional de ponta a ponta: cadastro → pedido → pagamento → relatórios.

**Branch:** `feature/US05.1-reports-dashboard` a partir de `develop`
**Commits:** `feat(report): ...`, `feat(frontend): ...`
**Ao concluir:** PR + commit no mesmo sprint, conforme nova regra do projeto.

Stack do Sprint 3:
- Backend: Spring Boot + `@Query` SQL + OpenHTMLtoPDF
- Frontend: Next.js 14 + Chart.js + Tailwind CSS
- Filtros: período customizado (`from` / `to`) em todos os relatórios

---

## Decisões de design — ler antes de codar

### 1. Queries SQL diretas via @Query
Relatórios usam `@Query` com JPQL ou SQL nativo no `ReportRepository`.
Sem Spring Data Projections — retornar `List<Object[]>` ou records Java
mapeados manualmente no Service. Mais simples, suficiente para MVP.

### 2. Dashboard embutido no layout principal
O dashboard é um componente React (`DashboardWidget`) exibido na
página principal do PDV. Visível para todos os roles mas os dados
de relatório detalhado são protegidos por role no backend.

### 3. PDF via OpenHTMLtoPDF
O backend gera o HTML do relatório, converte para PDF e retorna
como `application/pdf`. O frontend faz download direto via `<a href>`.
Nenhuma biblioteca de PDF no frontend.

### 4. Filtros de período
Todos os endpoints de relatório aceitam:
```
?from=2026-01-01&to=2026-01-31
```
Sem período informado → padrão: **hoje** (00:00 até 23:59:59).

### 5. Performance das queries
Relatórios são `@Transactional(readOnly = true)`.
Os índices criados na V3 cobrem os campos de filtro.
Para o MVP não usar cache — adicionar Redis no pós-MVP se necessário.

---

## Estrutura a criar

```
backend:
modules/report/
├── repository/
│   └── ReportRepository.java          ← queries SQL nativas
├── dto/
│   ├── SalesSummaryResponse.java      ← vendas do dia / período
│   ├── TopProductResponse.java        ← itens mais vendidos
│   ├── PaymentMethodSummary.java      ← faturamento por forma
│   ├── CashRegisterReportResponse.java ← relatório de fechamento
│   └── ReportFilterRequest.java       ← parâmetros from/to
├── service/
│   ├── ReportService.java             ← lógica dos relatórios
│   └── PdfReportService.java          ← geração HTML → PDF
└── controller/
    └── ReportController.java

frontend:
src/
├── app/
│   ├── (auth)/
│   │   └── login/
│   │       └── page.tsx               ← tela de login
│   └── (pdv)/
│       ├── layout.tsx                 ← layout principal com DashboardWidget
│       ├── page.tsx                   ← PDV (pedidos)
│       └── reports/
│           └── page.tsx               ← página de relatórios detalhados
├── components/
│   ├── dashboard/
│   │   ├── DashboardWidget.tsx        ← widget embutido no layout
│   │   ├── SalesCard.tsx              ← card total vendas do dia
│   │   ├── TicketCard.tsx             ← card ticket médio
│   │   └── PaymentChart.tsx           ← gráfico Chart.js (pizza)
│   └── reports/
│       ├── ReportFilters.tsx          ← seletor de período (from/to)
│       ├── TopProductsTable.tsx       ← tabela top produtos
│       └── CashRegisterReport.tsx     ← relatório de fechamento
└── lib/
    ├── api.ts                         ← cliente HTTP (fetch wrapper)
    └── auth.ts                        ← gerenciamento do JWT no frontend
```

---

## Tarefa 1 — Dependência OpenHTMLtoPDF no pom.xml

```xml
<!-- OpenHTMLtoPDF — HTML/CSS → PDF -->
<dependency>
    <groupId>com.openhtmltopdf</groupId>
    <artifactId>openhtmltopdf-pdfbox</artifactId>
    <version>1.0.10</version>
</dependency>
<dependency>
    <groupId>com.openhtmltopdf</groupId>
    <artifactId>openhtmltopdf-slf4j</artifactId>
    <version>1.0.10</version>
</dependency>
```

---

## Tarefa 2 — ReportRepository (queries SQL)

**Arquivo:** `modules/report/repository/ReportRepository.java`

Interface vazia anotada com `@Repository` — as queries ficam aqui
via `EntityManager` e SQL nativo. Não estender `JpaRepository`.

```java
@Repository
@RequiredArgsConstructor
public class ReportRepository {

    private final EntityManager entityManager;

    /**
     * Total de vendas, número de pedidos e ticket médio por período.
     * Filtra apenas pedidos CLOSED com pelo menos 1 Payment PAID.
     */
    public SalesSummaryResponse getSalesSummary(
            UUID tenantId, OffsetDateTime from, OffsetDateTime to) {

        Object[] result = (Object[]) entityManager.createNativeQuery("""
            SELECT
                COUNT(DISTINCT o.id)                    AS order_count,
                COALESCE(SUM(p.amount), 0)              AS total_revenue,
                COALESCE(AVG(o.total), 0)               AS avg_ticket
            FROM orders o
            JOIN payments p ON p.order_id = o.id
                            AND p.status = 'PAID'
                            AND p.tenant_id = :tenantId
            WHERE o.tenant_id = :tenantId
              AND o.status    = 'CLOSED'
              AND o.closed_at BETWEEN :from AND :to
        """)
        .setParameter("tenantId", tenantId)
        .setParameter("from", from)
        .setParameter("to", to)
        .getSingleResult();

        return new SalesSummaryResponse(
            ((Number) result[0]).longValue(),
            ((Number) result[1]).doubleValue(),
            ((Number) result[2]).doubleValue()
        );
    }

    /**
     * Top N produtos mais vendidos por quantidade no período.
     */
    @SuppressWarnings("unchecked")
    public List<TopProductResponse> getTopProducts(
            UUID tenantId, OffsetDateTime from, OffsetDateTime to, int limit) {

        return entityManager.createNativeQuery("""
            SELECT
                oi.product_id,
                oi.product_name,
                SUM(oi.quantity)        AS total_qty,
                SUM(oi.total_price)     AS total_revenue
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            WHERE o.tenant_id  = :tenantId
              AND o.status     = 'CLOSED'
              AND o.closed_at  BETWEEN :from AND :to
            GROUP BY oi.product_id, oi.product_name
            ORDER BY total_qty DESC
            LIMIT :limit
        """)
        .setParameter("tenantId", tenantId)
        .setParameter("from", from)
        .setParameter("to", to)
        .setParameter("limit", limit)
        .getResultList()
        .stream()
        .map(row -> {
            Object[] r = (Object[]) row;
            return new TopProductResponse(
                UUID.fromString(r[0].toString()),
                r[1].toString(),
                ((Number) r[2]).longValue(),
                ((Number) r[3]).doubleValue()
            );
        })
        .toList();
    }

    /**
     * Faturamento total por forma de pagamento no período.
     */
    @SuppressWarnings("unchecked")
    public List<PaymentMethodSummary> getRevenueByPaymentMethod(
            UUID tenantId, OffsetDateTime from, OffsetDateTime to) {

        return entityManager.createNativeQuery("""
            SELECT
                p.method,
                COUNT(p.id)         AS transaction_count,
                SUM(p.amount)       AS total_amount
            FROM payments p
            JOIN orders o ON o.id = p.order_id
            WHERE p.tenant_id  = :tenantId
              AND p.status     = 'PAID'
              AND o.closed_at  BETWEEN :from AND :to
            GROUP BY p.method
            ORDER BY total_amount DESC
        """)
        .setParameter("tenantId", tenantId)
        .setParameter("from", from)
        .setParameter("to", to)
        .getResultList()
        .stream()
        .map(row -> {
            Object[] r = (Object[]) row;
            return new PaymentMethodSummary(
                r[0].toString(),
                ((Number) r[1]).longValue(),
                ((Number) r[2]).doubleValue()
            );
        })
        .toList();
    }

    /**
     * Relatório de fechamento de um turno de caixa específico.
     */
    public CashRegisterReportResponse getCashRegisterReport(
            UUID tenantId, UUID cashRegisterId) {

        // Buscar os dados do caixa
        Object[] cr = (Object[]) entityManager.createNativeQuery("""
            SELECT cr.opening_balance, cr.closing_balance,
                   cr.physical_balance, cr.difference,
                   cr.total_cash, cr.total_pix,
                   cr.opened_at, cr.closed_at,
                   u.name AS operator_name
            FROM cash_registers cr
            JOIN users u ON u.id = cr.opened_by
            WHERE cr.id = :id AND cr.tenant_id = :tenantId
        """)
        .setParameter("id", cashRegisterId)
        .setParameter("tenantId", tenantId)
        .getSingleResult();

        // Buscar movimentações do turno
        List<Object[]> movements = entityManager.createNativeQuery("""
            SELECT cm.type, cm.amount, cm.description, cm.created_at
            FROM cash_movements cm
            WHERE cm.cash_register_id = :id
            ORDER BY cm.created_at ASC
        """)
        .setParameter("id", cashRegisterId)
        .getResultList();

        return CashRegisterReportResponse.from(cr, movements);
    }
}
```

---

## Tarefa 3 — DTOs de Relatório

**`ReportFilterRequest.java`** — record com:
```java
public record ReportFilterRequest(
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate from,

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate to
) {
    // Converte para OffsetDateTime início/fim do dia no fuso do servidor
    public OffsetDateTime fromDateTime() {
        return (from != null ? from : LocalDate.now())
            .atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    public OffsetDateTime toDateTime() {
        return (to != null ? to : LocalDate.now())
            .atTime(23, 59, 59).atOffset(ZoneOffset.UTC);
    }
}
```

**`SalesSummaryResponse.java`** — record com:
`orderCount` (long), `totalRevenue` (double), `avgTicket` (double)

**`TopProductResponse.java`** — record com:
`productId` (UUID), `productName` (String), `totalQty` (long), `totalRevenue` (double)

**`PaymentMethodSummary.java`** — record com:
`method` (String), `transactionCount` (long), `totalAmount` (double)

**`CashRegisterReportResponse.java`** — record com:
`operatorName`, `openingBalance`, `closingBalance`, `physicalBalance`,
`difference`, `totalCash`, `totalPix`, `openedAt`, `closedAt`,
`movements` (`List<MovementItem>`)

```java
// Record interno
public record MovementItem(String type, double amount, String description, OffsetDateTime createdAt) {}

// Factory method
public static CashRegisterReportResponse from(Object[] cr, List<Object[]> movements) { ... }
```

---

## Tarefa 4 — ReportService

**Arquivo:** `modules/report/service/ReportService.java`

```java
@Service @RequiredArgsConstructor @Slf4j
public class ReportService {

    private final ReportRepository reportRepository;

    @Transactional(readOnly = true)
    public SalesSummaryResponse getSalesSummary(ReportFilterRequest filter) {
        return reportRepository.getSalesSummary(
            TenantContext.getRequired(),
            filter.fromDateTime(),
            filter.toDateTime()
        );
    }

    @Transactional(readOnly = true)
    public List<TopProductResponse> getTopProducts(
            ReportFilterRequest filter, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50); // entre 1 e 50
        return reportRepository.getTopProducts(
            TenantContext.getRequired(),
            filter.fromDateTime(),
            filter.toDateTime(),
            safeLimit
        );
    }

    @Transactional(readOnly = true)
    public List<PaymentMethodSummary> getRevenueByPaymentMethod(
            ReportFilterRequest filter) {
        return reportRepository.getRevenueByPaymentMethod(
            TenantContext.getRequired(),
            filter.fromDateTime(),
            filter.toDateTime()
        );
    }

    @Transactional(readOnly = true)
    public CashRegisterReportResponse getCashRegisterReport(UUID cashRegisterId) {
        return reportRepository.getCashRegisterReport(
            TenantContext.getRequired(),
            cashRegisterId
        );
    }
}
```

---

## Tarefa 5 — PdfReportService

**Arquivo:** `modules/report/service/PdfReportService.java`

```java
@Service @RequiredArgsConstructor @Slf4j
public class PdfReportService {

    private final ReportService reportService;

    /**
     * Gera PDF do relatório de vendas do período.
     * Fluxo: montar HTML → converter com OpenHTMLtoPDF → retornar bytes.
     */
    public byte[] generateSalesReportPdf(ReportFilterRequest filter) {
        SalesSummaryResponse summary = reportService.getSalesSummary(filter);
        List<TopProductResponse> topProducts = reportService.getTopProducts(filter, 10);
        List<PaymentMethodSummary> byMethod = reportService.getRevenueByPaymentMethod(filter);

        String html = buildSalesReportHtml(summary, topProducts, byMethod, filter);
        return convertHtmlToPdf(html);
    }

    /**
     * Gera PDF do relatório de fechamento de caixa.
     */
    public byte[] generateCashRegisterPdf(UUID cashRegisterId) {
        CashRegisterReportResponse report =
            reportService.getCashRegisterReport(cashRegisterId);
        String html = buildCashRegisterHtml(report);
        return convertHtmlToPdf(html);
    }

    private byte[] convertHtmlToPdf(String html) {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        } catch (Exception e) {
            log.error("Erro ao gerar PDF", e);
            throw new BusinessException("Erro ao gerar PDF", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String buildSalesReportHtml(
            SalesSummaryResponse summary,
            List<TopProductResponse> topProducts,
            List<PaymentMethodSummary> byMethod,
            ReportFilterRequest filter) {

        StringBuilder rows = new StringBuilder();
        for (TopProductResponse p : topProducts) {
            rows.append(String.format(
                "<tr><td>%s</td><td>%d</td><td>R$ %.2f</td></tr>",
                p.productName(), p.totalQty(), p.totalRevenue()
            ));
        }

        StringBuilder methodRows = new StringBuilder();
        for (PaymentMethodSummary m : byMethod) {
            methodRows.append(String.format(
                "<tr><td>%s</td><td>%d</td><td>R$ %.2f</td></tr>",
                m.method(), m.transactionCount(), m.totalAmount()
            ));
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="UTF-8"/>
              <style>
                body { font-family: Arial, sans-serif; font-size: 12px; color: #333; }
                h1   { color: #1E3A5F; font-size: 20px; }
                h2   { color: #2D6A9F; font-size: 14px; margin-top: 20px; }
                table { width: 100%%; border-collapse: collapse; margin-top: 8px; }
                th   { background: #1E3A5F; color: white; padding: 6px; text-align: left; }
                td   { padding: 5px; border-bottom: 1px solid #eee; }
                .summary { display: flex; gap: 20px; margin: 12px 0; }
                .card { background: #f5f5f5; padding: 10px; border-radius: 4px; flex: 1; }
                .card .value { font-size: 18px; font-weight: bold; color: #1E3A5F; }
                .period { color: #888; font-size: 11px; margin-bottom: 16px; }
              </style>
            </head>
            <body>
              <h1>DiPDV — Relatório de Vendas</h1>
              <p class="period">Período: %s até %s</p>

              <div class="summary">
                <div class="card">
                  <div>Pedidos Fechados</div>
                  <div class="value">%d</div>
                </div>
                <div class="card">
                  <div>Faturamento Total</div>
                  <div class="value">R$ %.2f</div>
                </div>
                <div class="card">
                  <div>Ticket Médio</div>
                  <div class="value">R$ %.2f</div>
                </div>
              </div>

              <h2>Top Produtos</h2>
              <table>
                <tr><th>Produto</th><th>Qtd Vendida</th><th>Faturamento</th></tr>
                %s
              </table>

              <h2>Faturamento por Forma de Pagamento</h2>
              <table>
                <tr><th>Método</th><th>Transações</th><th>Total</th></tr>
                %s
              </table>
            </body>
            </html>
        """.formatted(
            filter.from(), filter.to(),
            summary.orderCount(),
            summary.totalRevenue(),
            summary.avgTicket(),
            rows,
            methodRows
        );
    }

    private String buildCashRegisterHtml(CashRegisterReportResponse report) {
        StringBuilder movRows = new StringBuilder();
        for (var m : report.movements()) {
            movRows.append(String.format(
                "<tr><td>%s</td><td>%s</td><td>R$ %.2f</td></tr>",
                m.type(), m.description(), m.amount()
            ));
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="UTF-8"/>
              <style>
                body { font-family: Arial, sans-serif; font-size: 12px; }
                h1   { color: #1E3A5F; font-size: 18px; }
                table { width: 100%%; border-collapse: collapse; }
                th { background: #1E3A5F; color: white; padding: 6px; }
                td { padding: 5px; border-bottom: 1px solid #eee; }
                .total { font-weight: bold; font-size: 14px; }
              </style>
            </head>
            <body>
              <h1>Relatório de Fechamento de Caixa</h1>
              <p>Operador: %s | Abertura: %s | Fechamento: %s</p>
              <table>
                <tr><th>Item</th><th>Valor</th></tr>
                <tr><td>Saldo Inicial</td><td>R$ %.2f</td></tr>
                <tr><td>Total Dinheiro</td><td>R$ %.2f</td></tr>
                <tr><td>Total Pix</td><td>R$ %.2f</td></tr>
                <tr><td>Saldo Calculado</td><td>R$ %.2f</td></tr>
                <tr><td>Saldo Físico (informado)</td><td>R$ %.2f</td></tr>
                <tr class="total"><td>Diferença</td><td>R$ %.2f</td></tr>
              </table>
              <h2>Movimentações</h2>
              <table>
                <tr><th>Tipo</th><th>Descrição</th><th>Valor</th></tr>
                %s
              </table>
            </body>
            </html>
        """.formatted(
            report.operatorName(), report.openedAt(), report.closedAt(),
            report.openingBalance(), report.totalCash(), report.totalPix(),
            report.closingBalance(), report.physicalBalance(),
            report.difference(), movRows
        );
    }
}
```

---

## Tarefa 6 — ReportController

```
@RestController @RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Relatórios", description = "Relatórios de vendas e caixa")
```

| Método | Path | Role | Descrição |
|---|---|---|---|
| GET | `/summary` | MANAGER | Resumo de vendas do período |
| GET | `/top-products` | MANAGER | Top produtos (`?limit=10`) |
| GET | `/payment-methods` | MANAGER | Faturamento por forma de pagamento |
| GET | `/cash-register/{id}` | MANAGER | Relatório de fechamento de caixa |
| GET | `/summary/pdf` | MANAGER | Download PDF do relatório de vendas |
| GET | `/cash-register/{id}/pdf` | MANAGER | Download PDF do fechamento de caixa |

Todos aceitam `?from=YYYY-MM-DD&to=YYYY-MM-DD`.

**Endpoints PDF retornam `ResponseEntity<byte[]>`:**
```java
@GetMapping(value = "/summary/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
public ResponseEntity<byte[]> downloadSalesReportPdf(
        @ModelAttribute ReportFilterRequest filter) {

    byte[] pdf = pdfReportService.generateSalesReportPdf(filter);

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"relatorio-vendas.pdf\"")
        .contentType(MediaType.APPLICATION_PDF)
        .body(pdf);
}
```

---

## Tarefa 7 — Frontend Next.js

### 7a. lib/api.ts — cliente HTTP

```typescript
const API_URL = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

export async function apiFetch<T>(
  path: string,
  options?: RequestInit
): Promise<T> {
  const token = localStorage.getItem('dipdv_token');

  const res = await fetch(`${API_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options?.headers,
    },
  });

  if (!res.ok) {
    const error = await res.json().catch(() => ({}));
    throw new Error(error.message ?? `HTTP ${res.status}`);
  }

  return res.json();
}
```

### 7b. lib/auth.ts — gerenciamento JWT

```typescript
export interface AuthData {
  token: string;
  userId: string;
  tenantId: string;
  name: string;
  role: 'ADMIN' | 'MANAGER' | 'CASHIER';
  expiresIn: number;
}

export function saveAuth(data: AuthData) {
  localStorage.setItem('dipdv_token', data.token);
  localStorage.setItem('dipdv_user', JSON.stringify(data));
}

export function getAuth(): AuthData | null {
  const raw = localStorage.getItem('dipdv_user');
  return raw ? JSON.parse(raw) : null;
}

export function clearAuth() {
  localStorage.removeItem('dipdv_token');
  localStorage.removeItem('dipdv_user');
}

export function isAuthenticated(): boolean {
  return !!localStorage.getItem('dipdv_token');
}
```

### 7c. Tela de Login — `app/(auth)/login/page.tsx`

```typescript
'use client';
import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { saveAuth } from '@/lib/auth';

export default function LoginPage() {
  const router = useRouter();
  const [form, setForm] = useState({
    tenantId: '', email: '', password: ''
  });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError('');
    try {
      const res = await fetch(
        `${process.env.NEXT_PUBLIC_API_URL}/api/v1/auth/login`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(form),
        }
      );
      if (!res.ok) throw new Error('Credenciais inválidas');
      const data = await res.json();
      saveAuth(data);
      router.push('/');
    } catch (err: any) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="bg-white p-8 rounded-xl shadow-md w-full max-w-md">
        <h1 className="text-2xl font-bold text-blue-900 mb-6">DiPDV</h1>
        {error && (
          <div className="bg-red-50 text-red-700 p-3 rounded mb-4 text-sm">
            {error}
          </div>
        )}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Tenant ID
            </label>
            <input
              type="text"
              className="w-full border rounded-lg px-3 py-2 text-sm"
              placeholder="UUID do estabelecimento"
              value={form.tenantId}
              onChange={e => setForm({ ...form, tenantId: e.target.value })}
              required
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              E-mail
            </label>
            <input
              type="email"
              className="w-full border rounded-lg px-3 py-2 text-sm"
              value={form.email}
              onChange={e => setForm({ ...form, email: e.target.value })}
              required
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Senha
            </label>
            <input
              type="password"
              className="w-full border rounded-lg px-3 py-2 text-sm"
              value={form.password}
              onChange={e => setForm({ ...form, password: e.target.value })}
              required
            />
          </div>
          <button
            type="submit"
            disabled={loading}
            className="w-full bg-blue-900 text-white rounded-lg py-2 font-medium
                       hover:bg-blue-800 disabled:opacity-50 transition"
          >
            {loading ? 'Entrando...' : 'Entrar'}
          </button>
        </form>
      </div>
    </div>
  );
}
```

### 7d. DashboardWidget — `components/dashboard/DashboardWidget.tsx`

```typescript
'use client';
import { useEffect, useState } from 'react';
import { apiFetch } from '@/lib/api';
import { getAuth } from '@/lib/auth';
import PaymentChart from './PaymentChart';

interface Summary {
  orderCount: number;
  totalRevenue: number;
  avgTicket: number;
}

interface PaymentMethod {
  method: string;
  transactionCount: number;
  totalAmount: number;
}

export default function DashboardWidget() {
  const auth = getAuth();
  const canViewReports = auth?.role === 'ADMIN' || auth?.role === 'MANAGER';

  const [summary, setSummary] = useState<Summary | null>(null);
  const [methods, setMethods] = useState<PaymentMethod[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!canViewReports) return;

    const today = new Date().toISOString().split('T')[0];
    Promise.all([
      apiFetch<Summary>(`/api/v1/reports/summary?from=${today}&to=${today}`),
      apiFetch<PaymentMethod[]>(`/api/v1/reports/payment-methods?from=${today}&to=${today}`),
    ])
      .then(([s, m]) => { setSummary(s); setMethods(m); })
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [canViewReports]);

  if (!canViewReports) return null;
  if (loading) return (
    <div className="text-sm text-gray-400 p-4">Carregando dashboard...</div>
  );

  return (
    <div className="grid grid-cols-1 md:grid-cols-3 gap-4 p-4 bg-gray-50 rounded-xl mb-4">
      {/* Cards de resumo */}
      <div className="bg-white rounded-lg p-4 shadow-sm">
        <p className="text-xs text-gray-500 uppercase tracking-wide">
          Pedidos Hoje
        </p>
        <p className="text-2xl font-bold text-blue-900 mt-1">
          {summary?.orderCount ?? 0}
        </p>
      </div>
      <div className="bg-white rounded-lg p-4 shadow-sm">
        <p className="text-xs text-gray-500 uppercase tracking-wide">
          Faturamento Hoje
        </p>
        <p className="text-2xl font-bold text-blue-900 mt-1">
          R$ {summary?.totalRevenue.toFixed(2) ?? '0,00'}
        </p>
      </div>
      <div className="bg-white rounded-lg p-4 shadow-sm">
        <p className="text-xs text-gray-500 uppercase tracking-wide">
          Ticket Médio
        </p>
        <p className="text-2xl font-bold text-blue-900 mt-1">
          R$ {summary?.avgTicket.toFixed(2) ?? '0,00'}
        </p>
      </div>

      {/* Gráfico de pizza — formas de pagamento */}
      {methods.length > 0 && (
        <div className="md:col-span-3 bg-white rounded-lg p-4 shadow-sm">
          <p className="text-sm font-medium text-gray-700 mb-3">
            Faturamento por Forma de Pagamento
          </p>
          <PaymentChart data={methods} />
        </div>
      )}
    </div>
  );
}
```

### 7e. PaymentChart — `components/dashboard/PaymentChart.tsx`

```typescript
'use client';
import { useEffect, useRef } from 'react';
import { Chart, ArcElement, Tooltip, Legend, DoughnutController } from 'chart.js';

Chart.register(ArcElement, Tooltip, Legend, DoughnutController);

interface Props {
  data: { method: string; totalAmount: number }[];
}

const COLORS = ['#1E3A5F', '#2D6A9F', '#F4A300', '#27AE60', '#E74C3C'];

export default function PaymentChart({ data }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const chartRef = useRef<Chart | null>(null);

  useEffect(() => {
    if (!canvasRef.current) return;
    if (chartRef.current) chartRef.current.destroy();

    chartRef.current = new Chart(canvasRef.current, {
      type: 'doughnut',
      data: {
        labels: data.map(d => d.method),
        datasets: [{
          data: data.map(d => d.totalAmount),
          backgroundColor: COLORS.slice(0, data.length),
          borderWidth: 2,
        }],
      },
      options: {
        responsive: true,
        plugins: {
          legend: { position: 'right' },
          tooltip: {
            callbacks: {
              label: ctx =>
                ` R$ ${(ctx.raw as number).toFixed(2)}`,
            },
          },
        },
      },
    });

    return () => chartRef.current?.destroy();
  }, [data]);

  return (
    <div className="flex justify-center" style={{ height: 200 }}>
      <canvas ref={canvasRef} />
    </div>
  );
}
```

### 7f. Página de Relatórios — `app/(pdv)/reports/page.tsx`

```typescript
'use client';
import { useState } from 'react';
import ReportFilters from '@/components/reports/ReportFilters';
import TopProductsTable from '@/components/reports/TopProductsTable';
import { apiFetch } from '@/lib/api';

export default function ReportsPage() {
  const today = new Date().toISOString().split('T')[0];
  const [from, setFrom] = useState(today);
  const [to, setTo] = useState(today);
  const [topProducts, setTopProducts] = useState([]);
  const [loading, setLoading] = useState(false);

  async function loadReports() {
    setLoading(true);
    try {
      const products = await apiFetch(
        `/api/v1/reports/top-products?from=${from}&to=${to}&limit=10`
      );
      setTopProducts(products as any);
    } finally {
      setLoading(false);
    }
  }

  function downloadPdf() {
    const token = localStorage.getItem('dipdv_token');
    const url = `${process.env.NEXT_PUBLIC_API_URL}/api/v1/reports/summary/pdf?from=${from}&to=${to}`;
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', 'relatorio-vendas.pdf');

    // Fetch com authorization header para download autenticado
    fetch(url, { headers: { Authorization: `Bearer ${token}` } })
      .then(res => res.blob())
      .then(blob => {
        const objectUrl = URL.createObjectURL(blob);
        link.href = objectUrl;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(objectUrl);
      });
  }

  return (
    <div className="p-6">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-xl font-bold text-blue-900">Relatórios</h1>
        <button
          onClick={downloadPdf}
          className="bg-blue-900 text-white px-4 py-2 rounded-lg text-sm
                     hover:bg-blue-800 transition"
        >
          Exportar PDF
        </button>
      </div>

      <ReportFilters
        from={from} to={to}
        onFromChange={setFrom} onToChange={setTo}
        onApply={loadReports}
      />

      {loading ? (
        <p className="text-gray-400 text-sm mt-4">Carregando...</p>
      ) : (
        <TopProductsTable data={topProducts} />
      )}
    </div>
  );
}
```

### 7g. Componentes de suporte

**`ReportFilters.tsx`:**
```typescript
interface Props {
  from: string; to: string;
  onFromChange: (v: string) => void;
  onToChange: (v: string) => void;
  onApply: () => void;
}

export default function ReportFilters(
  { from, to, onFromChange, onToChange, onApply }: Props
) {
  return (
    <div className="flex gap-3 items-end mb-6">
      <div>
        <label className="block text-xs text-gray-500 mb-1">De</label>
        <input type="date" value={from}
          onChange={e => onFromChange(e.target.value)}
          className="border rounded px-3 py-2 text-sm" />
      </div>
      <div>
        <label className="block text-xs text-gray-500 mb-1">Até</label>
        <input type="date" value={to}
          onChange={e => onToChange(e.target.value)}
          className="border rounded px-3 py-2 text-sm" />
      </div>
      <button onClick={onApply}
        className="bg-gray-800 text-white px-4 py-2 rounded text-sm hover:bg-gray-700">
        Aplicar
      </button>
    </div>
  );
}
```

**`TopProductsTable.tsx`:**
```typescript
interface Product {
  productName: string; totalQty: number; totalRevenue: number;
}
export default function TopProductsTable({ data }: { data: Product[] }) {
  if (!data.length) return (
    <p className="text-gray-400 text-sm">Nenhum produto vendido no período.</p>
  );
  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="bg-blue-900 text-white">
          <th className="p-3 text-left">Produto</th>
          <th className="p-3 text-right">Qtd Vendida</th>
          <th className="p-3 text-right">Faturamento</th>
        </tr>
      </thead>
      <tbody>
        {data.map((p, i) => (
          <tr key={i} className={i % 2 === 0 ? 'bg-gray-50' : 'bg-white'}>
            <td className="p-3">{p.productName}</td>
            <td className="p-3 text-right">{p.totalQty}</td>
            <td className="p-3 text-right">R$ {p.totalRevenue.toFixed(2)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
```

### 7h. Variável de ambiente

Criar `frontend/.env.local`:
```
NEXT_PUBLIC_API_URL=http://localhost:8080
```

### 7i. Instalar Chart.js

```bash
cd frontend
npm install chart.js
```

---

## Tarefa 8 — Testes unitários do ReportService

**Arquivo:** `test/java/com/dipdv/modules/report/service/ReportServiceTest.java`

```java
// 4 cenários obrigatórios
getSalesSummary_whenNoPaidOrders_shouldReturnZeroes()
getSalesSummary_whenHasPaidOrders_shouldReturnCorrectTotals()
getTopProducts_whenLimitExceeds50_shouldCapAt50()
getRevenueByPaymentMethod_shouldGroupByMethod()
```

> Mockar `ReportRepository` via `@Mock` e `@InjectMocks`.
> Usar `MockedStatic<TenantContext>` como nos testes anteriores.

---

## Tarefa 9 — Validação

### 9a. Backend

```bash
cd backend
.\mvnw.cmd test
```
Esperado: mínimo **59 testes** (55 anteriores + 4 novos).

```bash
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"tenantId":"00000000-0000-0000-0000-000000000001",
       "email":"admin@dipdv.dev","password":"dipdv@2025"}' \
  | jq -r .token)

# Relatório de resumo
curl -s "http://localhost:8080/api/v1/reports/summary" \
  -H "Authorization: Bearer $TOKEN" | jq .

# Top produtos
curl -s "http://localhost:8080/api/v1/reports/top-products?limit=5" \
  -H "Authorization: Bearer $TOKEN" | jq .

# Por forma de pagamento
curl -s "http://localhost:8080/api/v1/reports/payment-methods" \
  -H "Authorization: Bearer $TOKEN" | jq .

# Download PDF (salvar arquivo)
curl -s "http://localhost:8080/api/v1/reports/summary/pdf" \
  -H "Authorization: Bearer $TOKEN" \
  -o relatorio-teste.pdf

# Confirmar que o PDF foi gerado
ls -lh relatorio-teste.pdf
```
Esperado: `relatorio-teste.pdf` com tamanho > 0.

### 9b. Frontend

```bash
cd frontend
npm install
npm run dev
```

Verificar em `http://localhost:3000`:
- Tela de login carrega
- Após login, dashboard exibe cards de vendas do dia
- Gráfico de pizza aparece se houver pagamentos
- Página `/reports` carrega com filtros de data
- Botão "Exportar PDF" faz download do arquivo

---

## Tarefa 10 — Commit e PR

```bash
# Backend
git add backend/pom.xml
git add backend/src/main/java/com/dipdv/modules/report/
git add backend/src/test/java/com/dipdv/modules/report/

git commit -m "feat(report): relatorios de vendas, top produtos, caixa e PDF

- ReportRepository com queries SQL nativas (summary, top-products, payment-methods)
- ReportService com filtros from/to e limite seguro em top-products
- PdfReportService com OpenHTMLtoPDF (HTML -> PDF)
- ReportController: 6 endpoints (4 JSON + 2 PDF download)
- 4 testes unitarios ReportServiceTest

Closes #XX (US05.1, US05.2, US05.3, US05.4)"

# Frontend
git add frontend/
git commit -m "feat(frontend): dashboard, login e pagina de relatorios

- Tela de login com JWT e redirecionamento
- DashboardWidget embutido no layout principal
- Cards: pedidos, faturamento e ticket medio do dia
- PaymentChart: grafico de pizza com Chart.js
- Pagina /reports com filtros de data e top produtos
- Download autenticado de PDF
- lib/api.ts e lib/auth.ts

Closes #XX (US04.1, US04.2, US04.3)"

git push origin feature/US05.1-reports-dashboard
```

Abrir PR: `feature/US05.1-reports-dashboard` → `develop`

---

## Checklist final

- [ ] `.\mvnw.cmd test` → mínimo 59 testes, BUILD SUCCESS
- [ ] `GET /reports/summary` → JSON com orderCount, totalRevenue, avgTicket
- [ ] `GET /reports/top-products?limit=5` → lista ordenada por quantidade
- [ ] `GET /reports/payment-methods` → agrupado por CASH/PIX
- [ ] `GET /reports/summary/pdf` → arquivo PDF gerado (ls -lh > 0)
- [ ] Frontend: tela de login funcional
- [ ] Frontend: dashboard com cards e gráfico carregando
- [ ] Frontend: página /reports com filtros e tabela
- [ ] Frontend: download de PDF funcionando
- [ ] PR aberto com link + CI verde

---

## O que NÃO implementar aqui

- Relatório de estoque — pós-MVP
- Exportação CSV — pós-MVP
- Cache Redis nos relatórios — pós-MVP
- Notificações de estoque baixo via WhatsApp — pós-MVP
- Integração iFood — pós-MVP
