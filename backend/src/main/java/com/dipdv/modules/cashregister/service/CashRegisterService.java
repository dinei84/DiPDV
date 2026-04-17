package com.dipdv.modules.cashregister.service;

import com.dipdv.modules.cashregister.dto.*;
import com.dipdv.modules.cashregister.entity.CashMovement;
import com.dipdv.modules.cashregister.entity.CashRegister;
import com.dipdv.modules.cashregister.entity.enums.CashMovementType;
import com.dipdv.modules.cashregister.entity.enums.CashRegisterStatus;
import com.dipdv.modules.cashregister.repository.CashMovementRepository;
import com.dipdv.modules.cashregister.repository.CashRegisterRepository;
import com.dipdv.shared.audit.AuditAction;
import com.dipdv.shared.audit.Auditable;
import com.dipdv.shared.exception.BusinessException;
import com.dipdv.shared.security.DiPdvAuthDetails;
import com.dipdv.shared.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CashRegisterService {

    private final CashRegisterRepository cashRegisterRepository;
    private final CashMovementRepository cashMovementRepository;

    @Transactional
    public CashRegisterResponse openCashRegister(OpenCashRegisterRequest request) {
        UUID tenantId = TenantContext.getRequired();
        UUID userId   = extractUserId();

        // Validar que não há caixa aberto
        cashRegisterRepository.findByTenantIdAndStatus(tenantId, CashRegisterStatus.OPEN)
                .ifPresent(r -> {
                    throw new BusinessException("Já existe um caixa aberto", HttpStatus.CONFLICT);
                });

        CashRegister register = CashRegister.builder()
                .tenantId(tenantId)
                .openedBy(userId)
                .status(CashRegisterStatus.OPEN)
                .openingBalance(request.openingBalance() != null ? request.openingBalance() : BigDecimal.ZERO)
                .totalCash(BigDecimal.ZERO)
                .totalCard(BigDecimal.ZERO)
                .totalPix(BigDecimal.ZERO)
                .build();

        register = cashRegisterRepository.save(register);
        log.info("Caixa aberto: {} pelo tenant: {}", register.getId(), tenantId);

        return toResponse(register, List.of());
    }

    @Transactional(readOnly = true)
    public CashRegisterResponse getOpenRegister() {
        UUID tenantId = TenantContext.getRequired();
        CashRegister register = cashRegisterRepository
                .findByTenantIdAndStatus(tenantId, CashRegisterStatus.OPEN)
                .orElseThrow(() -> new BusinessException("Nenhum caixa aberto", HttpStatus.NOT_FOUND));

        List<CashMovement> movements = cashMovementRepository
                .findByCashRegisterIdOrderByCreatedAtAsc(register.getId());
        return toResponse(register, movements);
    }

    @Transactional
    public CashMovementResponse addMovement(UUID registerId, CashMovementRequest request) {
        UUID tenantId = TenantContext.getRequired();
        UUID userId   = extractUserId();

        CashRegister register = cashRegisterRepository
                .findByIdAndTenantId(registerId, tenantId)
                .orElseThrow(() -> new BusinessException("Caixa não encontrado", HttpStatus.NOT_FOUND));

        if (register.getStatus() != CashRegisterStatus.OPEN) {
            throw new BusinessException("Caixa não está aberto", HttpStatus.CONFLICT);
        }

        CashMovement movement = CashMovement.builder()
                .cashRegisterId(registerId)
                .userId(userId)
                .type(request.type())
                .amount(request.amount())
                .description(request.description())
                .build();

        movement = cashMovementRepository.save(movement);
        log.info("Movimentação {} de R$ {} registrada no caixa {}", request.type(), request.amount(), registerId);

        return new CashMovementResponse(
                movement.getId(),
                movement.getType(),
                movement.getAmount(),
                movement.getDescription(),
                movement.getCreatedAt()
        );
    }

    @Transactional
    @Auditable(action = AuditAction.CASH_REGISTER_CLOSED, entity = "cash_registers")
    public CashRegisterResponse closeCashRegister(UUID registerId, CloseCashRegisterRequest request) {
        UUID tenantId = TenantContext.getRequired();
        UUID userId   = extractUserId();

        CashRegister register = cashRegisterRepository
                .findByIdAndTenantId(registerId, tenantId)
                .orElseThrow(() -> new BusinessException("Caixa não encontrado", HttpStatus.NOT_FOUND));

        if (register.getStatus() != CashRegisterStatus.OPEN) {
            throw new BusinessException("Caixa não está aberto", HttpStatus.CONFLICT);
        }

        // Calcular somas de sangria e suprimento
        BigDecimal totalSupply = cashMovementRepository
                .sumAmountByRegisterIdAndType(registerId, CashMovementType.SUPPLY)
                .orElse(BigDecimal.ZERO);

        BigDecimal totalBleeding = cashMovementRepository
                .sumAmountByRegisterIdAndType(registerId, CashMovementType.BLEEDING)
                .orElse(BigDecimal.ZERO);

        // closingBalance = openingBalance + totalCash + totalPix + SUPPLY - BLEEDING
        BigDecimal closingBalance = register.getOpeningBalance()
                .add(register.getTotalCash())
                .add(register.getTotalPix())
                .add(totalSupply)
                .subtract(totalBleeding);

        BigDecimal difference = request.physicalBalance().subtract(closingBalance);

        register.setStatus(CashRegisterStatus.CLOSED);
        register.setClosedBy(userId);
        register.setClosedAt(OffsetDateTime.now());
        register.setClosingBalance(closingBalance);
        register.setPhysicalBalance(request.physicalBalance());
        register.setDifference(difference);

        register = cashRegisterRepository.save(register);
        log.info("Caixa {} fechado. closingBalance={} difference={}", registerId, closingBalance, difference);

        List<CashMovement> movements = cashMovementRepository
                .findByCashRegisterIdOrderByCreatedAtAsc(registerId);
        return toResponse(register, movements);
    }

    @Transactional(readOnly = true)
    public Page<CashRegisterResponse> listRegisters(Pageable pageable) {
        UUID tenantId = TenantContext.getRequired();
        return cashRegisterRepository
                .findByTenantIdOrderByOpenedAtDesc(tenantId, pageable)
                .map(r -> {
                    List<CashMovement> movements = cashMovementRepository
                            .findByCashRegisterIdOrderByCreatedAtAsc(r.getId());
                    return toResponse(r, movements);
                });
    }

    @Transactional(readOnly = true)
    public CashRegisterResponse getById(UUID registerId) {
        UUID tenantId = TenantContext.getRequired();
        CashRegister register = cashRegisterRepository
                .findByIdAndTenantId(registerId, tenantId)
                .orElseThrow(() -> new BusinessException("Caixa não encontrado", HttpStatus.NOT_FOUND));

        List<CashMovement> movements = cashMovementRepository
                .findByCashRegisterIdOrderByCreatedAtAsc(registerId);
        return toResponse(register, movements);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private UUID extractUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getDetails() instanceof DiPdvAuthDetails details) {
                return details.userId();
            }
        } catch (Exception ignored) {}
        throw new BusinessException("Usuário não autenticado", HttpStatus.UNAUTHORIZED);
    }

    private CashRegisterResponse toResponse(CashRegister register, List<CashMovement> movements) {
        List<CashMovementResponse> movementResponses = movements.stream()
                .map(m -> new CashMovementResponse(
                        m.getId(),
                        m.getType(),
                        m.getAmount(),
                        m.getDescription(),
                        m.getCreatedAt()
                ))
                .toList();

        return new CashRegisterResponse(
                register.getId(),
                register.getStatus(),
                register.getOpeningBalance(),
                register.getClosingBalance(),
                register.getPhysicalBalance(),
                register.getDifference(),
                register.getTotalCash(),
                register.getTotalPix(),
                register.getOpenedAt(),
                register.getClosedAt(),
                movementResponses
        );
    }
}
