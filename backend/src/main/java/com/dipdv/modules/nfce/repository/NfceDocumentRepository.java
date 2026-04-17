package com.dipdv.modules.nfce.repository;

import com.dipdv.modules.nfce.entity.NfceDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NfceDocumentRepository extends JpaRepository<NfceDocument, UUID> {

    Optional<NfceDocument> findByOrderId(UUID orderId);

    Optional<NfceDocument> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<NfceDocument> findByOrderIdAndTenantId(UUID orderId, UUID tenantId);
}
