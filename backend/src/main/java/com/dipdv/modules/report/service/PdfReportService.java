package com.dipdv.modules.report.service;

import com.dipdv.modules.report.dto.CashRegisterReportResponse;
import com.dipdv.modules.report.dto.PaymentMethodSummary;
import com.dipdv.modules.report.dto.ReportFilterRequest;
import com.dipdv.modules.report.dto.SalesSummaryResponse;
import com.dipdv.modules.report.dto.TopProductResponse;
import com.dipdv.shared.exception.BusinessException;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
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
        CashRegisterReportResponse report = reportService.getCashRegisterReport(cashRegisterId);
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
