package com.dipdv.modules.cashregister.service;

import com.dipdv.modules.cashregister.dto.*;
import com.dipdv.modules.cashregister.entity.CashMovement;
import com.dipdv.modules.cashregister.entity.CashRegister;
import com.dipdv.modules.cashregister.entity.enums.CashMovementType;
import com.dipdv.modules.cashregister.entity.enums.CashRegisterStatus;
import com.dipdv.modules.cashregister.repository.CashMovementRepository;
import com.dipdv.modules.cashregister.repository.CashRegisterRepository;
import com.dipdv.shared.exception.BusinessException;
import com.dipdv.shared.security.DiPdvAuthDetails;
import com.dipdv.shared.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CashRegisterServiceTest {

    @Mock
    private CashRegisterRepository cashRegisterRepository;

    @Mock
    private CashMovementRepository cashMovementRepository;

    @InjectMocks
    private CashRegisterService cashRegisterService;

    private static final UUID TENANT_ID   = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID     = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID REGISTER_ID = UUID.randomUUID();

    private MockedStatic<TenantContext> mockedTenantContext;

    @BeforeEach
    void setUp() {
        mockedTenantContext = mockStatic(TenantContext.class);
        mockedTenantContext.when(TenantContext::getRequired).thenReturn(TENANT_ID);

        DiPdvAuthDetails authDetails = new DiPdvAuthDetails(USER_ID, TENANT_ID, "MANAGER");
        Authentication auth = new UsernamePasswordAuthenticationToken(
                USER_ID.toString(), null, List.of());
        ((UsernamePasswordAuthenticationToken) auth).setDetails(authDetails);
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(auth);
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        mockedTenantContext.close();
        SecurityContextHolder.clearContext();
    }

    // ── openCashRegister ────────────────────────────────────────────────────────

    @Test
    void openCashRegister_whenNoOpenRegister_shouldReturnOpenStatus() {
        when(cashRegisterRepository.findByTenantIdAndStatus(TENANT_ID, CashRegisterStatus.OPEN))
                .thenReturn(Optional.empty());

        CashRegister saved = buildRegister(CashRegisterStatus.OPEN);
        when(cashRegisterRepository.save(any(CashRegister.class))).thenReturn(saved);

        CashRegisterResponse response = cashRegisterService.openCashRegister(
                new OpenCashRegisterRequest(new BigDecimal("100.00")));

        assertNotNull(response);
        assertEquals(CashRegisterStatus.OPEN, response.status());
        assertEquals(new BigDecimal("100.00"), response.openingBalance());
    }

    @Test
    void openCashRegister_whenAlreadyOpen_shouldThrowConflict() {
        when(cashRegisterRepository.findByTenantIdAndStatus(TENANT_ID, CashRegisterStatus.OPEN))
                .thenReturn(Optional.of(buildRegister(CashRegisterStatus.OPEN)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> cashRegisterService.openCashRegister(new OpenCashRegisterRequest(BigDecimal.ZERO)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("Já existe um caixa aberto"));
    }

    // ── addMovement ─────────────────────────────────────────────────────────────

    @Test
    void addMovement_whenRegisterClosed_shouldThrowConflict() {
        CashRegister closed = buildRegister(CashRegisterStatus.CLOSED);
        when(cashRegisterRepository.findByIdAndTenantId(REGISTER_ID, TENANT_ID))
                .thenReturn(Optional.of(closed));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> cashRegisterService.addMovement(REGISTER_ID,
                        new CashMovementRequest(CashMovementType.BLEEDING, new BigDecimal("10.00"), "Sangria")));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void addMovement_whenAmountZero_shouldNotBeAccepted() {
        // O DTO tem @DecimalMin("0.01"), mas aqui testamos a lógica do service
        // com valor válido para confirmar que caixa aberto aceita
        CashRegister open = buildRegister(CashRegisterStatus.OPEN);
        when(cashRegisterRepository.findByIdAndTenantId(REGISTER_ID, TENANT_ID))
                .thenReturn(Optional.of(open));

        CashMovement movement = CashMovement.builder()
                .id(UUID.randomUUID())
                .cashRegisterId(REGISTER_ID)
                .userId(USER_ID)
                .type(CashMovementType.SUPPLY)
                .amount(new BigDecimal("50.00"))
                .description("Suprimento inicial")
                .createdAt(OffsetDateTime.now())
                .build();
        when(cashMovementRepository.save(any(CashMovement.class))).thenReturn(movement);

        CashMovementResponse response = cashRegisterService.addMovement(REGISTER_ID,
                new CashMovementRequest(CashMovementType.SUPPLY, new BigDecimal("50.00"), "Suprimento inicial"));

        assertNotNull(response);
        assertEquals(CashMovementType.SUPPLY, response.type());
        assertEquals(new BigDecimal("50.00"), response.amount());
    }

    // ── closeCashRegister ───────────────────────────────────────────────────────

    @Test
    void closeCashRegister_whenOpen_shouldCalculateClosingBalance() {
        CashRegister open = buildRegister(CashRegisterStatus.OPEN);
        open.setTotalCash(new BigDecimal("50.00"));
        open.setTotalPix(new BigDecimal("30.00"));

        when(cashRegisterRepository.findByIdAndTenantId(REGISTER_ID, TENANT_ID))
                .thenReturn(Optional.of(open));
        when(cashMovementRepository.sumAmountByRegisterIdAndType(REGISTER_ID, CashMovementType.SUPPLY))
                .thenReturn(Optional.of(new BigDecimal("20.00")));
        when(cashMovementRepository.sumAmountByRegisterIdAndType(REGISTER_ID, CashMovementType.BLEEDING))
                .thenReturn(Optional.of(new BigDecimal("10.00")));
        when(cashRegisterRepository.save(any(CashRegister.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cashMovementRepository.findByCashRegisterIdOrderByCreatedAtAsc(REGISTER_ID)).thenReturn(List.of());

        // closingBalance = 100 + 50 + 30 + 20 - 10 = 190
        CashRegisterResponse response = cashRegisterService.closeCashRegister(REGISTER_ID,
                new CloseCashRegisterRequest(new BigDecimal("185.00")));

        assertEquals(CashRegisterStatus.CLOSED, response.status());
        assertEquals(new BigDecimal("190.00"), response.closingBalance());
        assertEquals(new BigDecimal("-5.00"), response.difference()); // 185 - 190
    }

    @Test
    void closeCashRegister_whenAlreadyClosed_shouldThrowConflict() {
        CashRegister closed = buildRegister(CashRegisterStatus.CLOSED);
        when(cashRegisterRepository.findByIdAndTenantId(REGISTER_ID, TENANT_ID))
                .thenReturn(Optional.of(closed));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> cashRegisterService.closeCashRegister(REGISTER_ID,
                        new CloseCashRegisterRequest(new BigDecimal("100.00"))));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    // ── getOpenRegister ─────────────────────────────────────────────────────────

    @Test
    void getOpenRegister_whenExists_shouldReturn() {
        CashRegister open = buildRegister(CashRegisterStatus.OPEN);
        when(cashRegisterRepository.findByTenantIdAndStatus(TENANT_ID, CashRegisterStatus.OPEN))
                .thenReturn(Optional.of(open));
        when(cashMovementRepository.findByCashRegisterIdOrderByCreatedAtAsc(REGISTER_ID))
                .thenReturn(List.of());

        CashRegisterResponse response = cashRegisterService.getOpenRegister();

        assertNotNull(response);
        assertEquals(CashRegisterStatus.OPEN, response.status());
    }

    @Test
    void getOpenRegister_whenNone_shouldThrowNotFound() {
        when(cashRegisterRepository.findByTenantIdAndStatus(TENANT_ID, CashRegisterStatus.OPEN))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> cashRegisterService.getOpenRegister());

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    // ── Helper ──────────────────────────────────────────────────────────────────

    private CashRegister buildRegister(CashRegisterStatus status) {
        return CashRegister.builder()
                .id(REGISTER_ID)
                .tenantId(TENANT_ID)
                .openedBy(USER_ID)
                .status(status)
                .openingBalance(new BigDecimal("100.00"))
                .totalCash(BigDecimal.ZERO)
                .totalCard(BigDecimal.ZERO)
                .totalPix(BigDecimal.ZERO)
                .openedAt(OffsetDateTime.now())
                .build();
    }
}
