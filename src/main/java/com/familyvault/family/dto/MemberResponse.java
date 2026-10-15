package com.familyvault.family.dto;

import com.familyvault.family.entity.Role;
import java.time.LocalDateTime;

public class MemberResponse {
    private Long memberId;
    private Long userId;
    private String name;
    private String email;
    private Role role;
    private LocalDateTime joinedAt;

    public MemberResponse(Long memberId, Long userId, String name, String email, Role role, LocalDateTime joinedAt) {
        this.memberId = memberId;
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public Long getMemberId() { return memberId; }
    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public Role getRole() { return role; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
}
