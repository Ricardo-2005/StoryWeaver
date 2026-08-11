package com.storyweaver.mcp.transport;

import com.storyweaver.shared.error.UnauthorizedException;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class McpCurrentUser {
    public UUID id() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new UnauthorizedException("authentication_required", "MCP requires a bearer token");
        }
        return UUID.fromString(jwt.getSubject());
    }
}
