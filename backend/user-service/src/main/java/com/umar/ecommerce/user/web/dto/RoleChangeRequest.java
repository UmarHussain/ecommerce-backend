package com.umar.ecommerce.user.web.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record RoleChangeRequest(@NotEmpty Set<String> roles) {
}
