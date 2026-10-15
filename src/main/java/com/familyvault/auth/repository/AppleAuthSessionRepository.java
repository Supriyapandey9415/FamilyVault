package com.familyvault.auth.repository;

import com.familyvault.auth.entity.AppleAuthSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AppleAuthSessionRepository extends JpaRepository<AppleAuthSession, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from AppleAuthSession session where session.stateHash = :stateHash")
    Optional<AppleAuthSession> findForUpdate(@Param("stateHash") String stateHash);

    @Modifying
    @Query("delete from AppleAuthSession session where session.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
