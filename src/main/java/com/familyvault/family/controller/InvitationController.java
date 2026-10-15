package com.familyvault.family.controller;

import com.familyvault.common.response.ApiResponse;
import com.familyvault.family.dto.AcceptInvitationRequest;
import com.familyvault.family.dto.InvitationPreviewResponse;
import com.familyvault.family.dto.InvitationRequest;
import com.familyvault.family.dto.InvitationResponse;
import com.familyvault.family.dto.MemberResponse;
import com.familyvault.family.service.FamilyInvitationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class InvitationController {

    private final FamilyInvitationService invitationService;

    public InvitationController(FamilyInvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping("/families/{familyId}/invitations")
    public ResponseEntity<ApiResponse<InvitationResponse>> createInvitation(
            @PathVariable Long familyId,
            @Valid @RequestBody InvitationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Invitation sent successfully", invitationService.createInvitation(familyId, request)));
    }

    @GetMapping("/families/{familyId}/invitations")
    public ResponseEntity<ApiResponse<List<InvitationResponse>>> getPendingInvitations(@PathVariable Long familyId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Pending invitations retrieved successfully", invitationService.getPendingInvitations(familyId)));
    }

    @PostMapping("/families/{familyId}/invitations/{invitationId}/resend")
    public ResponseEntity<ApiResponse<InvitationResponse>> resendInvitation(
            @PathVariable Long familyId, @PathVariable Long invitationId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Invitation resent successfully", invitationService.resendInvitation(familyId, invitationId)));
    }

    @DeleteMapping("/families/{familyId}/invitations/{invitationId}")
    public ResponseEntity<ApiResponse<Void>> revokeInvitation(
            @PathVariable Long familyId, @PathVariable Long invitationId) {
        invitationService.revokeInvitation(familyId, invitationId);
        return ResponseEntity.ok(ApiResponse.success("Invitation revoked successfully", null));
    }

    @GetMapping("/invitations/preview")
    public ResponseEntity<ApiResponse<InvitationPreviewResponse>> previewInvitation(@RequestParam String token) {
        return ResponseEntity.ok(ApiResponse.success(
                "Invitation retrieved successfully", invitationService.previewInvitation(token)));
    }

    @PostMapping("/invitations/accept")
    public ResponseEntity<ApiResponse<MemberResponse>> acceptInvitation(
            @Valid @RequestBody AcceptInvitationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "You joined the family successfully", invitationService.acceptInvitation(request)));
    }
}
