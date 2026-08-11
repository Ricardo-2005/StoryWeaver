package com.storyweaver.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 50) String username,
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 8, max = 72) String password) {}

    public record LoginRequest(
            @NotBlank @Size(max = 320) String identifier, @NotBlank @Size(max = 72) String password) {}

    public record UserResponse(UUID id, String username, String email, String role, Instant createdAt) {}

    public record AuthResponse(String accessToken, String tokenType, Instant expiresAt, UserResponse user) {}
}
