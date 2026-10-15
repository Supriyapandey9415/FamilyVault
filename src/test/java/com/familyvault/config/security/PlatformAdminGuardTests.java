package com.familyvault.config.security;

import com.familyvault.auth.entity.User;
import com.familyvault.common.security.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformAdminGuardTests {
    @Test void allowlistedButUnverifiedMailboxCannotBecomePlatformAdministrator() {
        CurrentUserService current = mock(CurrentUserService.class);
        User user = new User(); user.setEmail("admin@example.com");
        when(current.requireUser()).thenReturn(user);
        var guard = new PlatformAdminGuard(current, List.of(user.getEmail()));
        assertThrows(AccessDeniedException.class, guard::requirePlatformAdmin);
        user.setEmailVerified(true);
        assertSame(user, guard.requirePlatformAdmin());
    }
}
