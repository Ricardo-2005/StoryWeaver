package com.storyweaver.auth.api;

import com.storyweaver.auth.api.AuthDtos.AuthResponse;
import com.storyweaver.auth.api.AuthDtos.LoginRequest;
import com.storyweaver.auth.api.AuthDtos.RegisterRequest;
import com.storyweaver.auth.api.AuthDtos.UserResponse;
import com.storyweaver.auth.application.AuthService;
import com.storyweaver.auth.application.AuthService.AuthenticationResult;
import com.storyweaver.auth.domain.AppUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/register")
    ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthenticationResult result = authService.register(request.username(), request.email(), request.password());
        return ResponseEntity.created(URI.create("/api/me")).body(toResponse(result));
    }

    @PostMapping("/auth/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return toResponse(authService.login(request.identifier(), request.password()));
    }

    @GetMapping("/me")
    UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return toUser(authService.currentUser(UUID.fromString(jwt.getSubject())));
    }

    private AuthResponse toResponse(AuthenticationResult result) {
        return new AuthResponse(result.accessToken(), "Bearer", result.expiresAt(), toUser(result.user()));
    }

    private UserResponse toUser(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.getCreatedAt());
    }
}
