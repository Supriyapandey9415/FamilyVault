package com.familyvault.notification.controller;

import com.familyvault.common.response.ApiResponse;
import com.familyvault.notification.dto.NotificationPreferenceRequest;
import com.familyvault.notification.dto.NotificationPreferenceResponse;
import com.familyvault.notification.service.NotificationPreferenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/notifications")
public class NotificationPreferenceController {

    private final NotificationPreferenceService service;

    public NotificationPreferenceController(NotificationPreferenceService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> getPreferences() {
        return ResponseEntity.ok(ApiResponse.success("Notification preferences fetched", service.getMyPreferences()));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> updatePreferences(
            @RequestBody NotificationPreferenceRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Notification preferences updated", service.updateMyPreferences(request)));
    }
}
