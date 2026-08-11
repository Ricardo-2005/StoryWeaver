package com.storyweaver.impact.api;

import com.storyweaver.impact.application.ImpactReportService;
import com.storyweaver.impact.application.ImpactReportService.ImpactReport;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ImpactReportController {
    private final ImpactReportService service;

    public ImpactReportController(ImpactReportService service) {
        this.service = service;
    }

    @PostMapping("/chapters/{chapterId}/impact-reports")
    ResponseEntity<ImpactReport> create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID chapterId) {
        var value = service.create(chapterId, userId(jwt));
        return ResponseEntity.created(URI.create("/api/impact-reports/" + value.id()))
                .body(value);
    }

    @GetMapping("/chapters/{chapterId}/impact-reports")
    List<ImpactReport> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID chapterId) {
        return service.list(chapterId, userId(jwt));
    }

    @GetMapping("/impact-reports/{id}")
    ImpactReport get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.get(id, userId(jwt));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
