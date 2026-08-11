package com.storyweaver.memory.api;

import com.storyweaver.memory.api.StoryEventDtos.CreateEventRequest;
import com.storyweaver.memory.api.StoryEventDtos.EventMatchResponse;
import com.storyweaver.memory.api.StoryEventDtos.EventResponse;
import com.storyweaver.memory.api.StoryEventDtos.EventSearchResponse;
import com.storyweaver.memory.api.StoryEventDtos.SearchEventsRequest;
import com.storyweaver.memory.api.StoryEventDtos.UpdateEventRequest;
import com.storyweaver.memory.application.StoryEventService;
import com.storyweaver.memory.application.StoryEventService.Input;
import com.storyweaver.memory.application.StoryEventService.SearchInput;
import com.storyweaver.memory.application.StoryEventService.SearchResult;
import com.storyweaver.memory.domain.StoryEvent;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Arrays;
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
public class StoryEventController {
    private final StoryEventService service;

    public StoryEventController(StoryEventService service) {
        this.service = service;
    }

    @PostMapping("/projects/{projectId}/story-events")
    ResponseEntity<EventResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateEventRequest request) {
        StoryEvent event = service.create(projectId, userId(jwt), input(request));
        return ResponseEntity.created(URI.create("/api/story-events/" + event.getId()))
                .body(response(event));
    }

    @GetMapping("/projects/{projectId}/story-events")
    List<EventResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return service.list(projectId, userId(jwt)).stream().map(this::response).toList();
    }

    @PutMapping("/story-events/{eventId}")
    EventResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @Valid @RequestBody UpdateEventRequest request) {
        return response(service.update(eventId, userId(jwt), request.expectedVersion(), input(request)));
    }

    @PostMapping("/projects/{projectId}/story-events/search")
    EventSearchResponse search(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId,
            @Valid @RequestBody SearchEventsRequest request) {
        SearchResult result = service.search(
                projectId,
                userId(jwt),
                new SearchInput(
                        request.query(),
                        request.participantIds(),
                        request.location(),
                        request.chapterNo(),
                        request.topK()));
        return new EventSearchResponse(
                result.embeddingAvailable(),
                result.degradedReason(),
                result.query(),
                result.matches().stream()
                        .map(match -> new EventMatchResponse(
                                response(match.event()),
                                match.score(),
                                match.semanticSimilarity(),
                                match.participantScore(),
                                match.locationScore(),
                                match.chapterProximityScore(),
                                match.reasons()))
                        .toList());
    }

    private Input input(CreateEventRequest request) {
        return new Input(
                request.chapterId(),
                request.participantIds(),
                request.knownByIds(),
                request.location(),
                request.storyTime(),
                request.action(),
                request.result(),
                request.importance(),
                request.evidenceParagraph());
    }

    private Input input(UpdateEventRequest request) {
        return new Input(
                request.chapterId(),
                request.participantIds(),
                request.knownByIds(),
                request.location(),
                request.storyTime(),
                request.action(),
                request.result(),
                request.importance(),
                request.evidenceParagraph());
    }

    private EventResponse response(StoryEvent event) {
        return new EventResponse(
                event.getId(),
                event.getProjectId(),
                event.getChapterId(),
                event.getChapterNo(),
                Arrays.asList(event.getParticipantIds()),
                Arrays.asList(event.getKnownByIds()),
                event.getLocation(),
                event.getStoryTime(),
                event.getAction(),
                event.getResult(),
                event.getImportance(),
                event.getEvidenceParagraph(),
                event.getEmbeddingStatus(),
                event.getEmbeddingModel(),
                event.getVersion(),
                event.getCreatedAt(),
                event.getUpdatedAt());
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
