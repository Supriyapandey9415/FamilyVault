package com.familyvault.memory.repository;

import com.familyvault.memory.entity.MemoryMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MemoryMediaRepository extends JpaRepository<MemoryMedia, Long> {
    java.util.List<MemoryMedia> findByMemoryId(Long memoryId);
}
