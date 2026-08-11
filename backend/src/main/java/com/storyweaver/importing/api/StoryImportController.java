package com.storyweaver.importing.api;

import com.storyweaver.importing.application.StoryImportService;
import com.storyweaver.importing.application.StoryImportService.CandidateDecision;
import com.storyweaver.importing.application.StoryImportService.ChapterInput;
import com.storyweaver.importing.application.StoryImportService.ImportView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class StoryImportController {
    private final StoryImportService service;

    public StoryImportController(StoryImportService service) {
        this.service = service;
    }

    @PostMapping(value = "/projects/{projectId}/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ImportView> upload(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.upload(projectId, userId(jwt), file));
    }

    @GetMapping("/projects/{projectId}/imports")
    List<ImportView> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return service.list(projectId, userId(jwt));
    }

    @GetMapping("/imports/{importId}")
    ImportView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID importId) {
        return service.get(importId, userId(jwt));
    }

    @PutMapping("/imports/{importId}/chapters")
    ImportView chapters(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID importId,
            @Valid @RequestBody ChaptersRequest request) {
        return service.replaceChapters(importId, userId(jwt), request.expectedVersion(), request.chapters());
    }

    @PostMapping("/imports/{importId}/extract")
    ImportView extract(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID importId) {
        return service.extract(importId, userId(jwt));
    }

    @PostMapping("/imports/{importId}/retry")
    ImportView retry(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID importId) {
        return service.retry(importId, userId(jwt));
    }

    @PostMapping("/imports/{importId}/cancel")
    ImportView cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID importId) {
        return service.cancel(importId, userId(jwt));
    }

    @PostMapping("/imports/{importId}/complete")
    ImportView complete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID importId) {
        return service.complete(importId, userId(jwt));
    }

    @PostMapping("/imports/{importId}/candidates/decide")
    ImportView decide(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID importId,
            @Valid @RequestBody DecisionsRequest request) {
        return service.decide(importId, userId(jwt), request.decisions());
    }

    @PostMapping("/imports/{importId}/aliases/merge")
    ResponseEntity<Void> merge(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID importId,
            @Valid @RequestBody AliasMergeRequest request) {
        service.mergeAlias(importId, userId(jwt), request.sourceName(), request.targetCharacterId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/projects/{projectId}/exports/git")
    ResponseEntity<byte[]> export(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("storyweaver-" + projectId + ".zip")
                .build());
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(service.exportGit(projectId, userId(jwt)));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public record ChaptersRequest(long expectedVersion, @NotEmpty List<@Valid ChapterInput> chapters) {}

    public record DecisionsRequest(@NotEmpty List<@Valid CandidateDecision> decisions) {}

    public record AliasMergeRequest(@NotBlank String sourceName, @NotNull UUID targetCharacterId) {}
}
