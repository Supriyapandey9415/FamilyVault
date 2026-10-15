package com.familyvault.search.service;

import com.familyvault.auth.entity.User;
import com.familyvault.auth.repository.UserRepository;
import com.familyvault.document.dto.DocumentResponse;
import com.familyvault.document.repository.DocumentRepository;
import com.familyvault.family.entity.FamilyMember;
import com.familyvault.family.repository.FamilyMemberRepository;
import com.familyvault.memory.dto.MemoryResponse;
import com.familyvault.memory.repository.MemoryRepository;
import com.familyvault.search.dto.SearchResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private final MemoryRepository memoryRepository;
    private final DocumentRepository documentRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final UserRepository userRepository;

    public SearchService(MemoryRepository memoryRepository, DocumentRepository documentRepository, FamilyMemberRepository familyMemberRepository, UserRepository userRepository) {
        this.memoryRepository = memoryRepository;
        this.documentRepository = documentRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.userRepository = userRepository;
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("User is not authenticated");
        }
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found in database"));
    }

    public SearchResult searchGlobal(String keyword) {
        if (keyword != null && keyword.length() > 200) {
            throw new IllegalArgumentException("Search text must not exceed 200 characters");
        }
        if (keyword == null || keyword.trim().isEmpty()) {
            return new SearchResult(Collections.emptyList(), Collections.emptyList());
        }

        User currentUser = getCurrentUser();
        List<FamilyMember> memberships = familyMemberRepository.findByUserId(currentUser.getId());
        
        if (memberships.isEmpty()) {
            return new SearchResult(Collections.emptyList(), Collections.emptyList());
        }

        List<Long> authorizedFamilyIds = memberships.stream()
                .map(m -> m.getFamily().getId())
                .collect(Collectors.toList());

        List<MemoryResponse> memoryResponses = memoryRepository.searchMemories(authorizedFamilyIds, keyword).stream()
                .map(MemoryResponse::new)
                .collect(Collectors.toList());

        List<DocumentResponse> documentResponses = documentRepository.searchDocuments(authorizedFamilyIds, keyword).stream()
                .map(DocumentResponse::new)
                .collect(Collectors.toList());

        return new SearchResult(memoryResponses, documentResponses);
    }
}
