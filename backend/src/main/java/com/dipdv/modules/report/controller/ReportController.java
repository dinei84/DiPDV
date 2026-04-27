package com.dipdv.modules.report.controller;

import com.dipdv.modules.report.dto.CashRegisterReportResponse;
import com.dipdv.modules.report.dto.PaymentMethodSummary;
import com.dipdv.modules.report.dto.ReportFilterRequest;
import com.dipdv.modules.report.dto.SalesSummaryResponse;
import com.dipdv.modules.report.dto.TopProductResponse;
import com.dipdv.modules.report.service.PdfReportService;
import com.dipdv.modules.report.service.ReportService;
import com.dipdv.shared.module.annotation.RequiresModule;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Relatórios", description = "Relatórios de vendas e caixa")
@RequiresModule("REPORTS")
public class ReportController {

    private final ReportService reportService;
    private final PdfReportService pdfReportService;

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<SalesSummaryResponse> getSalesSummary(
            @ModelAttribute ReportFilterRequest filter) {
        return ResponseEntity.ok(reportService.getSalesSummary(filter));
    }

    @GetMapping("/top-products")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<List<TopProductResponse>> getTopProducts(
            @ModelAttribute ReportFilterRequest filter,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(reportService.getTopProducts(filter, limit));
    }

    @GetMapping("/payment-methods")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<List<PaymentMethodSummary>> getRevenueByPaymentMethod(
            @ModelAttribute ReportFilterRequest filter) {
        return ResponseEntity.ok(reportService.getRevenueByPaymentMethod(filter));
    }

    @GetMapping("/cash-register/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<CashRegisterReportResponse> getCashRegisterReport(
            @PathVariable UUID id) {
        return ResponseEntity.ok(reportService.getCashRegisterReport(id));
    }

    @GetMapping(value = "/summary/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<byte[]> downloadSalesReportPdf(
            @ModelAttribute ReportFilterRequest filter) {
        byte[] pdf = pdfReportService.generateSalesReportPdf(filter);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"relatorio-vendas.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping(value = "/cash-register/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<byte[]> downloadCashRegisterPdf(
            @PathVariable UUID id) {
        byte[] pdf = pdfReportService.generateCashRegisterPdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"relatorio-caixa.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
