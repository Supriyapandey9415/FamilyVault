package com.familyvault.notification.dto;

import com.familyvault.notification.entity.Notification;
import java.time.LocalDateTime;

public class NotificationResponse {
    private Long id;
    private Long userId;
    private Long familyId;
    private String familyName;
    private String message;
    private String type;
    private String title;
    private String actorName;
    private String entityType;
    private Long entityId;
    private String actionUrl;
    private boolean isRead;
    private LocalDateTime createdAt;

    public NotificationResponse(Notification notification) {
        this(notification, null, null);
    }

    public NotificationResponse(Notification notification, String familyName, String actorName) {
        this.id = notification.getId();
        this.userId = notification.getUserId();
        this.familyId = notification.getFamilyId();
        this.familyName = familyName;
        this.message = notification.getMessage();
        this.type = notification.getType() == null ? "SYSTEM" : notification.getType().name();
        this.title = notification.getTitle();
        this.actorName = actorName;
        this.entityType = notification.getEntityType();
        this.entityId = notification.getEntityId();
        this.actionUrl = notification.getActionUrl();
        this.isRead = notification.getIsRead();
        this.createdAt = notification.getCreatedAt();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getFamilyId() { return familyId; }
    public String getFamilyName() { return familyName; }
    public String getMessage() { return message; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getActorName() { return actorName; }
    public String getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public String getActionUrl() { return actionUrl; }
    public boolean getIsRead() { return isRead; }
    public boolean isRead() { return isRead; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
