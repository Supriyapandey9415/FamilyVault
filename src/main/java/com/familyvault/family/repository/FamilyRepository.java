package com.familyvault.family.repository;

import com.familyvault.family.entity.Family;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FamilyRepository extends JpaRepository<Family, Long> {
    boolean existsByNameAndCreatedBy(String name, Long createdBy);
}
