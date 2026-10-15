package com.familyvault.settings.repository;

import com.familyvault.settings.entity.FamilyNotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FamilyNotificationPreferenceRepository extends JpaRepository<FamilyNotificationPreference, Long> {
    Optional<FamilyNotificationPreference> findByFamilyIdAndUserId(Long familyId, Long userId);
    void deleteByFamilyId(Long familyId);
}
