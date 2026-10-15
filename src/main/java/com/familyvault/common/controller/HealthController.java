package com.familyvault.common.controller;

import com.familyvault.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public ApiResponse<String> checkHealth() {
        return ApiResponse.success("FamilyVault API is running");
    }
}
