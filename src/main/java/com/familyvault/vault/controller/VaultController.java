package com.familyvault.vault.controller;

import com.familyvault.common.response.ApiResponse;
import com.familyvault.vault.dto.VaultPinSetupRequest;
import com.familyvault.vault.dto.VaultStatusResponse;
import com.familyvault.vault.dto.VaultUnlockRequest;
import com.familyvault.vault.dto.VaultUnlockResponse;
import com.familyvault.vault.service.VaultAccessService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vault")
public class VaultController {

    private final VaultAccessService vaultAccessService;

    public VaultController(VaultAccessService vaultAccessService) {
        this.vaultAccessService = vaultAccessService;
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<VaultStatusResponse>> status(
            @RequestParam Long familyId,
            @RequestHeader(value = "X-Vault-Token", required = false) String vaultToken) {
        return ResponseEntity.ok(ApiResponse.success(
                "Vault status retrieved",
                vaultAccessService.getStatus(familyId, vaultToken)
        ));
    }

    @PostMapping("/pin")
    public ResponseEntity<ApiResponse<VaultUnlockResponse>> setupPin(
            @Valid @RequestBody VaultPinSetupRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Vault PIN created and vault unlocked",
                vaultAccessService.setupPin(request)
        ));
    }

    @PostMapping("/unlock")
    public ResponseEntity<ApiResponse<VaultUnlockResponse>> unlock(
            @Valid @RequestBody VaultUnlockRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Vault unlocked",
                vaultAccessService.unlock(request)
        ));
    }
}
