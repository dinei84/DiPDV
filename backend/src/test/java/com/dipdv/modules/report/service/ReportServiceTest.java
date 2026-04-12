package com.dipdv.modules.report.service;

import com.dipdv.modules.report.dto.PaymentMethodSummary;
import com.dipdv.modules.report.dto.ReportFilterRequest;
import com.dipdv.modules.report.dto.SalesSummaryResponse;
import com.dipdv.modules.report.dto.TopProductResponse;
import com.dipdv.modules.report.repository.ReportRepository;
import com.dipdv.shared.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @InjectMocks
    private ReportService reportService;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private MockedStatic<TenantContext> mockedTenantContext;

    @BeforeEach
    void setUp() {
        mockedTenantContext = mockStatic(TenantContext.class);
        mockedTenantContext.when(TenantContext::getRequired).thenReturn(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        mockedTenantContext.close();
    }

    @Test
    void getSalesSummary_whenNoPaidOrders_shouldReturnZeroes() {
        ReportFilterRequest filter = new ReportFilterRequest(LocalDate.now(), LocalDate.now());
        SalesSummaryResponse zeroes = new SalesSummaryResponse(0L, 0.0, 0.0);

        when(reportRepository.getSalesSummary(eq(TENANT_ID), any(), any()))
                .thenReturn(zeroes);

        SalesSummaryResponse result = reportService.getSalesSummary(filter);

        assertEquals(0L, result.orderCount());
        assertEquals(0.0, result.totalRevenue());
        assertEquals(0.0, result.avgTicket());
    }

    @Test
    void getSalesSummary_whenHasPaidOrders_shouldReturnCorrectTotals() {
        ReportFilterRequest filter = new ReportFilterRequest(LocalDate.now(), LocalDate.now());
        SalesSummaryResponse expected = new SalesSummaryResponse(5L, 250.0, 50.0);

        when(reportRepository.getSalesSummary(eq(TENANT_ID), any(), any()))
                .thenReturn(expected);

        SalesSummaryResponse result = reportService.getSalesSummary(filter);

        assertEquals(5L, result.orderCount());
        assertEquals(250.0, result.totalRevenue());
        assertEquals(50.0, result.avgTicket());
    }

    @Test
    void getTopProducts_whenLimitExceeds50_shouldCapAt50() {
        ReportFilterRequest filter = new ReportFilterRequest(LocalDate.now(), LocalDate.now());
        List<TopProductResponse> products = List.of(
                new TopProductResponse(UUID.randomUUID(), "X-Burguer", 100L, 500.0)
        );

        when(reportRepository.getTopProducts(eq(TENANT_ID), any(), any(), eq(50)))
                .thenReturn(products);

        // Passar limite 999 — deve ser truncado a 50
        List<TopProductResponse> result = reportService.getTopProducts(filter, 999);

        assertEquals(1, result.size());
        assertEquals("X-Burguer", result.get(0).productName());
    }

    @Test
    void getRevenueByPaymentMethod_shouldGroupByMethod() {
        ReportFilterRequest filter = new ReportFilterRequest(LocalDate.now(), LocalDate.now());
        List<PaymentMethodSummary> methods = List.of(
                new PaymentMethodSummary("CASH", 10L, 100.0),
                new PaymentMethodSummary("PIX", 5L, 75.0)
        );

        when(reportRepository.getRevenueByPaymentMethod(eq(TENANT_ID), any(), any()))
                .thenReturn(methods);

        List<PaymentMethodSummary> result = reportService.getRevenueByPaymentMethod(filter);

        assertEquals(2, result.size());
        assertEquals("CASH", result.get(0).method());
        assertEquals(10L, result.get(0).transactionCount());
        assertEquals("PIX", result.get(1).method());
        assertEquals(75.0, result.get(1).totalAmount());
    }
}
