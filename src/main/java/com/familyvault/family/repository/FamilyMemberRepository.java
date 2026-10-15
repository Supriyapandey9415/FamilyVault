package com.familyvault.family.repository;

import com.familyvault.family.entity.FamilyMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

@Repository
public interface FamilyMemberRepository extends JpaRepository<FamilyMember, Long> {
    List<FamilyMember> findByUserId(Long userId);
    List<FamilyMember> findByFamilyId(Long familyId);
    Optional<FamilyMember> findByFamilyIdAndUserId(Long familyId, Long userId);
    long countByFamilyId(Long familyId);
    long countByFamilyIdAndRole(Long familyId, com.familyvault.family.entity.Role role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT fm FROM FamilyMember fm WHERE fm.family.id = :familyId")
    List<FamilyMember> findByFamilyIdForUpdate(@Param("familyId") Long familyId);
}
