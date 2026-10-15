package com.familyvault.config.controller;

import com.familyvault.common.response.ApiResponse;
import com.familyvault.config.dto.SmtpConfigRequest;
import com.familyvault.config.dto.SmtpConfigResponse;
import com.familyvault.config.service.SmtpConfigService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/settings/smtp")
public class SmtpConfigController {

    private final SmtpConfigService smtpConfigService;

    public SmtpConfigController(SmtpConfigService smtpConfigService) {
        this.smtpConfigService = smtpConfigService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<SmtpConfigResponse>> getSmtpConfig() {
        SmtpConfigResponse response = smtpConfigService.getCurrentConfig();
        return ResponseEntity.ok(ApiResponse.success("SMTP configuration fetched", response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SmtpConfigResponse>> saveSmtpConfig(@Valid @RequestBody SmtpConfigRequest request) {
        SmtpConfigResponse response = smtpConfigService.saveConfig(request);
        return ResponseEntity.ok(ApiResponse.success("SMTP configuration saved successfully", response));
    }

    @PostMapping("/test")
    public ResponseEntity<ApiResponse<String>> testSmtpConnection(@RequestBody Map<String, String> request) {
        String targetEmail = request.get("email");
        String message = smtpConfigService.testSmtpConnection(targetEmail);
        return ResponseEntity.ok(ApiResponse.success(message, message));
    }
}
