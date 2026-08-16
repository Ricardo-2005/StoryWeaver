package com.storyweaver.importing.book.api;

import com.storyweaver.importing.book.application.BookReconstructionService;
import com.storyweaver.importing.book.application.BookReconstructionService.CandidateView;
import com.storyweaver.importing.book.application.BookReconstructionService.Estimate;
import com.storyweaver.importing.book.application.BookReconstructionService.JobView;
import com.storyweaver.importing.book.application.BookReconstructionService.Mode;
import com.storyweaver.importing.book.application.BookReconstructionService.StartRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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

@RestController
@RequestMapping("/api/projects/{projectId}/reconstruction")
public class BookReconstructionController {
    private final BookReconstructionService reconstruction;

    public BookReconstructionController(BookReconstructionService reconstruction) {
        this.reconstruction = reconstruction;
    }

    @PostMapping("/estimate")
    Estimate estimate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId,
            @Valid @RequestBody EstimateRequest request) {
        return reconstruction.estimate(
                projectId,
                userId(jwt),
                request.mode(),
                request.includeSkillDistillation(),
                request.includeForeshadowing());
    }

    @PostMapping
    ResponseEntity<JobView> start(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId,
            @Valid @RequestBody StartReconstructionRequest request) {
        JobView result = reconstruction.start(
                projectId,
                userId(jwt),
                new StartRequest(
                        request.mode(),
                        request.includeSkillDistillation(),
                        request.includeForeshadowing(),
                        request.maxBudget()));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    @GetMapping
    JobView status(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return reconstruction.get(projectId, userId(jwt));
    }

    @PostMapping("/pause")
    JobView pause(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return reconstruction.pause(projectId, userId(jwt));
    }

    @PostMapping("/resume")
    JobView resume(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId,
            @Valid @RequestBody(required = false) ResumeRequest request) {
        return reconstruction.resume(projectId, userId(jwt), request == null ? null : request.maxBudget());
    }

    @PostMapping("/cancel")
    JobView cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return reconstruction.cancel(projectId, userId(jwt));
    }

    @PostMapping("/retry-failed")
    JobView retryFailed(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return reconstruction.retryFailed(projectId, userId(jwt));
    }

    @GetMapping("/candidates")
    List<CandidateView> candidates(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) {
        return reconstruction.candidates(projectId, userId(jwt), status, type);
    }

    @PatchMapping("/candidates/{candidateId}")
    CandidateView decide(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId,
            @PathVariable UUID candidateId,
            @Valid @RequestBody CandidateDecisionRequest request) {
        return reconstruction.decide(projectId, candidateId, userId(jwt), request.approve());
    }

    @PostMapping("/candidates/{candidateId}/restore")
    CandidateView restore(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @PathVariable UUID candidateId) {
        return reconstruction.restoreRejectedCandidate(projectId, candidateId, userId(jwt));
    }

    @PostMapping("/candidates/{candidateId}/revoke")
    CandidateView revoke(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId,
            @PathVariable UUID candidateId,
            @Valid @RequestBody RevokeCandidateRequest request) {
        return reconstruction.revoke(projectId, candidateId, userId(jwt), request.reason());
    }

    @PostMapping("/approve-safe")
    JobView approveSafe(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return reconstruction.approveSafe(projectId, userId(jwt));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public record EstimateRequest(@NotNull Mode mode, boolean includeSkillDistillation, boolean includeForeshadowing) {}

    public record StartReconstructionRequest(
            @NotNull Mode mode,
            boolean includeSkillDistillation,
            boolean includeForeshadowing,
            @DecimalMin("0") BigDecimal maxBudget) {}

    public record ResumeRequest(@DecimalMin("0") BigDecimal maxBudget) {}

    public record CandidateDecisionRequest(boolean approve) {}

    public record RevokeCandidateRequest(@jakarta.validation.constraints.NotBlank String reason) {}
}
