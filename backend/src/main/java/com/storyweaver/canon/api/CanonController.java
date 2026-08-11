package com.storyweaver.canon.api;

import com.storyweaver.canon.api.CanonDtos.AssetResponse;
import com.storyweaver.canon.api.CanonDtos.AssetTransitionRequest;
import com.storyweaver.canon.api.CanonDtos.AssetVersionResponse;
import com.storyweaver.canon.api.CanonDtos.CreateAssetRequest;
import com.storyweaver.canon.api.CanonDtos.UpdateAssetRequest;
import com.storyweaver.canon.application.CanonService;
import com.storyweaver.canon.application.CanonService.AssetDetails;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CanonController {

    private final CanonService canonService;

    public CanonController(CanonService canonService) {
        this.canonService = canonService;
    }

    @PostMapping("/projects/{projectId}/assets")
    ResponseEntity<AssetResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateAssetRequest request) {
        AssetDetails details = canonService.create(
                projectId,
                userId(jwt),
                request.assetType(),
                request.name(),
                request.content(),
                request.changeSummary());
        return ResponseEntity.created(
                        URI.create("/api/assets/" + details.asset().getId()))
                .body(toResponse(details));
    }

    @GetMapping("/projects/{projectId}/assets")
    List<AssetResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return canonService.list(projectId, userId(jwt)).stream()
                .map(this::toResponse)
                .toList();
    }

    @PutMapping("/assets/{assetId}")
    AssetResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID assetId,
            @Valid @RequestBody UpdateAssetRequest request) {
        return toResponse(canonService.update(
                assetId,
                userId(jwt),
                request.expectedVersion(),
                request.name(),
                request.content(),
                request.changeSummary()));
    }

    @PostMapping("/assets/{assetId}/confirm")
    AssetResponse confirm(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID assetId,
            @Valid @RequestBody AssetTransitionRequest request) {
        return toResponse(canonService.confirm(assetId, userId(jwt), request.expectedVersion()));
    }

    @PostMapping("/assets/{assetId}/deprecate")
    AssetResponse deprecate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID assetId,
            @Valid @RequestBody AssetTransitionRequest request) {
        return toResponse(canonService.deprecate(assetId, userId(jwt), request.expectedVersion()));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private AssetResponse toResponse(AssetDetails details) {
        var asset = details.asset();
        var version = details.currentVersion();
        AssetVersionResponse versionResponse = new AssetVersionResponse(
                version.getId(),
                version.getVersionNo(),
                version.getName(),
                version.getContent(),
                version.getChangeSummary(),
                version.getCreatedAt());
        return new AssetResponse(
                asset.getId(),
                asset.getProjectId(),
                asset.getAssetType(),
                asset.getName(),
                asset.getStatus(),
                asset.getCurrentVersionNo(),
                asset.getConfirmedVersionNo(),
                asset.getVersion(),
                asset.getCreatedAt(),
                asset.getUpdatedAt(),
                versionResponse);
    }
}
