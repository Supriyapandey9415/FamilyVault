package com.familyvault.settings.repository;

import com.familyvault.settings.entity.FamilySettingsAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilySettingsAuditRepository extends JpaRepository<FamilySettingsAudit, Long> {
}
