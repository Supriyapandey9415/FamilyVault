package com.familyvault.settings.controller;

import com.familyvault.common.response.ApiResponse;
import com.familyvault.settings.dto.FamilyGeneralSettingsRequest;
import com.familyvault.settings.dto.FamilyNotificationPreferenceRequest;
import com.familyvault.settings.dto.FamilyPolicyUpdateRequest;
import com.familyvault.settings.dto.FamilySettingsResponse;
import com.familyvault.settings.dto.OwnershipTransferRequest;
import com.familyvault.settings.service.FamilySettingsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/families/{familyId}/settings")
public class FamilySettingsController {

    private final FamilySettingsService settingsService;

    public FamilySettingsController(FamilySettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<FamilySettingsResponse>> getSettings(
            @PathVariable Long familyId,
            @RequestHeader(value = "X-Vault-Token", required = false) String vaultToken) {
        return ResponseEntity.ok(ApiResponse.success("Family settings retrieved",
            settingsService.getSettings(familyId, vaultToken)));
    }

    @PatchMapping("/general")
    public ResponseEntity<ApiResponse<FamilySettingsResponse>> updateGeneral(
            @PathVariable Long familyId,
            @Valid @RequestBody FamilyGeneralSettingsRequest request,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestHeader(value = "X-Vault-Token", required = false) String vaultToken) {
        return ResponseEntity.ok(ApiResponse.success("General settings updated",
            settingsService.updateGeneral(familyId, request, requestId, vaultToken)));
    }

    @PatchMapping("/policies")
    public ResponseEntity<ApiResponse<FamilySettingsResponse>> updatePolicies(
            @PathVariable Long familyId,
            @Valid @RequestBody FamilyPolicyUpdateRequest request,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestHeader(value = "X-Vault-Token", required = false) String vaultToken) {
        return ResponseEntity.ok(ApiResponse.success("Family policies updated",
            settingsService.updatePolicies(familyId, request, requestId, vaultToken)));
    }

    @PatchMapping("/notification-preferences/me")
    public ResponseEntity<ApiResponse<FamilySettingsResponse>> updateMyNotifications(
            @PathVariable Long familyId,
            @Valid @RequestBody FamilyNotificationPreferenceRequest request,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestHeader(value = "X-Vault-Token", required = false) String vaultToken) {
        return ResponseEntity.ok(ApiResponse.success("Notification preferences updated",
            settingsService.updateMyNotifications(familyId, request, requestId, vaultToken)));
    }

    @PostMapping("/ownership-transfer")
    public ResponseEntity<ApiResponse<FamilySettingsResponse>> transferOwnership(
            @PathVariable Long familyId,
            @Valid @RequestBody OwnershipTransferRequest request,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestHeader(value = "X-Vault-Token", required = false) String vaultToken) {
        return ResponseEntity.ok(ApiResponse.success("Ownership transferred",
            settingsService.transferOwnership(familyId, request, requestId, vaultToken)));
    }
}
