package com.familyvault.auth.repository;

import com.familyvault.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByGoogleId(String googleId);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query("update User u set u.securityVersion = u.securityVersion + 1 where u.id = :id")
    void revokeSessions(@org.springframework.data.repository.query.Param("id") Long id);
}
