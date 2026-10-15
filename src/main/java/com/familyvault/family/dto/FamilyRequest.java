package com.familyvault.family.dto;

import jakarta.validation.constraints.NotBlank;

public class FamilyRequest {

    @NotBlank(message = "Family name is required")
    private String name;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
