package com.familyvault.search.dto;

import com.familyvault.document.dto.DocumentResponse;
import com.familyvault.memory.dto.MemoryResponse;
import java.util.List;

public class SearchResult {
    private List<MemoryResponse> memories;
    private List<DocumentResponse> documents;

    public SearchResult(List<MemoryResponse> memories, List<DocumentResponse> documents) {
        this.memories = memories;
        this.documents = documents;
    }

    public List<MemoryResponse> getMemories() { return memories; }
    public List<DocumentResponse> getDocuments() { return documents; }
}
