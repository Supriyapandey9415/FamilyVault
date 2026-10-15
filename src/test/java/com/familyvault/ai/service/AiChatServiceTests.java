package com.familyvault.ai.service;

import com.familyvault.ai.dto.ChatMessageRequest;
import com.familyvault.ai.dto.ChatMessageResponse;
import com.familyvault.ai.exception.AiChatIntegrationException;
import com.familyvault.auth.entity.User;
import com.familyvault.auth.repository.UserRepository;
import com.familyvault.document.dto.DocumentResponse;
import com.familyvault.document.entity.Document;
import com.familyvault.document.service.DocumentService;
import com.familyvault.family.dto.FamilyResponse;
import com.familyvault.family.dto.MemberResponse;
import com.familyvault.family.entity.Family;
import com.familyvault.family.entity.Role;
import com.familyvault.family.service.FamilyService;
import com.familyvault.memory.dto.MemoryResponse;
import com.familyvault.memory.entity.Memory;
import com.familyvault.memory.service.MemoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class AiChatServiceTests {

    @Mock
    private UserRepository userRepository;
    @Mock
    private FamilyService familyService;
    @Mock
    private DocumentService documentService;
    @Mock
    private MemoryService memoryService;
    @Mock
    private RestTemplate restTemplate;

    private AiChatService aiChatService;

    @BeforeEach
    void setUp() {
        aiChatService = new AiChatService(
                userRepository,
                familyService,
                documentService,
                memoryService,
                restTemplate,
                "test-key",
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user@example.com", null, List.of())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsGeminiResponseUsingAuthorizedMetadataOnly() throws Exception {
        LocalDate dateBeforeRequest = LocalDate.now();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user(12L)));
        FamilyResponse family = family(7L, "Green Families");
        when(familyService.getMyFamilies()).thenReturn(List.of(family));
        when(familyService.getMembers(7L)).thenReturn(List.of(
                new MemberResponse(1L, 12L, "Vault User", "private@example.com", Role.OWNER, null)
        ));
        when(documentService.getDocumentsByFamily(7L)).thenReturn(List.of(document()));
        when(memoryService.getMemoriesByFamily(7L)).thenReturn(List.of(memory()));

        Map<String, Object> geminiResponse = Map.of(
                "candidates", List.of(Map.of(
                        "content", Map.of(
                                "parts", List.of(Map.of("text", "Your passport is in Green Families."))
                        )
                ))
        );
        when(restTemplate.exchange(
                any(URI.class),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(geminiResponse));

        ChatMessageResponse response = aiChatService.ask(
                new ChatMessageRequest("Where is my document?")
        );

        assertEquals("Your passport is in Green Families.", response.getReply());

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                uriCaptor.capture(),
                eq(HttpMethod.POST),
                entityCaptor.capture(),
                eq(Map.class)
        );

        assertEquals(null, uriCaptor.getValue().getQuery());
        assertEquals("test-key", entityCaptor.getValue().getHeaders().getFirst("x-goog-api-key"));
        String payload = entityCaptor.getValue().getBody().toString();
        LocalDate dateAfterRequest = LocalDate.now();
        assertTrue(payload.contains("Today's current date is " + dateBeforeRequest)
                || payload.contains("Today's current date is " + dateAfterRequest));
        assertTrue(payload.contains("A date before today is in the past"));
        assertTrue(payload.contains("Green Families"));
        assertTrue(payload.contains("Vault User"));
        assertTrue(payload.contains("Passport"));
        assertTrue(payload.contains("Family holiday"));
        assertFalse(payload.contains("private@example.com"));
        assertFalse(payload.contains("uploads/documents/passport.pdf"));
        assertFalse(payload.contains("secret-hash"));
    }

    @Test
    void rejectsUnauthenticatedChatRequest() {
        SecurityContextHolder.clearContext();

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> aiChatService.ask(new ChatMessageRequest("Show my files"))
        );

        assertEquals("Authentication is required to use the assistant", exception.getMessage());
    }

    @Test
    void rejectsRequestWhenGeminiKeyIsMissing() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user(12L)));
        AiChatService unconfiguredService = new AiChatService(
                userRepository,
                familyService,
                documentService,
                memoryService,
                restTemplate,
                "",
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"
        );

        AiChatIntegrationException exception = assertThrows(
                AiChatIntegrationException.class,
                () -> unconfiguredService.ask(new ChatMessageRequest("Show my files"))
        );

        assertEquals("AI assistant is not configured. Set GEMINI_API_KEY.", exception.getMessage());
    }

    @Test
    void rejectsGeminiResponseWithoutReadableText() throws Exception {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user(12L)));
        when(familyService.getMyFamilies()).thenReturn(List.of());
        Map<String, Object> emptyGeminiResponse = Map.of("candidates", List.of());
        when(restTemplate.exchange(
                any(URI.class),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(emptyGeminiResponse));

        AiChatIntegrationException exception = assertThrows(
                AiChatIntegrationException.class,
                () -> aiChatService.ask(new ChatMessageRequest("What is in my vault?"))
        );

        assertEquals("AI service returned no answer", exception.getMessage());
    }

    @Test
    void reportsGeminiReadTimeoutClearly() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user(12L)));
        when(familyService.getMyFamilies()).thenReturn(List.of());
        when(restTemplate.exchange(
                any(URI.class),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenThrow(new ResourceAccessException("Read timed out", new SocketTimeoutException("Read timed out")));

        AiChatIntegrationException exception = assertThrows(
                AiChatIntegrationException.class,
                () -> aiChatService.ask(new ChatMessageRequest("What is in my vault?"))
        );

        assertEquals("AI service took too long to respond. Please try again.", exception.getMessage());
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setEmail("user@example.com");
        user.setFirstName("Vault");
        user.setLastName("User");
        return user;
    }

    private FamilyResponse family(Long id, String name) {
        Family family = new Family();
        family.setId(id);
        family.setName(name);
        family.setCreatedBy(12L);
        return new FamilyResponse(family);
    }

    private DocumentResponse document() {
        Document document = new Document();
        document.setId(21L);
        document.setFamilyId(7L);
        document.setUploadedBy(12L);
        document.setDocumentName("Passport");
        document.setDocumentType("IDENTITY");
        document.setFilePath("uploads/documents/passport.pdf");
        document.setPasswordProtected(true);
        document.setPasswordHash("secret-hash");
        document.setExpiryDate(LocalDate.of(2030, 1, 1));
        return new DocumentResponse(document);
    }

    private MemoryResponse memory() {
        Memory memory = new Memory();
        memory.setId(31L);
        memory.setFamilyId(7L);
        memory.setTitle("Family holiday");
        memory.setDescription("Our Jaipur trip");
        memory.setCreatedBy(12L);
        return new MemoryResponse(memory);
    }
}
