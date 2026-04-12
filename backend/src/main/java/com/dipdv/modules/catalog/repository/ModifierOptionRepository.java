package com.dipdv.modules.catalog.repository;

import com.dipdv.modules.catalog.entity.ModifierOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ModifierOptionRepository extends JpaRepository<ModifierOption, UUID> {

    List<ModifierOption> findByModifierGroupIdAndActiveTrueOrderByPositionAsc(UUID groupId);

    @Query("""
        SELECT o FROM ModifierOption o
        JOIN FETCH o.modifierGroup mg
        WHERE o.id = :id
    """)
    Optional<ModifierOption> findByIdWithGroup(@Param("id") UUID id);
}