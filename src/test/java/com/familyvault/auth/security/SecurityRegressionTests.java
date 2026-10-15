package com.familyvault.auth.security;

import com.familyvault.auth.entity.PasswordResetToken;
import com.familyvault.auth.entity.User;
import com.familyvault.auth.repository.PasswordResetTokenRepository;
import com.familyvault.auth.repository.UserRepository;
import com.familyvault.document.entity.Document;
import com.familyvault.document.entity.DocumentFolder;
import com.familyvault.document.repository.DocumentRepository;
import com.familyvault.document.repository.DocumentFolderRepository;
import com.familyvault.family.entity.Family;
import com.familyvault.family.entity.FamilyMember;
import com.familyvault.family.entity.Role;
import com.familyvault.family.repository.FamilyRepository;
import com.familyvault.family.repository.FamilyMemberRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class SecurityRegressionTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired PasswordResetTokenRepository resets;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;
    @Autowired UserDetailsService details;
    @Autowired FamilyRepository families;
    @Autowired FamilyMemberRepository members;
    @Autowired DocumentRepository documents;
    @Autowired DocumentFolderRepository folders;
    MockMvc mvc;
    User owner;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        owner = new User();
        owner.setFirstName("Test"); owner.setLastName("Owner");
        owner.setEmail("security-test@example.com");
        owner.setPassword(encoder.encode("OriginalPassword1"));
        owner = users.saveAndFlush(owner);
    }

    String bearer() { return "Bearer " + jwt.generateToken(details.loadUserByUsername(owner.getEmail())); }

    // Exercise the production cookie repository. Spring's csrf() test helper replaces
    // it with a session-based test repository, which hides browser integration errors.
    org.springframework.test.web.servlet.request.RequestPostProcessor browserCsrf() throws Exception {
        Cookie token = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(token);
        return request -> {
            var cookies = new java.util.ArrayList<Cookie>();
            if (request.getCookies() != null) cookies.addAll(java.util.Arrays.asList(request.getCookies()));
            cookies.add(token);
            request.setCookies(cookies.toArray(Cookie[]::new));
            request.addHeader("X-XSRF-TOKEN", token.getValue());
            return request;
        };
    }

    @Test void anonymousProfileAndDirectResetAreRejected() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/reset-password-direct").with(browserCsrf())
                .contentType("application/json").content("{\"email\":\"security-test@example.com\",\"newPassword\":\"AttackerPassword1\"}"))
                .andExpect(status().isUnauthorized());
        assertTrue(encoder.matches("OriginalPassword1", users.findById(owner.getId()).orElseThrow().getPassword()));
    }

    @Test void emailAndUnverifiedCodeCannotResetPassword() throws Exception {
        mvc.perform(post("/api/auth/reset-password").with(browserCsrf()).contentType("application/json")
                .content("{\"email\":\"security-test@example.com\",\"code\":\"anything\",\"newPassword\":\"AttackerPassword1\"}"))
                .andExpect(status().isBadRequest());
        assertTrue(encoder.matches("OriginalPassword1", users.findById(owner.getId()).orElseThrow().getPassword()));
    }

    @Test void hashedResetIsSingleUseAndRevokesExistingAccessAndVaultTokens() throws Exception {
        String oldAccess = bearer();
        String oldVault = jwt.generateVaultToken(owner.getEmail(), 9L, 0L, 0L);
        String raw = SecurityTokens.randomToken();
        resets.saveAndFlush(new PasswordResetToken(owner, SecurityTokens.hash(raw), LocalDateTime.now().plusMinutes(5)));
        String body = "{\"token\":\"" + raw + "\",\"newPassword\":\"ChangedPassword2\"}";
        mvc.perform(post("/api/auth/reset-password").with(browserCsrf()).contentType("application/json").content(body))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/reset-password").with(browserCsrf()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/auth/me").header("Authorization", oldAccess)).andExpect(status().isUnauthorized());
        assertFalse(jwt.isVaultTokenValid(oldVault, owner.getEmail(), 9L, 1L, 0L));
        assertTrue(encoder.matches("ChangedPassword2", users.findById(owner.getId()).orElseThrow().getPassword()));
        assertTrue(users.findById(owner.getId()).orElseThrow().isEmailVerified());
    }

    @Test void vaultTokenIsNeverAcceptedAsAnAccessToken() throws Exception {
        String vault = jwt.generateVaultToken(owner.getEmail(), 9L, 0L, 0L);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + vault))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", bearer())).andExpect(status().isOk());
    }

    @Test void browserCookieIsHttpOnlyAndMutationsRequireCsrfAndLogoutRevokesTokens() throws Exception {
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        var login = mvc.perform(post("/api/auth/login").with(browserCsrf()).contentType("application/json")
                .content("{\"email\":\"security-test@example.com\",\"password\":\"OriginalPassword1\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        String header = login.getHeader("Set-Cookie");
        assertNotNull(header);
        assertTrue(header.contains("HttpOnly"));
        assertTrue(header.contains("SameSite=Lax"));
        Cookie session = login.getCookie(AuthCookieService.NAME);
        assertNotNull(session);
        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").cookie(session)).andExpect(status().isForbidden());
        String oldAccess = bearer();
        mvc.perform(post("/api/auth/logout").cookie(session).with(browserCsrf())).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", oldAccess)).andExpect(status().isUnauthorized());
    }

    @Test void firstBrowserLoginWorksWithTheRealBootstrapCookieAndHeader() throws Exception {
        var bootstrap = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk()).andReturn().getResponse();
        Cookie csrfCookie = bootstrap.getCookie("XSRF-TOKEN");
        assertNotNull(csrfCookie);
        assertTrue(csrfCookie.isHttpOnly());
        mvc.perform(get("/api/auth/csrf").cookie(csrfCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(csrfCookie.getValue()));
        mvc.perform(post("/api/auth/login").cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue()).contentType("application/json")
                .content("{\"email\":\"security-test@example.com\",\"password\":\"OriginalPassword1\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly(AuthCookieService.NAME, true));
    }

    @Test void onlyCsrfFailuresHaveTheRefreshableErrorCode() throws Exception {
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
        mvc.perform(get("/uploads/private.pdf").header("Authorization", bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test void staleCsrfHeaderIsRejectedAndFreshTokenSucceedsWithoutBypassingSecurity() throws Exception {
        Cookie csrfCookie = mvc.perform(get("/api/auth/csrf"))
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(csrfCookie);
        String credentials = "{\"email\":\"security-test@example.com\",\"password\":\"OriginalPassword1\"}";
        mvc.perform(post("/api/auth/login").cookie(csrfCookie)
                .header("X-XSRF-TOKEN", "stale-token").contentType("application/json").content(credentials))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"))
                .andExpect(cookie().doesNotExist(AuthCookieService.NAME));
        mvc.perform(get("/api/auth/csrf").cookie(csrfCookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").value(csrfCookie.getValue()));
        mvc.perform(post("/api/auth/login").cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue()).contentType("application/json").content(credentials))
                .andExpect(status().isOk());
    }

    @Test void searchExcludesBothDirectlyHiddenDocumentsAndHiddenFolderContents() throws Exception {
        Family family = new Family(); family.setName("Security family"); family.setCreatedBy(owner.getId());
        family = families.saveAndFlush(family);
        FamilyMember member = new FamilyMember(); member.setFamily(family); member.setUserId(owner.getId()); member.setRole(Role.OWNER);
        members.saveAndFlush(member);
        DocumentFolder folder = new DocumentFolder(); folder.setFamilyId(family.getId()); folder.setName("Private");
        folder.setCreatedBy(owner.getId()); folder.setHidden(true); folder = folders.saveAndFlush(folder);
        for (int i = 0; i < 3; i++) {
            Document doc = new Document(); doc.setFamilyId(family.getId()); doc.setUploadedBy(owner.getId());
            doc.setDocumentName("needle-" + i); doc.setFilePath("fixture.pdf");
            doc.setHidden(i == 1); doc.setFolderId(i == 2 ? folder.getId() : null);
            documents.saveAndFlush(doc);
        }
        mvc.perform(get("/api/search").param("q", "needle").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.documents.length()").value(1))
                .andExpect(jsonPath("$.data.documents[0].documentName").value("needle-0"));
    }
}
