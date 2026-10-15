package com.familyvault.memory.repository;

import com.familyvault.memory.entity.Memory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemoryRepository extends JpaRepository<Memory, Long> {
    List<Memory> findByFamilyId(Long familyId);
    long countByFamilyId(Long familyId);

    @Query("SELECT m FROM Memory m WHERE m.familyId IN :familyIds AND (LOWER(m.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(m.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Memory> searchMemories(@Param("familyIds") List<Long> familyIds, @Param("keyword") String keyword);
}
