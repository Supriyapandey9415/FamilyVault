package com.familyvault.auth.repository;

import com.familyvault.auth.entity.AuthProvider;
import com.familyvault.auth.entity.User;
import com.familyvault.auth.entity.UserAuthIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAuthIdentityRepository extends JpaRepository<UserAuthIdentity, Long> {
    Optional<UserAuthIdentity> findByProviderAndProviderSubject(AuthProvider provider, String providerSubject);
    Optional<UserAuthIdentity> findByUserAndProvider(User user, AuthProvider provider);
}
