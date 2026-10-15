package com.familyvault.notification.repository;

import com.familyvault.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(Long userId);
    List<Notification> findByUserIdAndFamilyIdOrderByCreatedAtDesc(Long userId, Long familyId);
    List<Notification> findByUserIdAndFamilyIdAndIsReadFalseOrderByCreatedAtDesc(Long userId, Long familyId);
    long countByUserIdAndIsReadFalse(Long userId);
    long countByUserIdAndFamilyIdAndIsReadFalse(Long userId, Long familyId);
    boolean existsByDedupeKey(String dedupeKey);
    void deleteByFamilyId(Long familyId);
}
