package com.familyvault.settings.repository;

import com.familyvault.settings.entity.FamilySettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilySettingsRepository extends JpaRepository<FamilySettings, Long> {
}
