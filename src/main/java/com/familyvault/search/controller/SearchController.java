package com.familyvault.search.controller;

import com.familyvault.common.response.ApiResponse;
import com.familyvault.search.dto.SearchResult;
import com.familyvault.search.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<SearchResult>> search(@RequestParam("q") String keyword) {
        try {
            SearchResult result = searchService.searchGlobal(keyword);
            return ResponseEntity.ok(ApiResponse.success("Search completed successfully", result));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
