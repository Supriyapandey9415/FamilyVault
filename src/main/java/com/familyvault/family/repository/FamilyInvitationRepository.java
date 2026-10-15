package com.familyvault.family.repository;

import com.familyvault.family.entity.FamilyInvitation;
import com.familyvault.family.entity.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FamilyInvitationRepository extends JpaRepository<FamilyInvitation, Long> {
    Optional<FamilyInvitation> findByTokenHash(String tokenHash);
    List<FamilyInvitation> findByFamily_Id(Long familyId);

    List<FamilyInvitation> findByFamily_IdAndStatusOrderByCreatedAtDesc(Long familyId, InvitationStatus status);

    Optional<FamilyInvitation> findByFamily_IdAndInvitedEmailAndStatus(
            Long familyId, String invitedEmail, InvitationStatus status);
}
