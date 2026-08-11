package com.storyweaver.auth.application;

import com.storyweaver.auth.domain.AppUser;
import com.storyweaver.auth.domain.UserStatus;
import com.storyweaver.auth.repository.AppUserRepository;
import com.storyweaver.auth.security.JwtTokenService;
import com.storyweaver.auth.security.JwtTokenService.IssuedToken;
import com.storyweaver.shared.error.ConflictException;
import com.storyweaver.shared.error.NotFoundException;
import com.storyweaver.shared.error.UnauthorizedException;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    private final Clock clock;

    public AuthService(
            AppUserRepository users, PasswordEncoder passwordEncoder, JwtTokenService tokenService, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.clock = clock;
    }

    @Transactional
    public AuthenticationResult register(String username, String email, String password) {
        String trimmedUsername = username.trim();
        String trimmedEmail = email.trim();
        String normalizedUsername = normalize(trimmedUsername);
        String normalizedEmail = normalize(trimmedEmail);
        if (users.existsByNormalizedUsername(normalizedUsername)) {
            throw new ConflictException("username_exists", "Username is already registered");
        }
        if (users.existsByNormalizedEmail(normalizedEmail)) {
            throw new ConflictException("email_exists", "Email is already registered");
        }
        AppUser user = new AppUser(
                trimmedUsername,
                normalizedUsername,
                trimmedEmail,
                normalizedEmail,
                passwordEncoder.encode(password),
                clock.instant());
        users.saveAndFlush(user);
        return authenticated(user);
    }

    @Transactional(readOnly = true)
    public AuthenticationResult login(String identifier, String password) {
        String normalizedIdentifier = normalize(identifier);
        AppUser user = users.findByNormalizedUsernameOrNormalizedEmail(normalizedIdentifier, normalizedIdentifier)
                .orElseThrow(() ->
                        new UnauthorizedException("invalid_credentials", "Username/email or password is incorrect"));
        if (user.getStatus() != UserStatus.ACTIVE || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new UnauthorizedException("invalid_credentials", "Username/email or password is incorrect");
        }
        return authenticated(user);
    }

    @Transactional(readOnly = true)
    public AppUser currentUser(UUID userId) {
        return users.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("user_not_found", "User was not found"));
    }

    private AuthenticationResult authenticated(AppUser user) {
        IssuedToken token = tokenService.issue(user);
        return new AuthenticationResult(user, token.value(), token.expiresAt());
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public record AuthenticationResult(AppUser user, String accessToken, java.time.Instant expiresAt) {}
}
