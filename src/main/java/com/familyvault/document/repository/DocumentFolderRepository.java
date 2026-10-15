package com.familyvault.document.repository;

import com.familyvault.document.entity.DocumentFolder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentFolderRepository extends JpaRepository<DocumentFolder, Long> {
    List<DocumentFolder> findByFamilyIdOrderByNameAsc(Long familyId);
    Optional<DocumentFolder> findByIdAndFamilyId(Long id, Long familyId);
}
