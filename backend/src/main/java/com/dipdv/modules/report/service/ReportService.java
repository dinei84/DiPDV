package com.dipdv.modules.report.service;

import com.dipdv.modules.report.dto.CashRegisterReportResponse;
import com.dipdv.modules.report.dto.PaymentMethodSummary;
import com.dipdv.modules.report.dto.ReportFilterRequest;
import com.dipdv.modules.report.dto.SalesSummaryResponse;
import com.dipdv.modules.report.dto.TopProductResponse;
import com.dipdv.modules.report.repository.ReportRepository;
import com.dipdv.shared.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
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
    public List<TopProductResponse> getTopProducts(ReportFilterRequest filter, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        return reportRepository.getTopProducts(
                TenantContext.getRequired(),
                filter.fromDateTime(),
                filter.toDateTime(),
                safeLimit
        );
    }

    @Transactional(readOnly = true)
    public List<PaymentMethodSummary> getRevenueByPaymentMethod(ReportFilterRequest filter) {
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
