package com.familyvault.document.repository;

import com.familyvault.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByFamilyId(Long familyId);
    List<Document> findByExpiryDateBetween(LocalDate startDate, LocalDate endDate);
    List<Document> findByExpiryDate(LocalDate expiryDate);
    List<Document> findByExpiryDateBetweenOrderByExpiryDateAsc(LocalDate startDate, LocalDate endDate);
    boolean existsByFamilyIdAndFolderId(Long familyId, Long folderId);
    long countByFamilyId(Long familyId);

    @Query("SELECT d FROM Document d WHERE d.familyId IN :familyIds AND d.hidden = false "
            + "AND (d.folderId IS NULL OR EXISTS (SELECT f.id FROM DocumentFolder f "
            + "WHERE f.id = d.folderId AND f.familyId = d.familyId AND f.hidden = false)) "
            + "AND LOWER(d.documentName) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Document> searchDocuments(@Param("familyIds") List<Long> familyIds, @Param("keyword") String keyword);
}
