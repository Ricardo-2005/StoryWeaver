package com.storyweaver.importing.book.api;

import com.storyweaver.importing.book.application.BookAnalysisService;
import com.storyweaver.importing.book.application.BookAnalysisService.AnalysisRequest;
import com.storyweaver.importing.book.application.BookAnalysisService.AnalysisView;
import com.storyweaver.importing.book.application.TxtBookImportService;
import com.storyweaver.importing.book.application.TxtBookImportService.ProjectInput;
import com.storyweaver.importing.book.domain.TxtImportModels.ImportView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class TxtBookImportController {
    private final TxtBookImportService imports;
    private final BookAnalysisService analysis;

    public TxtBookImportController(TxtBookImportService imports, BookAnalysisService analysis) {
        this.imports = imports;
        this.analysis = analysis;
    }

    @PostMapping(value = "/imports/txt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ImportView> upload(@AuthenticationPrincipal Jwt jwt, @RequestParam("file") MultipartFile file) {
        ImportView result = imports.upload(userId(jwt), file);
        return ResponseEntity.created(URI.create("/api/txt-imports/" + result.id()))
                .body(result);
    }

    @GetMapping("/txt-imports/{importId}")
    ImportView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID importId) {
        return imports.get(importId, userId(jwt));
    }

    @PostMapping("/txt-imports/{importId}/parse")
    ImportView parse(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID importId, @Valid @RequestBody ParseRequest request) {
        return imports.parse(importId, userId(jwt), request.encoding());
    }

    @GetMapping("/txt-imports/{importId}/preview")
    ImportView preview(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID importId) {
        return imports.get(importId, userId(jwt));
    }

    @GetMapping("/txt-imports/{importId}/chapters/{chapterId}/content")
    ContentPreview content(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID importId,
            @PathVariable UUID chapterId,
            @RequestParam(required = false) Integer limit) {
        String content = imports.previewContent(importId, chapterId, userId(jwt), limit);
        long fullLength = imports.get(importId, userId(jwt)).chapters().stream()
                .filter(chapter -> chapter.id().equals(chapterId))
                .findFirst()
                .map(chapter -> chapter.characterCount())
                .orElse(0L);
        return new ContentPreview(content, fullLength > content.length());
    }

    @PatchMapping("/txt-imports/{importId}/chapters/{chapterId}")
    ImportView updateChapter(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID importId,
            @PathVariable UUID chapterId,
            @Valid @RequestBody UpdateChapterRequest request) {
        return imports.updateChapter(
                importId, chapterId, userId(jwt), request.expectedVersion(), request.title(), request.included());
    }

    @PostMapping("/txt-imports/{importId}/chapters/reorder")
    ImportView reorder(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID importId, @Valid @RequestBody ReorderRequest request) {
        return imports.reorder(importId, userId(jwt), request.expectedVersion(), request.chapterIds());
    }

    @PostMapping("/txt-imports/{importId}/chapters/merge")
    ImportView merge(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID importId, @Valid @RequestBody MergeRequest request) {
        return imports.merge(
                importId,
                userId(jwt),
                request.expectedVersion(),
                request.firstChapterId(),
                request.secondChapterId(),
                request.title());
    }

    @PostMapping("/txt-imports/{importId}/chapters/split")
    ImportView split(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID importId, @Valid @RequestBody SplitRequest request) {
        return imports.split(
                importId,
                userId(jwt),
                request.expectedVersion(),
                request.chapterId(),
                request.splitOffset(),
                request.secondTitle());
    }

    @PostMapping("/txt-imports/{importId}/chapters/whole")
    ImportView whole(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID importId,
            @Valid @RequestBody WholeBookRequest request) {
        return imports.wholeBook(importId, userId(jwt), request.expectedVersion(), request.title());
    }

    @PostMapping("/txt-imports/{importId}/chapters/fixed-split")
    ImportView fixedSplit(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID importId,
            @Valid @RequestBody FixedSplitRequest request) {
        return imports.fixedSplit(importId, userId(jwt), request.expectedVersion(), request.targetCharacters());
    }

    @PostMapping("/txt-imports/{importId}/commit")
    ResponseEntity<ImportView> commit(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID importId, @Valid @RequestBody CommitRequest request) {
        ImportView result = imports.commit(importId, userId(jwt), request.expectedVersion(), request.project());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/txt-imports/{importId}/cancel")
    ImportView cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID importId) {
        return imports.cancel(importId, userId(jwt));
    }

    @PostMapping("/projects/{projectId}/book-analysis")
    ResponseEntity<AnalysisView> startAnalysis(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId,
            @Valid @RequestBody AnalysisRequest request) {
        return ResponseEntity.accepted().body(analysis.start(projectId, userId(jwt), request));
    }

    @GetMapping("/txt-imports/{importId}/analysis")
    AnalysisView analysis(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID importId) {
        return analysis.get(importId, userId(jwt));
    }

    @PatchMapping("/txt-imports/{importId}/analysis/candidates/{candidateId}")
    AnalysisView decideAnalysis(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID importId,
            @PathVariable UUID candidateId,
            @Valid @RequestBody CandidateDecisionRequest request) {
        return analysis.decide(importId, candidateId, userId(jwt), request.accepted());
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public record ParseRequest(@NotBlank String encoding) {}

    public record ContentPreview(String content, boolean truncated) {}

    public record UpdateChapterRequest(
            @PositiveOrZero long expectedVersion, @NotBlank @Size(max = 160) String title, boolean included) {}

    public record ReorderRequest(@PositiveOrZero long expectedVersion, @NotEmpty List<@NotNull UUID> chapterIds) {}

    public record MergeRequest(
            @PositiveOrZero long expectedVersion,
            @NotNull UUID firstChapterId,
            @NotNull UUID secondChapterId,
            @Size(max = 160) String title) {}

    public record SplitRequest(
            @PositiveOrZero long expectedVersion,
            @NotNull UUID chapterId,
            @Positive long splitOffset,
            @Size(max = 160) String secondTitle) {}

    public record WholeBookRequest(@PositiveOrZero long expectedVersion, @Size(max = 160) String title) {}

    public record FixedSplitRequest(
            @PositiveOrZero long expectedVersion, @Min(1000) @Max(100000) int targetCharacters) {}

    public record CommitRequest(@PositiveOrZero long expectedVersion, @NotNull @Valid ProjectInput project) {}

    public record CandidateDecisionRequest(boolean accepted) {}
}
